package org.example.nabat.voting.application.port.in;

import org.example.nabat.incident.domain.AlertId;

/**
 * Writes an alert's vote counts as reported by the voting service.
 *
 * <p>Separate from {@link VoteAlertUseCase} because it is driven by a different actor: that
 * one is a user casting a vote, this one is an event saying what the counts are now. The
 * counts arrive as absolute values, so applying the same update twice is the same write —
 * which is the property the caller needs, since Kafka delivery is at-least-once.
 */
public interface ApplyVoteTalliesUseCase {

    void applyTallies(VoteTalliesUpdate update);

    record VoteTalliesUpdate(
        AlertId alertId,
        int upvotes,
        int downvotes,
        int confirmations,
        int credibilityScore
    ) {
    }
}
