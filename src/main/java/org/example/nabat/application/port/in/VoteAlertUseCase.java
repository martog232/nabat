package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;

import java.time.Instant;
import java.util.UUID;

public interface VoteAlertUseCase {

    VoteReceipt vote(VoteCommand command);

    void removeVote(AlertId alertId, UserId userId);

    boolean hasUserVoted(AlertId alertId, UserId userId);

    VoteStats getVoteStats(AlertId alertId);

    record VoteCommand(
            AlertId alertId,
            UserId userId,
            VoteType voteType
    ) {
    }

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
        public static VoteStats of(int upvotes, int downvotes, int confirmations) {
            int credibilityScore = upvotes - downvotes + (confirmations * 2);
            return new VoteStats(upvotes, downvotes, confirmations, credibilityScore);
        }

        public static VoteStats of(int upvotes, int downvotes, int confirmations, int credibilityScore) {
            return new VoteStats(upvotes, downvotes, confirmations, credibilityScore);
        }
    }
}
