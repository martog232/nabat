package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.ReportStep;

import java.util.List;

/** Who did what to a report, and when — oldest first. */
public interface ShowReportHistoryUseCase {

    /** @throws org.example.nabat.incident.domain.AlertNotFoundException for an unknown report */
    List<ReportStep> history(AlertId id);
}
