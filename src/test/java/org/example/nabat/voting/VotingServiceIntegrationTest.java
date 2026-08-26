package org.example.nabat.voting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The only test that runs a real nabat-voting.
 *
 * <p>Everywhere else {@code ExternalVotingPort} is mocked, which means the integration with
 * the one service that has actually been extracted was verified by nobody. That gap was not
 * theoretical: the published image could not start against an empty database for weeks —
 * Spring Boot 4 moved Flyway's autoconfiguration into its own module, so no migration ran —
 * and every suite stayed green throughout, because a mock does not need a schema.
 *
 * <p>Three containers on a shared network, plus the two {@link PostgresTestSupport} already
 * starts. This is the most expensive test in the repository and it earns that by covering
 * what nothing else can:
 *
 * <ul>
 *   <li>the HTTP contract — a vote returns real tallies, not a mocked shape;</li>
 *   <li>the token forward — nabat-voting derives the voter from the caller's own access
 *       token, so the two services must share a signing secret and agree on the claim;</li>
 *   <li>the Kafka round trip — the vote is published, consumed, and reaches the credibility
 *       projection;</li>
 *   <li>the failure mapping — with the service stopped, a vote is a 503 and not a 500.</li>
 * </ul>
 *
 * <p>It found two real defects on its first run. The image could not migrate its schema at
 * all, because Spring Boot 4 moved Flyway's autoconfiguration into a module the pom did not
 * declare; that one is fixed and published. And the credibility projection races: the vote
 * event is published inside the transaction that writes the vote, so a consumer that
 * recomputes from the write model can get there first and store zeros — observed both ways,
 * zeros in the compose stack and the correct count here. Neither outcome is asserted; see
 * {@link #theWriteModelHoldsTheVoteRegardlessOfHowTheProjectionRaceGoes()}.
 *
 * <p><b>Which image.</b> The default is the published one,
 * {@code ghcr.io/martog232/nabat-voting-app:latest}. To test a change to nabat-voting that
 * is not published yet, build it and point {@code -Dnabat.voting.image} at the result:
 * <pre>
 *   (cd ../nabat-voting &amp;&amp; docker build -t nabat-voting-app:local .)
 *   mvnw -Dnabat.voting.image=nabat-voting-app:local -Dtest=VotingServiceIntegrationTest test
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VotingServiceIntegrationTest extends PostgresTestSupport {

    /** Shared by nabat-app's test context and the container, or every forwarded token is a 401. */
    private static final String JWT_SECRET =
        "test-jwt-secret-key-for-testing-only-min-256-bits-required-do-not-use-in-production";

    private static final String VOTING_IMAGE =
        System.getProperty("nabat.voting.image", "ghcr.io/martog232/nabat-voting-app:latest");

    private static final Network NETWORK = Network.newNetwork();

    private static final PostgreSQLContainer<?> VOTING_DB =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withNetwork(NETWORK)
            .withNetworkAliases("voting-postgres")
            .withDatabaseName("nabat_voting_db")
            .withUsername("nabat_voting_user")
            .withPassword("nabat_voting_password");

    /**
     * A plain container rather than the Testcontainers Kafka module: this is the exact
     * KRaft configuration docker-compose.yml runs, and the listener setup is the part worth
     * keeping identical. Only nabat-voting talks to it, so there is no EXTERNAL listener —
     * the test observes Kafka through the projection it feeds, not by consuming the topic.
     */
    private static final GenericContainer<?> KAFKA =
        new GenericContainer<>(DockerImageName.parse("apache/kafka:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka")
            .withEnv("KAFKA_NODE_ID", "1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withEnv("KAFKA_LISTENERS", "INTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093")
            .withEnv("KAFKA_ADVERTISED_LISTENERS", "INTERNAL://kafka:9092")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "INTERNAL")
            .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                     "CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT")
            .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@kafka:9093")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("CLUSTER_ID", "DWKW8vl4RI6tPZGCbhhHVg")
            .waitingFor(Wait.forLogMessage(".*Kafka Server started.*", 1)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    private static final GenericContainer<?> VOTING =
        new GenericContainer<>(DockerImageName.parse(VOTING_IMAGE))
            .withNetwork(NETWORK)
            .withEnv("SPRING_DATASOURCE_URL",
                     "jdbc:postgresql://voting-postgres:5432/nabat_voting_db")
            .withEnv("SPRING_DATASOURCE_USERNAME", "nabat_voting_user")
            .withEnv("SPRING_DATASOURCE_PASSWORD", "nabat_voting_password")
            .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
            .withEnv("SERVER_PORT", "8081")
            .withEnv("JWT_SECRET", JWT_SECRET)
            .withExposedPorts(8081)
            // Health, not a log line: this waits for the schema to be migrated and the
            // Kafka consumer to be up, which is what "ready to take a vote" means here.
            .waitingFor(Wait.forHttp("/actuator/health")
                            .forPort(8081)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        VOTING_DB.start();
        KAFKA.start();
        VOTING.start();
    }

    @DynamicPropertySource
    static void votingService(DynamicPropertyRegistry registry) {
        registry.add("nabat.voting.service.base-url",
            () -> "http://" + VOTING.getHost() + ":" + VOTING.getMappedPort(8081));
        // The default read timeout is 3s, which a cold container occasionally exceeds on the
        // first call. Raised here only; production values stay as they are.
        registry.add("nabat.voting.service.read-timeout", () -> "PT10S");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailSender emailSender;

    private static String accessToken;
    private static String alertId;

    @BeforeAll
    static void report() {
        System.out.println("nabat-voting image under test: " + VOTING_IMAGE);
    }

    @Test
    @Order(1)
    void aVoteReachesTheServiceAndComesBackWithRealTallies() throws Exception {
        accessToken = register("voting-it@example.com", "Votingtest123");
        alertId = createAlert(accessToken);

        mockMvc.perform(post("/api/v1/alerts/{id}/votes", alertId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteType\":\"CONFIRM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.voteType").value("CONFIRM"))
                // The tallies come back on the write, which is why callers must not follow a
                // vote with getVoteStats — that reads an asynchronous projection.
                .andExpect(jsonPath("$.stats.confirmations").value(1));
    }

    /**
     * The Kafka round trip: published by the write path, consumed, and applied to the
     * credibility projection.
     *
     * <p>Asserted against the voting service's own database rather than through the API,
     * because from outside a missing projection row and a zeroed one look identical —
     * {@code getVoteStats} answers {@code EMPTY} for both. Reaching into another service's
     * schema is not something production code may do; a test that owns the container is the
     * one place it buys something, and what it buys is the difference between "the event
     * arrived" and "the event never left".
     */
    @Test
    @Order(2)
    void theVoteEventReachesTheProjectionThroughKafka() {
        await().atMost(Duration.ofSeconds(60))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted(() -> assertThat(projectionRowExistsFor(alertId))
                .as("alert_credibility row written by the vote.cast consumer")
                .isTrue());
    }

    /**
     * The write model is authoritative; the projection is not, and deliberately is not
     * asserted on.
     *
     * <p>The projection recomputes from the {@code votes} table whenever an event arrives,
     * and the event is published <em>inside</em> the transaction that writes the vote. So the
     * consumer races the commit: win, and the row is correct; lose, and it recomputes against
     * a table that does not yet hold the vote and writes zeros — permanently, because
     * recomputation only runs again on the next event for that alert.
     *
     * <p>Both outcomes were observed while writing this test: zeros in the compose stack, the
     * correct count here. That non-determinism is the defect, and it is the "Kafka dual write"
     * row in AGENTS.md made concrete. Pinning either outcome would produce a flaky test that
     * says nothing, so this asserts only what the design actually guarantees — and Phase 2's
     * outbox is what turns the projection into something a test may assert on.
     */
    @Test
    @Order(3)
    void theWriteModelHoldsTheVoteRegardlessOfHowTheProjectionRaceGoes() throws Exception {
        assertThat(voteRowsFor(alertId))
            .as("the vote itself — committed, and what the caller's own response was read from")
            .isEqualTo(1);

        MvcResult result = mockMvc.perform(get("/api/v1/alerts/{id}/votes/stats", alertId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        // Recorded, not asserted: 0 and 1 are both reachable today, which is the point.
        JsonNode stats = objectMapper.readTree(result.getResponse().getContentAsString());
        System.out.println("projection confirmations after the vote (0 or 1, racy by design): "
                           + stats.get("confirmations").asInt());
    }

    /** Casting the same vote twice is a conflict there, and must arrive here as 409, not 503. */
    @Test
    @Order(4)
    void aDuplicateVoteIsAConflictRatherThanAnOutage() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/{id}/votes", alertId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteType\":\"CONFIRM\"}"))
                .andExpect(status().isConflict());
    }

    /**
     * Runs last, because it stops the service.
     *
     * <p>503 and not 500: the frontend silently ignores vote conflicts, so an outage reported
     * as a 4xx disappears from view. This is the mapping AGENTS.md says not to collapse, and
     * the only way to check it is to actually take the dependency away.
     */
    @Test
    @Order(5)
    void anOutageIsA503() throws Exception {
        VOTING.stop();

        mockMvc.perform(post("/api/v1/alerts/{id}/votes", alertId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voteType\":\"UPVOTE\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    private static boolean projectionRowExistsFor(String alertId) throws Exception {
        return countIn("SELECT count(*) FROM alert_credibility WHERE alert_id = ?", alertId) == 1;
    }

    private static int voteRowsFor(String alertId) throws Exception {
        return countIn("SELECT count(*) FROM votes WHERE alert_id = ?", alertId);
    }

    private static int countIn(String sql, String alertId) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                VOTING_DB.getJdbcUrl(), VOTING_DB.getUsername(), VOTING_DB.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, alertId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                 + "\",\"displayName\":\"Voting IT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("accessToken").asText();
    }

    private String createAlert(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"Voting integration",
                             "description":"An alert to vote on",
                             "type":"HAZARD","severity":"LOW",
                             "latitude":42.6977,"longitude":23.3219}"""))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
