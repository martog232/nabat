package org.example.nabat.callsofia.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.example.nabat.callsofia.application.port.in.CompareDistrictsUseCase;
import org.example.nabat.callsofia.application.port.in.CompareDistrictsUseCase.ComparisonQuery;
import org.example.nabat.callsofia.application.port.in.CompareDistrictsUseCase.DistrictComparison;
import org.example.nabat.callsofia.domain.DistrictOutcomes;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * How each district handled Call Sofia reports, side by side. Public, like the ranked list:
 * the figures come from published data and are about the administrations, not about anyone
 * who reported. Cacheable for an hour — they change only with an import.
 */
@RestController
@RequestMapping("/api/v1/call-sofia/district-comparison")
public class DistrictComparisonController {

    private final CompareDistrictsUseCase compare;

    public DistrictComparisonController(CompareDistrictsUseCase compare) {
        this.compare = compare;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Sofia's districts compared on how they answered Call Sofia reports, from the archive")
    public ResponseEntity<DistrictComparisonResponse> compare(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        DistrictComparison result = compare.compare(new ComparisonQuery(from, to));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(DistrictComparisonResponse.from(result));
    }

    @Schema(description = "Districts compared over a period of Call Sofia's archive")
    public record DistrictComparisonResponse(
        @Schema(description = "First filing date in the archive; null if no archive is imported", example = "2021-12-14") LocalDate archiveFrom,
        @Schema(description = "Last filing date in the archive — everything is as of this day", example = "2026-08-01") LocalDate archiveTo,
        @Schema(description = "Start of the period, inclusive", example = "2025-08-02") LocalDate from,
        @Schema(description = "End of the period, exclusive", example = "2026-08-02") LocalDate to,
        @Schema(description = "One entry per district with reports in the period") List<DistrictOutcomesResponse> districts
    ) {
        static DistrictComparisonResponse from(DistrictComparison c) {
            return new DistrictComparisonResponse(c.archiveFrom(), c.archiveTo(), c.from(), c.to(),
                c.districts().stream().map(DistrictOutcomesResponse::from).toList());
        }
    }

    @Schema(description = "How one district handled its reports. \"Answered\" is the first final word to the citizen — a reply, a closure or a rejection — not a fix.")
    public record DistrictOutcomesResponse(
        @Schema(description = "District code, as in GET /api/v1/districts", example = "LOZENETS") String districtCode,
        @Schema(description = "Reports filed in the period") int signals,
        @Schema(description = "Of those, answered at all") int answered,
        @Schema(description = "Median days to the first answer, over answered reports; null if none", example = "12.4") Double medianDaysToAnswer,
        @Schema(description = "Nine in ten answered reports were answered within this many days", example = "41.0") Double p90DaysToAnswer,
        @Schema(description = "Reports filed at least 30 days before the archive ends — the ones the 30-day deadline can be judged on") int dueIn30Days,
        @Schema(description = "Of those, answered within 30 days") int answeredIn30Days,
        @Schema(description = "Ended as „Приключен“") int closed,
        @Schema(description = "Ended as „Получил уведомление“ — a reply, and nothing after it") int answeredOnly,
        @Schema(description = "Ended as „Отхвърлен“") int rejected,
        @Schema(description = "No final answer when the archive was taken") int stillOpen
    ) {
        static DistrictOutcomesResponse from(DistrictOutcomes d) {
            return new DistrictOutcomesResponse(d.districtCode(), d.signals(), d.answered(),
                round(d.medianDaysToAnswer()), round(d.p90DaysToAnswer()), d.dueIn30Days(), d.answeredIn30Days(),
                d.closed(), d.answeredOnly(), d.rejected(), d.stillOpen());
        }

        private static Double round(Double days) {
            return days == null ? null : Math.round(days * 10) / 10.0;
        }
    }
}
