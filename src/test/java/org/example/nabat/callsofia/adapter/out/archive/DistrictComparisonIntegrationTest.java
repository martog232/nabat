package org.example.nabat.callsofia.adapter.out.archive;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.callsofia.adapter.out.persistence.JdbcCallSofiaArchiveStore;
import org.example.nabat.callsofia.adapter.out.persistence.JdbcSignalOutcomeStatistics;
import org.example.nabat.callsofia.application.CallSofiaArchiveImportService;
import org.example.nabat.callsofia.application.DistrictComparisonService;
import org.example.nabat.callsofia.application.port.in.CompareDistrictsUseCase;
import org.example.nabat.callsofia.application.port.in.CompareDistrictsUseCase.ComparisonQuery;
import org.example.nabat.callsofia.application.port.in.CompareDistrictsUseCase.DistrictComparison;
import org.example.nabat.callsofia.application.port.in.ImportCallSofiaArchiveUseCase;
import org.example.nabat.callsofia.domain.DistrictOutcomes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The fixture archive imported for real, its outcomes refreshed (V26), and compared. Every
 * expected figure below is worked out by hand from the fixture files.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CallSofiaArchiveImportService.class, YurukovCsvArchive.class, JdbcCallSofiaArchiveStore.class,
    JdbcSignalOutcomeStatistics.class, DistrictComparisonService.class})
class DistrictComparisonIntegrationTest extends PostgresTestSupport {

    private static final ComparisonQuery FIXTURE_YEARS = new ComparisonQuery(LocalDate.of(2021, 1, 1), LocalDate.of(2023, 1, 1));

    @Autowired private ImportCallSofiaArchiveUseCase importArchive;
    @Autowired private CompareDistrictsUseCase compare;

    @BeforeEach
    void importFixtures() {
        importArchive.importArchive();
    }

    /**
     * Vitosha: filed 2021-12-14 14:13 Sofia, first answered 2022-01-10 09:00 Sofia — 26.78
     * days, then closed. Pancharevo: rejected the minute it was filed. Krasno selo: never
     * answered. The report with no district is left out.
     */
    @Test
    void comparesDistrictsOnHowTheyAnswered() {
        Map<String, DistrictOutcomes> byCode = byCode(compare.compare(FIXTURE_YEARS));

        assertThat(byCode).containsOnlyKeys("VITOSHA", "KRASNO_SELO", "PANCHAREVO");

        DistrictOutcomes vitosha = byCode.get("VITOSHA");
        assertThat(vitosha.signals()).isEqualTo(1);
        assertThat(vitosha.answered()).isEqualTo(1);
        assertThat(vitosha.medianDaysToAnswer()).isCloseTo(26.78, within(0.01));
        assertThat(vitosha.answeredIn30Days()).isEqualTo(1);
        assertThat(vitosha.closed()).isEqualTo(1);

        DistrictOutcomes krasnoSelo = byCode.get("KRASNO_SELO");
        assertThat(krasnoSelo.answered()).isZero();
        assertThat(krasnoSelo.medianDaysToAnswer()).isNull();
        assertThat(krasnoSelo.dueIn30Days()).isEqualTo(1);
        assertThat(krasnoSelo.answeredIn30Days()).isZero();
        assertThat(krasnoSelo.stillOpen()).isEqualTo(1);

        DistrictOutcomes pancharevo = byCode.get("PANCHAREVO");
        assertThat(pancharevo.medianDaysToAnswer()).isZero();
        assertThat(pancharevo.rejected()).isEqualTo(1);
    }

    /**
     * The archive's last twelve months, not the calendar's. The fixture's last report is
     * from 2026-07-15 and has no district, so the default period has nothing to compare.
     */
    @Test
    void defaultsToTheArchivesLastYear() {
        DistrictComparison result = compare.compare(new ComparisonQuery(null, null));

        assertThat(result.archiveFrom()).isEqualTo(LocalDate.of(2021, 12, 14));
        assertThat(result.archiveTo()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(result.to()).isEqualTo(LocalDate.of(2026, 7, 16));
        assertThat(result.from()).isEqualTo(LocalDate.of(2025, 7, 16));
        assertThat(result.districts()).isEmpty();
    }

    @Test
    void refusesAPeriodThatEndsBeforeItStarts() {
        assertThatThrownBy(() -> compare.compare(new ComparisonQuery(LocalDate.of(2023, 1, 1), LocalDate.of(2022, 1, 1))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, DistrictOutcomes> byCode(DistrictComparison comparison) {
        return comparison.districts().stream().collect(Collectors.toMap(DistrictOutcomes::districtCode, Function.identity()));
    }
}
