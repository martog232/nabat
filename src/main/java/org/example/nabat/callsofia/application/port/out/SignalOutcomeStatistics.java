package org.example.nabat.callsofia.application.port.out;

import org.example.nabat.callsofia.domain.ArchiveSpan;
import org.example.nabat.callsofia.domain.DistrictOutcomes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Aggregates over the archive's per-report outcomes (V26). */
public interface SignalOutcomeStatistics {

    /** Empty when no archive has been imported. */
    Optional<ArchiveSpan> span();

    /**
     * Per district, for reports filed in {@code [from, to)}.
     *
     * @param dueBy reports filed after this cannot be judged on the 30-day deadline: the
     *              archive ends before their 30 days do
     */
    List<DistrictOutcomes> byDistrict(Instant from, Instant to, Instant dueBy);
}
