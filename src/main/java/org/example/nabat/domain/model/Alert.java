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
        int confirmationCount,
        Instant resolvedAt
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
                0, 0, 0,
                null
        );
    }

    /** Returns a copy of this alert marked as RESOLVED. Idempotent for already-resolved alerts. */
    public Alert resolve() {
        if (status == AlertStatus.RESOLVED) {
            return this;
        }
        return new Alert(
                id, title, description, type, severity, location,
                createdAt, AlertStatus.RESOLVED, reportedBy,
                upvoteCount, downvoteCount, confirmationCount,
                Instant.now()
        );
    }

    public int getCredibilityScore() {
        // Confirmations weigh double because they imply the reporter is on-site.
        return upvoteCount - downvoteCount + (confirmationCount * 2);
    }
}