package org.example.nabat.callsofia.application;

import org.example.nabat.callsofia.application.port.in.CompareDistrictsUseCase;
import org.example.nabat.callsofia.application.port.out.SignalOutcomeStatistics;
import org.example.nabat.callsofia.domain.ArchiveSpan;
import org.example.nabat.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Picks the period and leaves the arithmetic to the database.
 *
 * <p>Dates are Sofia's calendar days: a report filed at 00:30 on 1 January in Sofia belongs
 * to the new year, though it is still 31 December in UTC.
 */
@UseCase
public class DistrictComparisonService implements CompareDistrictsUseCase {

    static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");
    static final Duration DEADLINE = Duration.ofDays(30);

    private final SignalOutcomeStatistics statistics;

    public DistrictComparisonService(SignalOutcomeStatistics statistics) {
        this.statistics = statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public DistrictComparison compare(ComparisonQuery query) {
        Optional<ArchiveSpan> span = statistics.span();
        if (span.isEmpty()) {
            return new DistrictComparison(null, null, query.from(), query.to(), List.of());
        }
        LocalDate archiveFrom = span.get().firstSubmitted().atZone(SOFIA).toLocalDate();
        LocalDate archiveTo = span.get().lastSubmitted().atZone(SOFIA).toLocalDate();

        // The archive's last twelve months by default — not the calendar's: the archive stopped
        // on 2026-08-01, and a year ending today would be mostly empty.
        LocalDate to = query.to() != null ? query.to() : archiveTo.plusDays(1);
        LocalDate from = query.from() != null ? query.from() : to.minusMonths(12);
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("The period must start before it ends");
        }

        return new DistrictComparison(archiveFrom, archiveTo, from, to, statistics.byDistrict(
            from.atStartOfDay(SOFIA).toInstant(),
            to.atStartOfDay(SOFIA).toInstant(),
            span.get().lastSubmitted().minus(DEADLINE)));
    }
}
