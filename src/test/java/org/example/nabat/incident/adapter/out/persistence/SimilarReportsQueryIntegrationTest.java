package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.callsofia.adapter.out.incident.CallSofiaReportsAdapter;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.OpenCallSofiaReport;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both halves of the duplicate check against PostGIS: nabat's open reports, and Call
 * Sofia's as last seen on its map. Distances are real, so the radius is too.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CallSofiaReportsAdapter.class)
class SimilarReportsQueryIntegrationTest extends PostgresTestSupport {

    /** Lozenets. 0.00045° of latitude is about 50 m; 0.0018° about 200 m. */
    private static final double LAT = 42.6700;
    private static final double LON = 23.3200;
    private static final Location HERE = Location.of(LAT, LON);

    @Autowired private AlertJpaRepository alertJpa;
    @Autowired private UserJpaRepository users;
    @Autowired private CallSofiaReportsAdapter callSofia;
    @Autowired private JdbcTemplate jdbc;

    private UUID reporterId;

    @BeforeEach
    void setUp() {
        alertJpa.deleteAllInBatch();
        jdbc.update("DELETE FROM call_sofia_open_signals");
        User reporter = User.create("similar-" + UUID.randomUUID() + "@example.com", "hash", "Подател");
        users.saveAndFlush(UserJpaEntity.from(reporter));
        reporterId = reporter.id().value();
    }

    @Test
    void findsOpenReportsInTheGroupWithinTheRadiusNearestFirst() {
        save("Fifty metres, pavement", 1008, LAT + 0.00045, AlertStatus.SENT);
        save("Here, roadway", 1007, LAT, AlertStatus.SUBMITTED);
        save("Two hundred metres", 1007, LAT + 0.0018, AlertStatus.SUBMITTED);
        save("Here, but a street light", 1028, LAT, AlertStatus.SUBMITTED);
        save("Here, but resolved", 1007, LAT, AlertStatus.RESOLVED);

        List<String> titles = alertJpa.findOpenNear(LAT, LON, 100, List.of(1007, 1008), 5).stream()
            .map(AlertJpaEntity::getTitle).toList();

        assertThat(titles).containsExactly("Here, roadway", "Fifty metres, pavement");
    }

    @Test
    void findsCallSofiaReportsStillOnItsMapOnly() {
        callSofiaSignal(788076, 1007, LAT, null);
        callSofiaSignal(788077, 1008, LAT + 0.00045, null);
        callSofiaSignal(788078, 1007, LAT, "NO_LONGER_PROCESSING");
        callSofiaSignal(788079, 1028, LAT, null);
        callSofiaSignal(788080, 1007, LAT + 0.0018, null);

        List<OpenCallSofiaReport> found = callSofia.findOpenNear(HERE, 100, List.of(1007, 1008), 5);

        assertThat(found).extracting(OpenCallSofiaReport::id).containsExactly(788076L, 788077L);
        assertThat(found.getFirst().location().latitude()).isEqualTo(LAT);
        assertThat(found.getFirst().location().longitude()).isEqualTo(LON);
    }

    private void save(String title, int category, double lat, AlertStatus status) {
        alertJpa.saveAndFlush(AlertJpaEntity.from(new Alert(AlertId.generate(), title, "d", category,
            Location.of(lat, LON), Instant.now(), status, reporterId, 0, 0, 0, 0,
            status == AlertStatus.RESOLVED ? Instant.now() : null, null, "LOZENETS",
            status == AlertStatus.RESOLVED ? org.example.nabat.incident.domain.ClosedBy.AUTHORITY : null)));
    }

    private void callSofiaSignal(long id, int category, double lat, String leftReason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
            INSERT INTO call_sofia_open_signals (id, registry_number, category_id, category_name, status, title,
                submitted_at, location, district_code, first_seen_at, last_seen_at, left_map_at, left_map_reason)
            VALUES (?, ?, ?, 'x', 'PROCESSING', ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, 'LOZENETS', ?, ?, ?, ?)
            """, id, "СОА26-КЦ01-" + id, category, "Сигнал " + id, now, LON, lat, now, now,
            leftReason == null ? null : now, leftReason);
    }
}
