package org.example.nabat.callsofia.adapter.out.archive;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.callsofia.adapter.out.persistence.JdbcCallSofiaArchiveStore;
import org.example.nabat.callsofia.application.CallSofiaArchiveImportService;
import org.example.nabat.callsofia.application.port.in.ImportCallSofiaArchiveUseCase;
import org.example.nabat.callsofia.application.port.in.ImportCallSofiaArchiveUseCase.ImportSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fixture archive through the real parser into the real V21 schema, on PostGIS.
 * In this package so it can hold {@link YurukovRegions} against {@code districts}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CallSofiaArchiveImportService.class, YurukovCsvArchive.class, JdbcCallSofiaArchiveStore.class})
class CallSofiaArchiveImportIntegrationTest extends PostgresTestSupport {

    @Autowired
    private ImportCallSofiaArchiveUseCase importArchive;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void importsTheArchiveAndLeavesOutHistoryWithNoReport() {
        ImportSummary summary = importArchive.importArchive();

        assertThat(summary).isEqualTo(new ImportSummary(6, 4, 7, 1));
        assertThat(count("call_sofia_signals")).isEqualTo(4);
        assertThat(count("call_sofia_status_changes")).isEqualTo(7);
    }

    @Test
    void importingTheSameArchiveAgainChangesNothing() {
        importArchive.importArchive();
        List<Map<String, Object>> before = snapshot();

        importArchive.importArchive();

        assertThat(snapshot()).isEqualTo(before);
    }

    /** Built longitude first; ST_X is longitude. */
    @Test
    void storesThePointWhereItIs() {
        importArchive.importArchive();

        Map<String, Object> point = jdbc.queryForMap(
            "SELECT ST_X(location::geometry) AS lon, ST_Y(location::geometry) AS lat FROM call_sofia_signals WHERE id = 295879");
        assertThat(point).containsEntry("lon", 23.294585).containsEntry("lat", 42.636833);
        assertThat(jdbc.queryForObject(
            "SELECT location IS NULL FROM call_sofia_signals WHERE id = 296879", Boolean.class)).isTrue();
    }

    /** A code that is not in districts would put a whole district's reports nowhere. */
    @Test
    void everyDistrictTheArchiveNamesIsOneOfOurs() {
        List<String> ours = jdbc.queryForList("SELECT code FROM districts", String.class);

        assertThat(ours).containsExactlyInAnyOrderElementsOf(YurukovRegions.all().values());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private List<Map<String, Object>> snapshot() {
        return jdbc.queryForList("""
            SELECT s.id, s.category_code, s.district_code, s.submitted_at, ST_AsText(s.location) AS location,
                   s.title, c.position, c.changed_at, c.status
            FROM call_sofia_signals s LEFT JOIN call_sofia_status_changes c ON c.signal_id = s.id
            ORDER BY s.id, c.position
            """);
    }
}
