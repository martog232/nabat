package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.example.nabat.incident.application.port.in.FindSimilarReportsUseCase;
import org.example.nabat.incident.application.port.in.FindSimilarReportsUseCase.Near;
import org.example.nabat.incident.application.port.in.FindSimilarReportsUseCase.SimilarQuery;
import org.example.nabat.incident.application.port.in.FindSimilarReportsUseCase.SimilarReports;
import org.example.nabat.incident.domain.OpenCallSofiaReport;
import org.example.nabat.incident.domain.Alert;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * "Is this already reported?" — asked by the report form once it has a place and a
 * category, so a second report of the same problem can become a confirmation of the first.
 * Advice only: nothing refuses a report because something similar exists.
 */
@RestController
@RequestMapping("/api/v1/alerts/similar")
@Validated
public class SimilarReportsController {

    private final FindSimilarReportsUseCase findSimilar;

    public SimilarReportsController(FindSimilarReportsUseCase findSimilar) {
        this.findSimilar = findSimilar;
    }

    @GetMapping
    @Operation(summary = "Open reports near a point in the same category group, in nabat and in Call Sofia")
    public ResponseEntity<SimilarReportsResponse> similar(
        @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @RequestParam @NotNull Integer category
    ) {
        SimilarReports found = findSimilar.findSimilar(new SimilarQuery(latitude, longitude, category));
        return ResponseEntity.ok(SimilarReportsResponse.from(found));
    }

    @Schema(description = "What might be the same problem, already reported")
    public record SimilarReportsResponse(
        @Schema(description = "How far around the point was searched", example = "100") int radiusMeters,
        @Schema(description = "Open nabat reports, nearest first") List<SimilarReport> reports,
        @Schema(description = "Reports open in Call Sofia, nearest first; empty when Call Sofia's map is not being read") List<SimilarCallSofiaReport> callSofia
    ) {
        static SimilarReportsResponse from(SimilarReports found) {
            return new SimilarReportsResponse(
                found.radiusMeters(),
                found.reports().stream().map(SimilarReport::from).toList(),
                found.callSofia().stream().map(SimilarCallSofiaReport::from).toList());
        }
    }

    @Schema(description = "An open nabat report near the point")
    public record SimilarReport(
        @Schema(description = "The report") AlertResponse report,
        @Schema(description = "Metres from the point asked about", example = "35") int meters
    ) {
        static SimilarReport from(Near<Alert> near) {
            return new SimilarReport(AlertResponse.from(near.report()), near.meters());
        }
    }

    @Schema(description = "A report open in Call Sofia near the point")
    public record SimilarCallSofiaReport(
        @Schema(description = "Call Sofia's id", example = "788076") long id,
        @Schema(description = "The municipality's registry number", example = "СОА26-КЦ01-103731/13.09.2026") String registryNumber,
        @Schema(description = "Title as filed") String title,
        @Schema(description = "Category code — the same codes as nabat's", example = "1007") int categoryCode,
        @Schema(description = "When it was filed") Instant submittedAt,
        @Schema(description = "Metres from the point asked about", example = "60") int meters,
        @Schema(description = "Its page on call.sofia.bg", example = "https://call.sofia.bg/signals/788076") String url
    ) {
        static SimilarCallSofiaReport from(Near<OpenCallSofiaReport> near) {
            OpenCallSofiaReport r = near.report();
            return new SimilarCallSofiaReport(r.id(), r.registryNumber(), r.title(), r.categoryCode(),
                r.submittedAt(), near.meters(), "https://call.sofia.bg/signals/" + r.id());
        }
    }
}
