package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.in.ShowReportHistoryUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.ReportHistory;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.ReportStep;
import org.example.nabat.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ReportHistoryService implements ShowReportHistoryUseCase {

    private final AlertRepository alerts;
    private final ReportHistory history;

    public ReportHistoryService(AlertRepository alerts, ReportHistory history) {
        this.alerts = alerts;
        this.history = history;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportStep> history(AlertId id) {
        if (alerts.findById(id).isEmpty()) {
            throw new AlertNotFoundException(id);
        }
        return history.of(id.value());
    }
}
