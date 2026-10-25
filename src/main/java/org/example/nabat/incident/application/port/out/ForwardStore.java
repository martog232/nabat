package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.ReportForward;

import java.util.List;
import java.util.UUID;

/** The forwards of reports, oldest first. */
public interface ForwardStore {

    void insert(ReportForward forward);

    List<ReportForward> forwardsOf(UUID alertId);
}
