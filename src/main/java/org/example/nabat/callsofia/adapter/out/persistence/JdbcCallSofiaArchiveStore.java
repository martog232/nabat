package org.example.nabat.callsofia.adapter.out.persistence;

import org.example.nabat.callsofia.application.port.out.CallSofiaArchiveStore;
import org.example.nabat.callsofia.domain.CallSofiaCategory;
import org.example.nabat.callsofia.domain.CallSofiaSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatusChange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Plain JDBC, not JPA, and that is the exception in this codebase rather than a new rule.
 *
 * <p>The import writes millions of rows that nothing reads back as objects, and every write
 * is an upsert. JPA would load or track an entity per row to find out whether it exists,
 * which is precisely the question {@code ON CONFLICT} answers in the database for free.
 * Reading this data, when something needs to, can still go through JPA.
 */
@Component
public class JdbcCallSofiaArchiveStore implements CallSofiaArchiveStore {

    private static final String UPSERT_CATEGORY = """
        INSERT INTO call_sofia_categories (code, name) VALUES (?, ?)
        ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name
        """;

    /**
     * ST_MakePoint is strict, so null coordinates make a null location without a branch.
     * Longitude first.
     */
    private static final String UPSERT_SIGNAL = """
        INSERT INTO call_sofia_signals (id, category_code, district_code, submitted_at, location, title)
        VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
        ON CONFLICT (id) DO UPDATE SET
            category_code = EXCLUDED.category_code,
            district_code = EXCLUDED.district_code,
            submitted_at  = EXCLUDED.submitted_at,
            location      = EXCLUDED.location,
            title         = EXCLUDED.title
        """;

    private static final String INSERT_STATUS_CHANGE = """
        INSERT INTO call_sofia_status_changes (signal_id, position, changed_at, status)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (signal_id, position) DO NOTHING
        """;

    private final JdbcTemplate jdbc;

    public JdbcCallSofiaArchiveStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void saveCategories(List<CallSofiaCategory> categories) {
        jdbc.batchUpdate(UPSERT_CATEGORY, categories, categories.size(), (ps, category) -> {
            ps.setInt(1, category.code());
            ps.setString(2, category.name());
        });
    }

    @Override
    @Transactional
    public void saveSignals(List<CallSofiaSignal> signals) {
        jdbc.batchUpdate(UPSERT_SIGNAL, signals, signals.size(), (ps, signal) -> {
            ps.setLong(1, signal.id());
            ps.setInt(2, signal.categoryCode());
            ps.setString(3, signal.districtCode());
            ps.setObject(4, utc(signal.submittedAt()));
            if (signal.location() == null) {
                ps.setNull(5, Types.DOUBLE);
                ps.setNull(6, Types.DOUBLE);
            } else {
                ps.setDouble(5, signal.location().longitude());
                ps.setDouble(6, signal.location().latitude());
            }
            ps.setString(7, signal.title());
        });
    }

    @Override
    @Transactional
    public void saveStatusChanges(List<CallSofiaStatusChange> changes) {
        jdbc.batchUpdate(INSERT_STATUS_CHANGE, changes, changes.size(), JdbcCallSofiaArchiveStore::bind);
    }

    /** Not CONCURRENTLY: an import is rare, and a plain refresh needs no unique-index dance. */
    @Override
    @Transactional
    public void refreshOutcomes() {
        jdbc.execute("REFRESH MATERIALIZED VIEW call_sofia_signal_outcomes");
    }

    private static void bind(PreparedStatement ps, CallSofiaStatusChange change) throws SQLException {
        ps.setLong(1, change.signalId());
        ps.setInt(2, change.position());
        ps.setObject(3, utc(change.changedAt()));
        ps.setString(4, change.status().name());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
