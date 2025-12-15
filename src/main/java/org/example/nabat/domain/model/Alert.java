package org.example.nabat.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Alert(
        AlertId id,
        String title,
        String description,
        AlertType type,
        AlertSeverity severity,
        Location location,
        Instant createdAt,
        AlertStatus status,
        UUID reportedBy,
        int upvoteCount,
        int downvoteCount,
        int confirmationCount
) {
    public static Alert create(
            String title,
            String description,
            AlertType type,
            AlertSeverity severity,
            Location location,
            UUID reportedBy
    ) {
        return new Alert(
                AlertId.generate(),
                title,
                description,
                type,
                severity,
                location,
                Instant.now(),
                AlertStatus.ACTIVE,
                reportedBy,
                0, 0, 0
        );
    }

    public int getCredibilityScore() {
        return upvoteCount - downvoteCount + (confirmationCount * 2);
        // Потвържденията тежат двойно, защото човекът е на място
    }
}