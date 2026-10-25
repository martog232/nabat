package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.ReportStep;

import java.util.List;
import java.util.UUID;

/** Each step of a report's life, and who took it (V36). */
public interface ReportHistory {

    /** In the transaction that takes the step: a step not recorded was not taken. */
    void record(ReportStep step);

    /** Oldest first. */
    List<ReportStep> of(UUID alertId);
}
