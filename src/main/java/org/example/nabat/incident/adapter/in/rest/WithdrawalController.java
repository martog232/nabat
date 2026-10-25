package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.WithdrawReportUseCase;
import org.example.nabat.incident.domain.AlertId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The reporter takes their report back. 403 for anyone else, 400 without a reason, 409 on a
 * closed report.
 */
@RestController
@RequestMapping("/api/v1/alerts/{id}/withdraw")
public class WithdrawalController {

    private final WithdrawReportUseCase withdrawing;

    public WithdrawalController(WithdrawReportUseCase withdrawing) {
        this.withdrawing = withdrawing;
    }

    @PostMapping
    @Operation(summary = "The reporter withdraws their open report, saying why")
    public ResponseEntity<AlertResponse> withdraw(
        @PathVariable UUID id,
        @Valid @RequestBody WithdrawRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(AlertResponse.from(withdrawing.withdraw(AlertId.of(id), currentUser, request.reason())));
    }

    @Schema(description = "Why the report is withdrawn")
    public record WithdrawRequest(
        @Schema(description = "1 to 1000 characters", example = "Сгреших мястото — подадох го отново на правилното.")
        @NotBlank(message = "A reason is required")
        @Size(max = WithdrawReportUseCase.MAX_REASON_LENGTH, message = "A reason is at most 1000 characters")
        String reason
    ) {
    }
}
