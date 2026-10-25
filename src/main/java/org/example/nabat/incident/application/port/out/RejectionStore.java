package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.ReportRejection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The rejections of reports, and what came of their disputes (V31). */
public interface RejectionStore {

    void insert(ReportRejection rejection);

    /**
     * The report's most recent rejection, held for the rest of the transaction: a dispute and a
     * decision on the same one must not both read it undecided.
     */
    Optional<ReportRejection> holdLatest(UUID alertId);

    /** Writes the dispute and decision columns of a rejection already stored. */
    void update(ReportRejection rejection);

    /** Every rejection of the report, oldest first. */
    List<ReportRejection> rejectionsOf(UUID alertId);

    /** Reports whose latest rejection is disputed and undecided, the oldest dispute first. */
    List<UUID> openDisputes(int limit);
}
