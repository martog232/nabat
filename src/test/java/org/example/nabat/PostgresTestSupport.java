package org.example.nabat;

import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Canonical Testcontainers base for every integration / persistence test in Nabat.
 *
 * <p>Postgres and Redis containers are shared across all test classes (static fields) which,
 * combined with Spring Boot's application-context cache, keeps the suite fast: each container
 * starts once per JVM, and each unique Spring context is created once and reused.
 *
 * <h2>Why Redis is here</h2>
 * Redis is not optional for this application. WebSocket ticket redemption
 * ({@code RedisWebSocketTicketRepository}), single-use refresh-token tracking
 * ({@code RedisRefreshTokenStore}), the nearby-alerts cache and cross-instance WebSocket
 * relay all go through it.
 *
 * <p>There was no Redis container, and {@code src/test/resources/application.properties}
 * overrode nothing, so integration tests inherited the production default of
 * {@code 127.0.0.1:6379} and ran against a Redis that was not there. Locally that went
 * unnoticed because these tests skip without Docker; on CI, where Docker exists and they do
 * run, every Redis-backed path was exercised against a refused connection — so
 * {@code POST /api/v1/ws/tickets} could not return the 201 its test asserts, and the
 * refresh-token flow could not record a {@code jti} as consumed.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // @SpringBootTest integration test
 * @SpringBootTest
 * @AutoConfigureMockMvc
 * class MyControllerIntegrationTest extends PostgresTestSupport { ... }
 *
 * // @DataJpaTest slice test
 * @DataJpaTest
 * @AutoConfigureTestDatabase(replace = NONE)
 * class MyRepositoryTest extends PostgresTestSupport { ... }
 * }</pre>
 *
 * <p>Tests are skipped automatically when Docker is not available
 * ({@code disabledWithoutDocker = true}).
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresTestSupport {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    private static final int REDIS_PORT = 6379;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("nabat_test")
            .withUsername("nabat")
            .withPassword("nabat");

    /**
     * Plain {@link GenericContainer} rather than the Testcontainers Redis module: the module
     * would be a new dependency and this needs nothing beyond a port.
     */
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    /**
     * Overrides the datasource, Redis and schema-management properties so every test type
     * (DataJpaTest slice or full SpringBootTest) talks to the shared containers.
     */
    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",           POSTGRES::getUsername);
        registry.add("spring.datasource.password",           POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Flyway creates/migrates the schema; Hibernate only validates.
        registry.add("spring.flyway.enabled",                () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto",        () -> "validate");
        // Suppress SMTP health-check noise in test output.
        registry.add("management.health.mail.enabled",       () -> "false");

        registry.add("spring.data.redis.host",               REDIS::getHost);
        registry.add("spring.data.redis.port",       () -> REDIS.getMappedPort(REDIS_PORT));
    }
}
