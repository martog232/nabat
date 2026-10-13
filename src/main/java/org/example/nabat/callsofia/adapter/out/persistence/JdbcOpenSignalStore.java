package org.example.nabat.callsofia.adapter.out.persistence;

import org.example.nabat.callsofia.application.port.out.OpenSignalStore;
import org.example.nabat.callsofia.domain.CallSofiaOpenSignal;
import org.example.nabat.callsofia.domain.MapDeparture;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JDBC for the same reason as {@link JdbcCallSofiaArchiveStore}: ten thousand upserts a
 * reading, none of them read back as objects.
 */
@Component
public class JdbcOpenSignalStore implements OpenSignalStore {

    private static final int BATCH_SIZE = 1_000;

    /**
     * A report already recorded keeps its district — it is computed once, from the point,
     * when the report is first seen — and comes back onto the map if it had left it.
     */
    private static final String UPSERT = """
        INSERT INTO call_sofia_open_signals (
            id, registry_number, category_id, category_name, status, title, submitted_at,
            location, district_code, first_seen_at, last_seen_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            registry_number = EXCLUDED.registry_number,
            category_id     = EXCLUDED.category_id,
            category_name   = EXCLUDED.category_name,
            status          = EXCLUDED.status,
            title           = EXCLUDED.title,
            submitted_at    = EXCLUDED.submitted_at,
            location        = EXCLUDED.location,
            district_code   = COALESCE(call_sofia_open_signals.district_code, EXCLUDED.district_code),
            last_seen_at    = EXCLUDED.last_seen_at,
            left_map_at     = NULL,
            left_map_reason = NULL
        """;

    /**
     * Still marked as on the map, but not written by this reading. Aged-out first, so the
     * second statement only sees what is left.
     */
    private static final String MARK_AGED_OUT = """
        UPDATE call_sofia_open_signals SET left_map_at = ?, left_map_reason = ?
        WHERE left_map_at IS NULL AND last_seen_at < ? AND submitted_at < ?
        """;

    private static final String MARK_NO_LONGER_PROCESSING = """
        UPDATE call_sofia_open_signals SET left_map_at = ?, left_map_reason = ?
        WHERE left_map_at IS NULL AND last_seen_at < ?
        """;

    private final JdbcTemplate jdbc;

    public JdbcOpenSignalStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<Long> knownIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
            "SELECT id FROM call_sofia_open_signals WHERE id = ANY(?)", Long.class, (Object) ids.toArray(Long[]::new)));
    }

    @Override
    @Transactional
    public Departures recordReading(
        List<CallSofiaOpenSignal> onMap,
        Map<Long, String> districtCodes,
        Instant seenAt,
        Instant agedBefore
    ) {
        OffsetDateTime seen = utc(seenAt);
        jdbc.batchUpdate(UPSERT, onMap, BATCH_SIZE, (ps, signal) -> {
            ps.setLong(1, signal.id());
            ps.setString(2, signal.registryNumber());
            ps.setInt(3, signal.categoryId());
            ps.setString(4, signal.categoryName());
            ps.setString(5, signal.status().name());
            ps.setString(6, signal.title());
            ps.setObject(7, utc(signal.submittedAt()));
            if (signal.location() == null) {
                ps.setNull(8, Types.DOUBLE);
                ps.setNull(9, Types.DOUBLE);
            } else {
                ps.setDouble(8, signal.location().longitude());
                ps.setDouble(9, signal.location().latitude());
            }
            ps.setString(10, districtCodes.get(signal.id()));
            ps.setObject(11, seen);
            ps.setObject(12, seen);
        });

        int agedOut = jdbc.update(MARK_AGED_OUT, seen, MapDeparture.AGED_OUT.name(), seen, utc(agedBefore));
        int noLongerProcessing = jdbc.update(MARK_NO_LONGER_PROCESSING, seen, MapDeparture.NO_LONGER_PROCESSING.name(), seen);
        return new Departures(agedOut, noLongerProcessing);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
