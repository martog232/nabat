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
    UUID reportedBy
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
            reportedBy
        );
    }

    public Alert resolve() {
        return new Alert(id, title, description, type, severity,
                         location, createdAt, AlertStatus.RESOLVED, reportedBy);
    }

    public boolean isWithinRadius(Location userLocation, double radiusKm) {
        return location. distanceTo(userLocation) <= radiusKm;
    }
}