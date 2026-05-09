package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Alert details returned by the API")
public record AlertResponse(
    @Schema(description = "Unique identifier of the alert") UUID id,
    @Schema(description = "Short title") String title,
    @Schema(description = "Full description") String description,
    @Schema(description = "Alert category") AlertType type,
    @Schema(description = "Severity level") AlertSeverity severity,
    @Schema(description = "Latitude of the incident location", example = "40.7128") double latitude,
    @Schema(description = "Longitude of the incident location", example = "-74.0060") double longitude,
    @Schema(description = "Timestamp when the alert was created") Instant createdAt,
    @Schema(description = "Current lifecycle status") AlertStatus status,
    @Schema(description = "ID of the user who reported the alert") UUID reportedBy,
    @Schema(description = "Number of upvotes") int upvoteCount,
    @Schema(description = "Number of downvotes") int downvoteCount,
    @Schema(description = "Number of confirmations") int confirmationCount,
    @Schema(description = "Timestamp when the alert was resolved; null if still active") Instant resolvedAt
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
            alert.status(),
            alert.reportedBy(),
            alert.upvoteCount(),
            alert.downvoteCount(),
            alert.confirmationCount(),
            alert.resolvedAt()
        );
    }
}
