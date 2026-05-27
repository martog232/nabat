package org.example.nabat.domain.event;

import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;

import java.time.Instant;

public record VoteCastEvent(
        AlertId alertId,
        UserId voterId,
        VoteType voteType,
        Instant occurredAt
) {
}

