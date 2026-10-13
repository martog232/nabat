package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.CheckFixUseCase;
import org.example.nabat.incident.application.port.in.CheckFixUseCase.FixCheckView;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.FixVerdict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Is it really fixed?" on a resolved report. One answer per person: {@code PUT} sets or
 * changes it, {@code DELETE} takes it back. 409 on a report that is not resolved, 403 for
 * an official of the report's own district.
 */
@RestController
@RequestMapping("/api/v1/alerts/{id}/fix-check")
public class FixCheckController {

    private final CheckFixUseCase checkFix;

    public FixCheckController(CheckFixUseCase checkFix) {
        this.checkFix = checkFix;
    }

    @GetMapping
    @Operation(summary = "What people say about whether a resolved report is really fixed, and what you said")
    public ResponseEntity<FixCheckResponse> view(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(FixCheckResponse.from(checkFix.view(AlertId.of(id), currentUser)));
    }

    @PutMapping
    @Operation(summary = "Say whether a resolved report is really fixed")
    public ResponseEntity<FixCheckResponse> check(
        @PathVariable UUID id,
        @Valid @RequestBody FixCheckRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(FixCheckResponse.from(checkFix.check(AlertId.of(id), currentUser, request.verdict())));
    }

    @DeleteMapping
    @Operation(summary = "Take back your answer")
    public ResponseEntity<FixCheckResponse> withdraw(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(FixCheckResponse.from(checkFix.withdraw(AlertId.of(id), currentUser)));
    }

    @Schema(description = "An answer to: is it really fixed?")
    public record FixCheckRequest(
        @Schema(description = "FIXED, PARTLY or NOT_FIXED", example = "NOT_FIXED")
        @NotNull(message = "Verdict is required")
        FixVerdict verdict
    ) {
    }

    @Schema(description = "The answers on a report, with the caller's own")
    public record FixCheckResponse(
        @Schema(description = "The tally and what it adds up to") FixCheckSummary summary,
        @Schema(description = "The caller's own answer; null if they have not given one") FixVerdict mine
    ) {
        static FixCheckResponse from(FixCheckView view) {
            return new FixCheckResponse(FixCheckSummary.from(view.tally()), view.mine());
        }
    }
}
