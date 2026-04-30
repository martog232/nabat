package org.example.nabat.domain.model;

import java.time.Instant;

public record AlertVote(
        AlertVoteId id,
        AlertId alertId,
        UserId userId,
        VoteType voteType,
        Instant createdAt
) {
    public static AlertVote create(AlertId alertId, UserId userId, VoteType voteType) {
        return new AlertVote(
                AlertVoteId.generate(),
                alertId,
                userId,
                voteType,
                Instant.now()
        );
    }

    /** Returns a copy with a new vote type, preserving the id (for idempotent switch). */
    public AlertVote withVoteType(VoteType newType) {
        return new AlertVote(id, alertId, userId, newType, Instant.now());
    }
}
