package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.nabat.incident.domain.ReportRanking;

import java.util.List;

@Schema(description = "One page of the public list of reports")
public record RankedReportsResponse(
    @Schema(description = "The reports on this page, in order") List<AlertResponse> alerts,
    @Schema(description = "The order that was applied", example = "HOT") ReportRanking ranking,
    @Schema(description = "Zero-based page number", example = "0") int page,
    @Schema(description = "Page size that was applied", example = "50") int size,
    @Schema(description = "How many reports match in all, across every page", example = "137") long total
) {
}
