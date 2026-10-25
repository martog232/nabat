package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ClosedBy;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Alert details returned by the API")
public record AlertResponse(
    @Schema(description = "Unique identifier of the alert") UUID id,
    @Schema(description = "Short title") String title,
    @Schema(description = "Full description") String description,
    @Schema(description = "Subcategory code; resolve the name and group with GET /api/v1/categories", example = "1007") int categoryCode,
    @Schema(description = "Latitude of the incident location", example = "40.7128") double latitude,
    @Schema(description = "Longitude of the incident location", example = "-74.0060") double longitude,
    @Schema(description = "Timestamp when the alert was created") Instant createdAt,
    @Schema(description = "Current lifecycle status") AlertStatus status,
    @Schema(description = "ID of the user who reported the alert") UUID reportedBy,
    @Schema(description = "Number of upvotes") int upvoteCount,
    @Schema(description = "Number of downvotes") int downvoteCount,
    @Schema(description = "Number of confirmations") int confirmationCount,
    // Included so clients can render credibility straight from a list response.
    // Its absence was why the frontend fetched /votes/stats per alert card — two
    // extra requests per alert — and then recomputed the score locally anyway.
    @Schema(description = "Credibility score as computed by the voting service") int credibilityScore,
    @Schema(description = "Timestamp when the alert was resolved; null if still active") Instant resolvedAt,
    @Schema(description = "URL of the uploaded photo, if any") String photoUrl,
    @Schema(description = "Code of the Sofia district the report is in; null only on reports older than districts", example = "LOZENETS") String districtCode,
    @Schema(description = "Who closed the report — the authority that answers for it, the reporter, or a moderator. Null while open, and on reports closed before this was recorded", example = "AUTHORITY") ClosedBy closedBy,
    @Schema(description = "Whether people say a resolved report is really fixed. All zero and UNCHECKED on reports nobody has checked") FixCheckSummary fixCheck,
    @Schema(description = "Who answers for the report: its district, or the city-wide institution its category belongs to. See GET /api/v1/authorities for names", example = "LOZENETS") String authorityCode,
    @Schema(description = "When its authority has to have answered by; null when nothing is waiting on it. Past this, the report is overdue") Instant answerDueAt
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
            alert.id().value(),
            alert.title(),
            alert.description(),
            alert.categoryCode(),
            alert.location().latitude(),
            alert.location().longitude(),
            alert.createdAt(),
            alert.status(),
            alert.reportedBy(),
            alert.upvoteCount(),
            alert.downvoteCount(),
            alert.confirmationCount(),
            alert.credibilityScore(),
            alert.resolvedAt(),
            alert.photoUrl(),
            alert.districtCode(),
            alert.closedBy(),
            FixCheckSummary.from(alert.fixCheck()),
            alert.authorityCode(),
            alert.answerDueAt()
        );
    }
}
