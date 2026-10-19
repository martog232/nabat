package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.ForwardReportUseCase;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.ReportForward;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Forwarding a report to the authority it belongs to. {@code POST} is the report's own
 * authority or a moderator — 403 for anyone else, and for the authority once the report has
 * been forwarded three times; 400 without a reason or to an unknown or the same authority; 409
 * on a closed report. {@code GET} is the chain so far.
 */
@RestController
@RequestMapping("/api/v1/alerts/{id}")
public class ForwardController {

    private final ForwardReportUseCase forwarding;

    public ForwardController(ForwardReportUseCase forwarding) {
        this.forwarding = forwarding;
    }

    @PostMapping("/forward")
    @Operation(summary = "Hand the report to the authority it belongs to, saying why")
    public ResponseEntity<AlertResponse> forward(
        @PathVariable UUID id,
        @Valid @RequestBody ForwardRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(AlertResponse.from(
            forwarding.forward(AlertId.of(id), currentUser, request.authorityCode(), request.reason())));
    }

    @GetMapping("/forwards")
    @Operation(summary = "Where the report has been forwarded, oldest first")
    public ResponseEntity<List<ForwardResponse>> forwards(@PathVariable UUID id) {
        return ResponseEntity.ok(forwarding.forwards(AlertId.of(id)).stream()
            .map(ForwardResponse::from)
            .toList());
    }

    @Schema(description = "Where to forward a report, and why")
    public record ForwardRequest(
        @Schema(description = "The authority it belongs to, as listed by GET /api/v1/authorities", example = "CGM")
        @NotBlank(message = "Authority is required")
        String authorityCode,
        @Schema(description = "Why it is that authority's, 1 to 1000 characters", example = "Шахтата е на водопровода.")
        @NotBlank(message = "A reason is required")
        @Size(max = ReportForward.MAX_REASON_LENGTH, message = "A reason is at most 1000 characters")
        String reason
    ) {
    }

    @Schema(description = "One forward of a report")
    public record ForwardResponse(
        @Schema(description = "1 for the first forward, then 2, 3...") int sequence,
        @Schema(description = "The authority that had it", example = "LOZENETS") String fromAuthority,
        @Schema(description = "The authority it went to", example = "CGM") String toAuthority,
        @Schema(description = "Made by a moderator rather than by the authority that had it") boolean byModerator,
        @Schema(description = "Why") String reason,
        Instant forwardedAt
    ) {
        static ForwardResponse from(ReportForward f) {
            return new ForwardResponse(f.sequence(), f.fromAuthority(), f.toAuthority(), f.byModerator(),
                f.reason(), f.forwardedAt());
        }
    }
}
