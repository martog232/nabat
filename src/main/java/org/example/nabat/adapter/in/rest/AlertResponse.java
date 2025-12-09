package org.example.nabat.adapter.in.rest;

import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
    UUID id,
    String title,
    String description,
    AlertType type,
    AlertSeverity severity,
    double latitude,
    double longitude,
    Instant createdAt,
    AlertStatus status
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
            alert.id().value(),
            alert.title(),
            alert.description(),
            alert.type(),
            alert.severity(),
            alert.location().latitude(),
            alert.location().longitude(),
            alert.createdAt(),
            alert.status()
        );
    }
}
