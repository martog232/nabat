package org.example.nabat.voting.adapter.in.kafka;

import lombok.RequiredArgsConstructor;
import org.example.nabat.events.vote.VoteChanged;
import org.example.nabat.events.vote.VoteTallies;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.voting.application.port.in.ApplyVoteTalliesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Applies nabat-voting's vote events to this service's copy of the counts.
 *
 * <p>This is the first place nabat-app consumes another service's events rather than calling
 * it. The vote itself is still a synchronous call — the caller has to be told whether their
 * vote was accepted — but the *consequence* of the vote on this side no longer rides on that
 * response.
 *
 * <p>Casts and retractions arrive on one topic, keyed by alert, and this listener treats them
 * alike: both say what the counts became. That gives the two properties it depends on —
 * absolute values, so a redelivery is the same write, and one partition per alert, so a vote
 * and an immediate retraction cannot be applied in the wrong order. They were two topics
 * once, which gave the second property to nobody: two containers polled them independently,
 * and a consumer that applies the counts it is told (this one does; nabat-voting's own
 * recomputes instead) could end up one vote off until the next event for that alert.
 *
 * <p>Inactive unless {@code nabat.kafka.enabled} is set. Where there is no broker there is no
 * nabat-voting either — it cannot start without one — so there are no votes to project, and
 * a listener whose only effect would be a connection warning every second is worse than
 * absent.
 */
@Component
@ConditionalOnProperty(name = "nabat.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class VoteEventListener {

    static final String VOTE_CHANGED_TOPIC = "vote.changed";

    private static final Logger log = LoggerFactory.getLogger(VoteEventListener.class);

    private final ApplyVoteTalliesUseCase applyVoteTallies;

    @KafkaListener(
        topics = VOTE_CHANGED_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "voteEventListenerContainerFactory"
    )
    public void onVoteChanged(VoteChanged message) {
        apply(VOTE_CHANGED_TOPIC, message);
    }

    private void apply(String topic, VoteChanged message) {
        AlertId alertId = parseAlertId(topic, message.getAlertId());
        if (alertId == null) {
            return;
        }

        // No check that the tallies are there: the schema makes them a required field, so a
        // message without them cannot be written, let alone read. That guard used to exist
        // and was doing the schema's job by hand.
        VoteTallies tallies = message.getTallies();
        applyVoteTallies.applyTallies(new ApplyVoteTalliesUseCase.VoteTalliesUpdate(
                alertId,
                tallies.getUpvotes(),
                tallies.getDownvotes(),
                tallies.getConfirmations(),
                tallies.getCredibilityScore()
        ));
    }

    /**
     * Returns null for an id this service cannot use.
     *
     * <p>Handled here rather than left to the error handler because no number of retries
     * turns an unparseable id into a valid one; the retries would only delay the rest of the
     * partition.
     */
    private static AlertId parseAlertId(String topic, String rawAlertId) {
        try {
            return AlertId.of(UUID.fromString(rawAlertId));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Event on {} names alert '{}', which is not an id this service can use; skipping",
                    topic, rawAlertId);
            return null;
        }
    }
}
