package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.example.nabat.incident.application.port.in.ShowReportHistoryUseCase;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.ReportStep;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A report's history: each step, and who took it. Reports older than V36 have none. */
@RestController
@RequestMapping("/api/v1/alerts/{id}/history")
public class HistoryController {

    private final ShowReportHistoryUseCase history;

    public HistoryController(ShowReportHistoryUseCase history) {
        this.history = history;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Who did what to the report, and when, oldest first")
    public ResponseEntity<List<StepResponse>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(history.history(AlertId.of(id)).stream().map(StepResponse::from).toList());
    }

    @Schema(description = "One step in a report's life")
    public record StepResponse(
        ReportStep.Kind step,
        @Schema(description = "Who took it, by name when they did; null for what nabat does itself (delivering it)") String actorName,
        @Schema(description = "They spoke for the authority the report was with") boolean byAuthority,
        @Schema(description = "The authority the report was with", example = "LOZENETS") String authorityCode,
        Instant happenedAt
    ) {
        static StepResponse from(ReportStep s) {
            return new StepResponse(s.kind(), s.actorName(), s.byAuthority(), s.authorityCode(), s.happenedAt());
        }
    }
}
