package org.example.nabat.voting.adapter.in.kafka;

import org.apache.kafka.common.serialization.StringSerializer;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The vote counts on an alert, written by an event rather than by the request that caused it.
 *
 * <p>The JSON below is written by hand on purpose. It is nabat-voting's schema, from a
 * separate repository and a separate build, so the contract between the two is a shape agreed
 * in prose — not a shared class that a rename would quietly keep in step. Pinning it here
 * means a producer-side rename fails on this side as well, which is where it would otherwise
 * be discovered in production. That the real producer still emits this shape is what
 * {@code VotingServiceIntegrationTest} checks, by running the actual service.
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

    private KafkaTemplate<String, String> producer;
    private AlertId alertId;

    @BeforeEach
    void setUp() {
        // A string producer, because that is what the outbox relay is: it ships the JSON it
        // stored, with no type headers for the consumer to lean on.
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));

        UUID owner = userRepository.save(
                Fixtures.user("vote-events-" + UUID.randomUUID() + "@example.com")).id().value();
        alertId = AlertId.generate();
        alertRepository.save(Fixtures.alert(alertId, owner));
    }

    @Test
    void aCastWritesTheCountsOntoTheAlert() {
        producer.send(VoteEventListener.VOTE_CHANGED_TOPIC, alertId.value().toString(), """
            {"changeType":"CAST",
             "voteId":"%s",
             "alertId":"%s",
             "voterId":"%s",
             "voteType":"CONFIRM",
             "occurredAt":"2026-08-31T10:00:00Z",
             "tallies":{"upvotes":3,"downvotes":1,"confirmations":2,"credibilityScore":6}}"""
            .formatted(UUID.randomUUID(), alertId.value(), UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Alert alert = alertRepository.findById(alertId).orElseThrow();
            assertThat(alert.upvoteCount()).isEqualTo(3);
            assertThat(alert.downvoteCount()).isEqualTo(1);
            assertThat(alert.confirmationCount()).isEqualTo(2);
            // Carried by the event, not recomputed here: the formula lives in nabat-voting.
            assertThat(alert.credibilityScore()).isEqualTo(6);
        });
    }

    /** A retraction has no vote and no vote type, and is otherwise the same message. */
    @Test
    void aRetractionWritesTheCountsThatAreLeft() {
        producer.send(VoteEventListener.VOTE_CHANGED_TOPIC, alertId.value().toString(), """
            {"changeType":"REMOVED",
             "voteId":null,
             "alertId":"%s",
             "voterId":"%s",
             "voteType":null,
             "occurredAt":"2026-08-31T10:05:00Z",
             "tallies":{"upvotes":1,"downvotes":0,"confirmations":0,"credibilityScore":1}}"""
            .formatted(alertId.value(), UUID.randomUUID()));

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

        producer.send(VoteEventListener.VOTE_CHANGED_TOPIC, alertId.value().toString(), """
            {"changeType":"CAST","voteId":"%s","alertId":"%s","voterId":"%s","voteType":"UPVOTE",
             "occurredAt":"2026-08-31T11:00:00Z",
             "tallies":{"upvotes":1,"downvotes":0,"confirmations":0,"credibilityScore":1}}"""
            .formatted(UUID.randomUUID(), alertId.value(), UUID.randomUUID()));

        producer.send(VoteEventListener.VOTE_CHANGED_TOPIC, alertId.value().toString(), """
            {"changeType":"REMOVED","voteId":null,"alertId":"%s","voterId":"%s","voteType":null,
             "occurredAt":"2026-08-31T11:00:01Z",
             "tallies":{"upvotes":0,"downvotes":0,"confirmations":0,"credibilityScore":0}}"""
            .formatted(alertId.value(), UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Alert alert = alertRepository.findById(alertId).orElseThrow();
            assertThat(alert.upvoteCount()).isZero();
            assertThat(alert.credibilityScore()).isZero();
        });
    }

    /**
     * An event from a producer that predates the tallies leaves the counts alone.
     *
     * <p>The alternative — reading absent counts as zeros — would write the wrong numbers and
     * keep them until the next vote on that alert, which is exactly the failure this whole
     * change removed elsewhere.
     */
    @Test
    void anEventWithoutTalliesIsSkippedRatherThanTreatedAsZeros() throws Exception {
        alertRepository.applyVoteCounts(alertId, 7, 0, 0, 7);

        producer.send(VoteEventListener.VOTE_CHANGED_TOPIC, alertId.value().toString(), """
            {"changeType":"CAST","voteId":"%s","alertId":"%s","voterId":"%s","voteType":"UPVOTE",
             "occurredAt":"2026-08-31T10:10:00Z"}"""
            .formatted(UUID.randomUUID(), alertId.value(), UUID.randomUUID()));

        // Then one that does carry them, on the same key and so the same partition: once its
        // counts are visible, the one before it has certainly been consumed.
        producer.send(VoteEventListener.VOTE_CHANGED_TOPIC, alertId.value().toString(), """
            {"changeType":"CAST","voteId":"%s","alertId":"%s","voterId":"%s","voteType":"UPVOTE",
             "occurredAt":"2026-08-31T10:11:00Z",
             "tallies":{"upvotes":8,"downvotes":0,"confirmations":0,"credibilityScore":8}}"""
            .formatted(UUID.randomUUID(), alertId.value(), UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(alertRepository.findById(alertId).orElseThrow().upvoteCount()).isEqualTo(8));
    }
}
