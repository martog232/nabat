package org.example.nabat.voting.adapter.in.kafka;

import lombok.RequiredArgsConstructor;
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
 * <p>Both topics are handled the same way, because both say what the counts became. Absolute
 * values, so a redelivery is the same write; per-alert order, because the alert id is the
 * message key.
 *
 * <p><b>Known gap: order holds within a topic, not across the two.</b> A vote and its
 * retraction go to different topics with their own listener containers, so a voter who
 * unvotes immediately can have the removal applied before the cast — leaving counts one vote
 * too high until the next event for that alert. nabat-voting's own consumer is immune
 * because it recomputes from the write model rather than applying what it is told; this one
 * cannot, not owning that model. The fix is a watermark: carry the event's timestamp, keep
 * the last applied one on the alert, and ignore anything older. It is not here yet — see the
 * gap table in AGENTS.md.
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

    static final String VOTE_CAST_TOPIC = "vote.cast";
    static final String VOTE_REMOVED_TOPIC = "vote.removed";

    private static final Logger log = LoggerFactory.getLogger(VoteEventListener.class);

    private final ApplyVoteTalliesUseCase applyVoteTallies;

    @KafkaListener(
        topics = VOTE_CAST_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "voteEventListenerContainerFactory"
    )
    public void onVoteCast(VoteTalliesMessage message) {
        apply(VOTE_CAST_TOPIC, message);
    }

    @KafkaListener(
        topics = VOTE_REMOVED_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "voteEventListenerContainerFactory"
    )
    public void onVoteRemoved(VoteTalliesMessage message) {
        apply(VOTE_REMOVED_TOPIC, message);
    }

    private void apply(String topic, VoteTalliesMessage message) {
        AlertId alertId = parseAlertId(topic, message.alertId());
        if (alertId == null) {
            return;
        }

        if (message.tallies() == null) {
            // Only an event published before the tallies were added to the schema, still in
            // nabat-voting's outbox across a deploy. Skipping loses one update, which the
            // next vote on that alert corrects; treating absent counts as zeros would write
            // the wrong numbers and keep them until then.
            log.warn("Event on {} for alert {} carries no tallies; skipping", topic, alertId.value());
            return;
        }

        VoteTalliesMessage.Tallies tallies = message.tallies();
        applyVoteTallies.applyTallies(new ApplyVoteTalliesUseCase.VoteTalliesUpdate(
                alertId,
                tallies.upvotes(),
                tallies.downvotes(),
                tallies.confirmations(),
                tallies.credibilityScore()
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
