package org.example.nabat.domain.model;

import java.time.Instant;

public record AlertVote(
        AlertVoteId id,         // Уникален идентификатор
        AlertId alertId,        // Към кой alert е гласът
        UserId userId,          // Кой потребител е гласувал
        VoteType voteType,      // Тип на гласа
        Instant createdAt       // Кога е гласувано
) {
    // Factory метод за създаване на нов глас
    public static AlertVote create(AlertId alertId, UserId userId, VoteType voteType) {
        return new AlertVote(
                AlertVoteId.generate(),
                alertId,
                userId,
                voteType,
                Instant.now()
        );
    }
}
