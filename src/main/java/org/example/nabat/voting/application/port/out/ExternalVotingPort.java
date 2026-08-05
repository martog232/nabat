package org.example.nabat.voting.application.port.out;

import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.voting.domain.VoteType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridge to the nabat-voting service, which owns vote persistence.
 *
 * <p>The mutating operations return the resulting {@link VoteStats} rather than
 * requiring a follow-up {@link #getVoteStats} call. That is not just an
 * optimisation: {@code getVoteStats} is served from an asynchronously-maintained
 * projection, so reading it immediately after a write returned the *pre-write*
 * counts.
 */
public interface ExternalVotingPort {

    VoteResult vote(AlertId alertId, UserId userId, VoteType voteType);

    /** @return the tallies after removal */
    VoteStats removeVote(AlertId alertId, UserId userId);

    /** The user's current vote, or empty when they have not voted on this alert. */
    Optional<VoteType> findUserVote(AlertId alertId, UserId userId);

    /** Eventually-consistent aggregate stats. */
    VoteStats getVoteStats(AlertId alertId);

    /**
     * @param stats tallies as of the write, read from the voting service's write
     *              model inside its own transaction
     */
    record VoteResult(
            UUID id,
            AlertId alertId,
            VoteType voteType,
            Instant createdAt,
            VoteStats stats
    ) {
    }

    record VoteStats(
            int upvotes,
            int downvotes,
            int confirmations,
            int credibilityScore
    ) {
        public static final VoteStats EMPTY = new VoteStats(0, 0, 0, 0);
    }
}
