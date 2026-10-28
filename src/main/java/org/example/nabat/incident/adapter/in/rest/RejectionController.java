package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.RejectReportUseCase;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.ReportRejection;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Rejecting a report, and contesting it.
 *
 * <p>{@code PATCH /reject} is the report's authority or a moderator, with a reason — 400
 * without one. {@code POST /rejections/dispute} is the reporter, once — 409 if the report is
 * not rejected, the rejection predates V31, or it is disputed already. {@code POST
 * /rejections/decision} and {@code GET /disputed} are moderators'.
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class RejectionController {

    private final RejectReportUseCase rejecting;

    public RejectionController(RejectReportUseCase rejecting) {
        this.rejecting = rejecting;
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Close a report without a fix — a duplicate, not a real problem — saying why")
    public ResponseEntity<AlertResponse> reject(
        @PathVariable UUID id,
        @Valid @RequestBody ReasonRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(AlertResponse.from(rejecting.reject(AlertId.of(id), currentUser, request.reason())));
    }

    @GetMapping("/{id}/rejections")
    @SecurityRequirements
    @Operation(summary = "The report's rejections, oldest first, with their disputes and decisions")
    public ResponseEntity<List<RejectionResponse>> rejections(@PathVariable UUID id) {
        return ResponseEntity.ok(rejecting.rejections(AlertId.of(id)).stream().map(RejectionResponse::from).toList());
    }

    @PostMapping("/{id}/rejections/dispute")
    @Operation(summary = "The reporter contests the rejection of their report, once, saying why")
    public ResponseEntity<RejectionResponse> dispute(
        @PathVariable UUID id,
        @Valid @RequestBody ReasonRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(RejectionResponse.from(rejecting.dispute(AlertId.of(id), currentUser, request.reason())));
    }

    @PostMapping("/{id}/rejections/decision")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    @Operation(summary = "A moderator upholds a disputed rejection or reopens the report")
    public ResponseEntity<AlertResponse> decide(
        @PathVariable UUID id,
        @Valid @RequestBody DecisionRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(AlertResponse.from(rejecting.decide(AlertId.of(id), currentUser, request.decision())));
    }

    @GetMapping("/disputed")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    @Operation(summary = "Reports whose rejection the reporter disputes, waiting for a moderator, oldest dispute first")
    public ResponseEntity<List<AlertResponse>> disputed(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(rejecting.disputed(currentUser).stream().map(AlertResponse::from).toList());
    }

    @Schema(description = "Why")
    public record ReasonRequest(
        @Schema(description = "1 to 1000 characters", example = "Дублира сигнал от вчера на същото място.")
        @NotBlank(message = "A reason is required")
        @Size(max = ReportRejection.MAX_REASON_LENGTH, message = "A reason is at most 1000 characters")
        String reason
    ) {
    }

    @Schema(description = "A moderator's answer to a disputed rejection")
    public record DecisionRequest(
        @Schema(description = "UPHELD — the rejection stands; REOPENED — the report goes back to its authority")
        @NotNull(message = "A decision is required")
        ReportRejection.Decision decision
    ) {
    }

    @Schema(description = "A rejection of a report, and what came of it")
    public record RejectionResponse(
        @Schema(description = "Why it was rejected") String reason,
        @Schema(description = "Rejected by a moderator rather than by the report's authority") boolean byModerator,
        @Schema(description = "The authority the report was with", example = "LOZENETS") String authorityCode,
        Instant rejectedAt,
        @Schema(description = "The reporter's dispute; null if they did not dispute it") String disputeReason,
        Instant disputedAt,
        @Schema(description = "A moderator's decision on the dispute; null until there is one") ReportRejection.Decision decision,
        Instant decidedAt
    ) {
        static RejectionResponse from(ReportRejection r) {
            return new RejectionResponse(r.reason(), r.byModerator(), r.authorityCode(), r.rejectedAt(),
                r.disputeReason(), r.disputedAt(), r.decision(), r.decidedAt());
        }
    }
}
