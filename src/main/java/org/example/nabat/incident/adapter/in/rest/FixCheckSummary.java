package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.nabat.incident.domain.FixCheckTally;
import org.example.nabat.incident.domain.FixOutcome;
import org.example.nabat.incident.domain.FixVerdict;

@Schema(description = "What people say about whether a resolved report is really fixed")
public record FixCheckSummary(
    @Schema(description = "Answers saying it is fixed") int fixed,
    @Schema(description = "Answers saying it is partly fixed") int partly,
    @Schema(description = "Answers saying it is not fixed") int notFixed,
    @Schema(description = "The reporter's own answer; null if they have not given one") FixVerdict reporterVerdict,
    @Schema(description = "What the answers add up to. DISPUTED if the reporter says not fixed, or more say not than say fixed", example = "CONFIRMED") FixOutcome outcome
) {
    public static FixCheckSummary from(FixCheckTally tally) {
        return new FixCheckSummary(tally.fixed(), tally.partly(), tally.notFixed(), tally.reporterVerdict(), tally.outcome());
    }
}
