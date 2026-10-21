package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.ReportStep;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Builds a report's history steps from who took them; the domain does not know accounts. */
final class Steps {

    private Steps() {
    }

    /** {@code actor} took this step on {@code report}, which was with {@code authorityCode} at the time. */
    static ReportStep by(User actor, Alert report, String authorityCode, ReportStep.Kind kind) {
        return new ReportStep(UUID.randomUUID(), report.id(), kind, actor.id().value(), actor.displayName(),
            actor.actsFor(authorityCode), authorityCode, now());
    }

    /** nabat itself took this step: delivered the report. */
    static ReportStep bySystem(Alert report, ReportStep.Kind kind) {
        return new ReportStep(UUID.randomUUID(), report.id(), kind, null, null, false, report.authorityCode(), now());
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
