package org.example.nabat.voting.application.port.in;

import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.voting.domain.VoteType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VoteAlertUseCase {

    VoteReceipt vote(VoteCommand command);

    /** @return the tallies after the vote was removed */
    VoteStats removeVote(AlertId alertId, UserId userId);

    /**
     * The user's current vote on the alert.
     *
     * <p>Returns {@link Optional} rather than a nullable {@code VoteType}: the old
     * name ({@code hasUserVoted}) read as a predicate while actually returning an
     * enum-or-null, which forced every caller into a null check.
     */
    Optional<VoteType> findUserVote(AlertId alertId, UserId userId);

    VoteStats getVoteStats(AlertId alertId);

    record VoteCommand(
            AlertId alertId,
            UserId userId,
            VoteType voteType
    ) {
    }

    /** @param stats tallies as of this vote, so callers need no follow-up read */
    record VoteReceipt(
            UUID id,
            AlertId alertId,
            VoteType voteType,
            Instant createdAt,
            VoteStats stats
    ) {
    }

    /**
     * Vote tallies plus the credibility score.
     *
     * <p>The score is supplied by the voting service, which owns the formula. This
     * record deliberately does not recompute it — there used to be four independent
     * copies of {@code upvotes - downvotes + 2 * confirmations} across the two
     * backends and the frontend.
     */
    record VoteStats(
            int upvotes,
            int downvotes,
            int confirmations,
            int credibilityScore
    ) {
        public static final VoteStats EMPTY = new VoteStats(0, 0, 0, 0);
    }
}
