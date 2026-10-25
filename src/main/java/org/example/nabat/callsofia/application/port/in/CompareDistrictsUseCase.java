package org.example.nabat.callsofia.application.port.in;

import org.example.nabat.callsofia.domain.DistrictOutcomes;

import java.time.LocalDate;
import java.util.List;

/**
 * Sofia's districts side by side, on how they handled Call Sofia reports — from the
 * archive, so as of the day it was taken.
 */
public interface CompareDistrictsUseCase {

    /**
     * @throws IllegalArgumentException if {@code from} is not before {@code to}
     */
    DistrictComparison compare(ComparisonQuery query);

    /**
     * Reports filed from {@code from} up to, not including, {@code to}, in Sofia's calendar.
     * Either may be null: the default is the archive's last twelve months.
     */
    record ComparisonQuery(LocalDate from, LocalDate to) {
    }

    /**
     * @param archiveFrom the first filing date in the archive; null if none is imported
     * @param archiveTo   the last
     * @param districts   in no particular order; districts with no reports in the period are absent
     */
    record DistrictComparison(LocalDate archiveFrom, LocalDate archiveTo, LocalDate from, LocalDate to,
                              List<DistrictOutcomes> districts) {
    }
}
