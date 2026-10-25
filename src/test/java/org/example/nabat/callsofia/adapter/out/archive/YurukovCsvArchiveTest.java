package org.example.nabat.callsofia.adapter.out.archive;

import org.example.nabat.callsofia.domain.CallSofiaCategory;
import org.example.nabat.callsofia.domain.CallSofiaSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatus;
import org.example.nabat.callsofia.domain.CallSofiaStatusChange;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The archive's format, against lines in its own layout. Every case here is one the real
 * dump of 2026-08-01 contains — titles with their own commas and quotes, reports with no
 * point, district 0, steps recorded in the same second — apart from the malformed lines,
 * which it does not, and which the import must survive anyway.
 */
class YurukovCsvArchiveTest {

    private final YurukovCsvArchive archive =
        new YurukovCsvArchive(new DefaultResourceLoader(), "classpath:callsofia");

    @Test
    void readsTheCategoryIndexWithoutItsQuotes() {
        assertThat(archive.categories())
            .contains(new CallSofiaCategory(28, "Улично осветление"))
            .contains(new CallSofiaCategory(3056,
                "Пътна инфраструктура-Дупки, пропадане или издигане на настилката на пътното платно или тротоара"));
    }

    @Test
    void keepsATitleWholeWithItsOwnCommasAndQuotes() {
        assertThat(signal(296879).title()).isEqualTo("Дупка на ул. \"Гинци\", пред бл. 28, вече месец");
        assertThat(signal(296872).title()).isEqualTo("Неизвозени контейнери по график, засметяване");
    }

    /** The file has longitude first. A swapped point lands in the Gulf of Aden. */
    @Test
    void readsThePointLongitudeFirst() {
        assertThat(signal(295879).location()).isEqualTo(Location.of(42.636833, 23.294585));
    }

    @Test
    void aReportWithNoPointHasNoLocation() {
        assertThat(signal(296879).location()).isNull();
    }

    @Test
    void translatesTheDistrictNumberAndLeavesZeroEmpty() {
        assertThat(signal(295879).districtCode()).isEqualTo("VITOSHA");
        assertThat(signal(296872).districtCode()).isEqualTo("KRASNO_SELO");
        assertThat(signal(296900).districtCode()).isEqualTo("PANCHAREVO");
        assertThat(signal(296879).districtCode()).isNull();
    }

    /** Sofia is UTC+2 in winter and UTC+3 in summer; the archive writes neither. */
    @Test
    void readsTimesAsSofiaWallClock() {
        assertThat(signal(295879).submittedAt()).isEqualTo(Instant.parse("2021-12-14T12:13:08Z"));
        assertThat(signal(296879).submittedAt()).isEqualTo(Instant.parse("2026-07-15T06:30:00Z"));
    }

    @Test
    void skipsALineThatIsNotAReportAndReadsOnPastIt() {
        assertThat(signals()).extracting(CallSofiaSignal::id)
            .containsExactly(295879L, 296872L, 296879L, 296900L);
    }

    /**
     * Two steps in the same second keep the archive's order. A step whose code the archive
     * never defined is skipped, and the name of whoever made a step is not carried.
     */
    @Test
    void numbersEachReportsStepsInTheArchivesOrder() {
        List<CallSofiaStatusChange> changes;
        try (Stream<CallSofiaStatusChange> stream = archive.statusChanges()) {
            changes = stream.toList();
        }

        assertThat(changes)
            .extracting(CallSofiaStatusChange::signalId, CallSofiaStatusChange::position, CallSofiaStatusChange::status)
            .containsExactly(
                tuple(100L, 0, CallSofiaStatus.AWAITING_PROCESSING),
                tuple(295879L, 0, CallSofiaStatus.AWAITING_PROCESSING),
                tuple(295879L, 1, CallSofiaStatus.PROCESSING),
                tuple(295879L, 2, CallSofiaStatus.ANSWERED),
                tuple(295879L, 3, CallSofiaStatus.CLOSED),
                tuple(296872L, 0, CallSofiaStatus.AWAITING_PROCESSING),
                tuple(296879L, 0, CallSofiaStatus.AWAITING_PROCESSING),
                tuple(296900L, 0, CallSofiaStatus.REJECTED));
        assertThat(changes.get(3).changedAt()).isEqualTo(changes.get(4).changedAt());
    }

    @Test
    void refusesAnArchiveWhoseLayoutChanged() {
        YurukovCsvArchive changed = new YurukovCsvArchive(new DefaultResourceLoader(), "classpath:callsofia-changed/");

        assertThatThrownBy(changed::signals)
            .isInstanceOf(ArchiveFormatChangedException.class)
            .hasMessageContaining("address");
    }

    @Test
    void everyDistrictNumberIsADifferentDistrict() {
        Map<Integer, String> regions = YurukovRegions.all();

        assertThat(regions.keySet()).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 24).boxed().toList());
        assertThat(new HashSet<>(regions.values())).hasSize(24);
        assertThat(YurukovRegions.districtCode(0)).isNull();
    }

    private List<CallSofiaSignal> signals() {
        try (Stream<CallSofiaSignal> stream = archive.signals()) {
            return stream.toList();
        }
    }

    private CallSofiaSignal signal(long id) {
        return signals().stream().filter(s -> s.id() == id).findFirst().orElseThrow();
    }
}
