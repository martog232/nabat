package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;

import java.time.Instant;
import java.util.UUID;

public interface ExternalVotingPort {

    VoteReceipt vote(AlertId alertId, UserId userId, VoteType voteType);

    void removeVote(AlertId alertId, UserId userId);

    boolean hasUserVoted(AlertId alertId, UserId userId);

    VoteStats getVoteStats(AlertId alertId);

    record VoteReceipt(
            UUID id,
            AlertId alertId,
            VoteType voteType,
            Instant createdAt
    ) {
    }

    record VoteStats(
            int upvotes,
            int downvotes,
            int confirmations,
            int credibilityScore
    ) {
    }
}
