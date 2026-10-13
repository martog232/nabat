package org.example.nabat.callsofia.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.callsofia.application.port.out.OpenSignalStore.Departures;
import org.example.nabat.callsofia.domain.CallSofiaOpenSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatus;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Successive readings of the map against the real V22 schema: which reports are on it,
 * which left, and for which of the two reasons.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcOpenSignalStore.class)
class JdbcOpenSignalStoreIntegrationTest extends PostgresTestSupport {

    private static final Instant FIRST = Instant.parse("2026-10-13T10:00:00Z");
    private static final Instant SECOND = FIRST.plus(Duration.ofHours(1));
    private static final Instant AGED_BEFORE = SECOND.minus(Duration.ofDays(30));

    @Autowired
    private JdbcOpenSignalStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM call_sofia_open_signals");
    }

    /**
     * Three on the map, then only one. The one filed 30 days ago aged out; the one filed
     * yesterday left processing — that is the only reason it can be gone.
     */
    @Test
    void tellsAReportThatAgedOutFromOneThatLeftProcessing() {
        CallSofiaOpenSignal old = signal(1, SECOND.minus(Duration.ofDays(30)).minusSeconds(60));
        CallSofiaOpenSignal recent = signal(2, SECOND.minus(Duration.ofDays(1)));
        CallSofiaOpenSignal stays = signal(3, SECOND.minus(Duration.ofDays(2)));
        store.recordReading(List.of(old, recent, stays), Map.of(), FIRST, FIRST.minus(Duration.ofDays(30)));

        Departures departures = store.recordReading(List.of(stays), Map.of(), SECOND, AGED_BEFORE);

        assertThat(departures).isEqualTo(new Departures(1, 1));
        assertThat(reason(1)).isEqualTo("AGED_OUT");
        assertThat(reason(2)).isEqualTo("NO_LONGER_PROCESSING");
        assertThat(reason(3)).isNull();
        assertThat(jdbc.queryForObject("SELECT left_map_at FROM call_sofia_open_signals WHERE id = 2", OffsetDateTime.class).toInstant())
            .isEqualTo(SECOND);
    }

    @Test
    void aReportThatComesBackIsOnTheMapAgain() {
        CallSofiaOpenSignal signal = signal(1, FIRST.minus(Duration.ofDays(1)));
        CallSofiaOpenSignal other = signal(2, FIRST.minus(Duration.ofDays(1)));
        store.recordReading(List.of(signal, other), Map.of(), FIRST, FIRST.minus(Duration.ofDays(30)));
        store.recordReading(List.of(other), Map.of(), SECOND, AGED_BEFORE);

        store.recordReading(List.of(signal, other), Map.of(), SECOND.plusSeconds(3600), AGED_BEFORE);

        assertThat(reason(1)).isNull();
        assertThat(jdbc.queryForObject("SELECT left_map_at IS NULL FROM call_sofia_open_signals WHERE id = 1", Boolean.class))
            .isTrue();
    }

    /** The district is computed once, when the report is first seen, and not looked up again. */
    @Test
    void keepsTheDistrictAReportWasFirstPlacedIn() {
        CallSofiaOpenSignal signal = signal(1, FIRST.minus(Duration.ofDays(1)));
        store.recordReading(List.of(signal), Map.of(1L, "MLADOST"), FIRST, FIRST.minus(Duration.ofDays(30)));

        store.recordReading(List.of(signal), Map.of(), SECOND, AGED_BEFORE);

        assertThat(jdbc.queryForObject("SELECT district_code FROM call_sofia_open_signals WHERE id = 1", String.class))
            .isEqualTo("MLADOST");
        assertThat(jdbc.queryForObject("SELECT first_seen_at FROM call_sofia_open_signals WHERE id = 1", OffsetDateTime.class).toInstant())
            .isEqualTo(FIRST);
    }

    @Test
    void knowsWhichReportsItHasSeen() {
        store.recordReading(List.of(signal(1, FIRST), signal(2, FIRST)), Map.of(), FIRST, FIRST.minus(Duration.ofDays(30)));

        assertThat(store.knownIds(List.of(1L, 2L, 3L))).containsExactlyInAnyOrder(1L, 2L);
        assertThat(store.knownIds(List.of())).isEmpty();
    }

    /** Longitude first into PostGIS. */
    @Test
    void storesThePointWhereItIs() {
        store.recordReading(List.of(signal(1, FIRST)), Map.of(), FIRST, FIRST.minus(Duration.ofDays(30)));

        assertThat(jdbc.queryForObject(
            "SELECT ST_X(location::geometry) FROM call_sofia_open_signals WHERE id = 1", Double.class)).isEqualTo(23.379);
    }

    private String reason(long id) {
        return jdbc.queryForObject("SELECT left_map_reason FROM call_sofia_open_signals WHERE id = ?", String.class, id);
    }

    private static CallSofiaOpenSignal signal(long id, Instant submittedAt) {
        return new CallSofiaOpenSignal(id, "СОА26-КЦ01-" + id, 1007, "Улично платно", CallSofiaStatus.PROCESSING,
            "Дупка", submittedAt, Location.of(42.6505, 23.379));
    }
}
