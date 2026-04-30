package org.example.nabat.domain.model;

import java.time.Instant;
import java.util.UUID;

public record UserSubscription(
        UUID id,
        UserId userId,
        AlertType alertType,
        Location center,
        double radiusKm,
        boolean active,
        Instant createdAt
) {
    public static UserSubscription create(
            UserId userId,
            AlertType alertType,
            Location center,
            double radiusKm
    ) {
        return new UserSubscription(
                UUID.randomUUID(),
                userId,
                alertType,
                center,
                radiusKm,
                true,
                Instant.now()
        );
    }
}

