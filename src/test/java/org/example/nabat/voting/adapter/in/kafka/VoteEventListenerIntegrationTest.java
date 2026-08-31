package org.example.nabat.voting.adapter.in.kafka;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.events.vote.VoteChangeType;
import org.example.nabat.events.vote.VoteChanged;
import org.example.nabat.events.vote.VoteKind;
import org.example.nabat.events.vote.VoteTallies;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The vote counts on an alert, written by an event rather than by the request that caused it.
 *
 * <p>The messages here are built from the same schema nabat-voting publishes with —
 * {@code src/main/avro/VoteChanged.avsc}, a verbatim copy of the file in that repository —
 * and serialised the same way, schema id and all. The test used to write the JSON by hand
 * because the shape was an agreement in prose; it is a file now, and a rename on either side
 * fails at the registry rather than in production.
 *
 * <p>What this cannot check is that the two copies of that file are still the same. That is
 * {@code VotingServiceIntegrationTest}, which runs the real service against a real registry.
 */
@SpringBootTest(properties = "nabat.kafka.enabled=true")
@EmbeddedKafka(
        partitions = 1,
        topics = {VoteEventListener.VOTE_CHANGED_TOPIC},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class VoteEventListenerIntegrationTest extends PostgresTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Value("${nabat.schema-registry.url}")
    private String schemaRegistryUrl;

    private KafkaTemplate<String, VoteChanged> producer;
    private AlertId alertId;

    @BeforeEach
    void setUp() {
        Map<String, Object> properties = new HashMap<>(KafkaTestUtils.producerProps(broker));
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        // The same in-memory registry the application context resolves ids against, so the
        // id this producer writes is one the listener can look up.
        properties.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));

        UUID owner = userRepository.save(
                Fixtures.user("vote-events-" + UUID.randomUUID() + "@example.com")).id().value();
        alertId = AlertId.generate();
        alertRepository.save(Fixtures.alert(alertId, owner));
    }

    private void send(VoteChanged message) {
        producer.send(VoteEventListener.VOTE_CHANGED_TOPIC, message.getAlertId(), message);
    }

    private VoteChanged cast(VoteKind voteType, int upvotes, int downvotes, int confirmations, int score) {
        return VoteChanged.newBuilder()
                .setChangeType(VoteChangeType.CAST)
                .setVoteId(UUID.randomUUID().toString())
                .setAlertId(alertId.value().toString())
                .setVoterId(UUID.randomUUID().toString())
                .setVoteType(voteType)
                .setOccurredAt(Instant.now())
                .setTallies(tallies(upvotes, downvotes, confirmations, score))
                .build();
    }

    private VoteChanged retraction(int upvotes, int downvotes, int confirmations, int score) {
        return VoteChanged.newBuilder()
                .setChangeType(VoteChangeType.REMOVED)
                .setAlertId(alertId.value().toString())
                .setVoterId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now())
                .setTallies(tallies(upvotes, downvotes, confirmations, score))
                .build();
    }

    private static VoteTallies tallies(int upvotes, int downvotes, int confirmations, int score) {
        return VoteTallies.newBuilder()
                .setUpvotes(upvotes)
                .setDownvotes(downvotes)
                .setConfirmations(confirmations)
                .setCredibilityScore(score)
                .build();
    }

    @Test
    void aCastWritesTheCountsOntoTheAlert() {
        send(cast(VoteKind.CONFIRM, 3, 1, 2, 6));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Alert alert = alertRepository.findById(alertId).orElseThrow();
            assertThat(alert.upvoteCount()).isEqualTo(3);
            assertThat(alert.downvoteCount()).isEqualTo(1);
            assertThat(alert.confirmationCount()).isEqualTo(2);
            // Carried by the message, not recomputed here: the formula lives in nabat-voting.
            assertThat(alert.credibilityScore()).isEqualTo(6);
        });
    }

    /** A retraction has no vote and no vote type, and is otherwise the same message. */
    @Test
    void aRetractionWritesTheCountsThatAreLeft() {
        send(retraction(1, 0, 0, 1));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Alert alert = alertRepository.findById(alertId).orElseThrow();
            assertThat(alert.upvoteCount()).isEqualTo(1);
            assertThat(alert.credibilityScore()).isEqualTo(1);
        });
    }

    /**
     * A vote and an immediate retraction, in that order, end at the retraction's counts.
     *
     * <p>This is the assertion two topics could not support. Same key means one partition,
     * which means these are applied in the order they were sent; with a topic each, two
     * listener containers polled independently and the cast could be applied second, leaving
     * the alert one vote high until the next event for it. Nothing was wrong with either
     * message — only with the order, and nothing ordered them.
     *
     * <p>The alert is seeded with counts that neither message carries, so reaching the
     * retraction's numbers cannot be an accident of where it started.
     */
    @Test
    void aRetractionAfterACastLeavesTheRetractionsCounts() {
        alertRepository.applyVoteCounts(alertId, 9, 9, 9, 9);

        send(cast(VoteKind.UPVOTE, 1, 0, 0, 1));
        send(retraction(0, 0, 0, 0));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Alert alert = alertRepository.findById(alertId).orElseThrow();
            assertThat(alert.upvoteCount()).isZero();
            assertThat(alert.credibilityScore()).isZero();
        });
    }
}
