package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.incident.application.port.out.FixCheckStore;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.FixCheckTally;
import org.example.nabat.incident.domain.FixVerdict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The answers and the tally they add up to, kept in step.
 *
 * <p>Every write first takes the report's row with {@code FOR UPDATE}. Without it two
 * answers arriving together each insert their row and each recount — and under READ
 * COMMITTED each recount sees its own row but not the other's uncommitted one, so the
 * report ends up counting one of them. With it the second waits, and counts both.
 *
 * <p>The tally is recounted from {@code fix_checks} rather than incremented: a changed
 * mind is an overwrite, and a recount cannot drift however the rows got there.
 */
@Component
public class JdbcFixCheckStore implements FixCheckStore {

    private static final String RECOUNT = """
        UPDATE alerts SET
            fix_fixed_count      = (SELECT count(*) FROM fix_checks WHERE alert_id = ? AND verdict = 'FIXED'),
            fix_partly_count     = (SELECT count(*) FROM fix_checks WHERE alert_id = ? AND verdict = 'PARTLY'),
            fix_not_fixed_count  = (SELECT count(*) FROM fix_checks WHERE alert_id = ? AND verdict = 'NOT_FIXED'),
            reporter_fix_verdict = (SELECT verdict FROM fix_checks WHERE alert_id = ? AND by_reporter)
        WHERE id = ?
        RETURNING fix_fixed_count, fix_partly_count, fix_not_fixed_count, reporter_fix_verdict
        """;

    private final JdbcTemplate jdbc;

    public JdbcFixCheckStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public FixCheckTally record(UUID alertId, UUID userId, FixVerdict verdict, boolean byReporter, Instant at) {
        lock(alertId);
        jdbc.update("""
            INSERT INTO fix_checks (alert_id, user_id, verdict, by_reporter, checked_at) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (alert_id, user_id) DO UPDATE SET verdict = EXCLUDED.verdict, checked_at = EXCLUDED.checked_at
            """, alertId, userId, verdict.name(), byReporter, at.atOffset(ZoneOffset.UTC));
        return recount(alertId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public FixCheckTally withdraw(UUID alertId, UUID userId) {
        lock(alertId);
        jdbc.update("DELETE FROM fix_checks WHERE alert_id = ? AND user_id = ?", alertId, userId);
        return recount(alertId);
    }

    @Override
    public Optional<FixVerdict> verdictOf(UUID alertId, UUID userId) {
        return jdbc.queryForList("SELECT verdict FROM fix_checks WHERE alert_id = ? AND user_id = ?",
                String.class, alertId, userId)
            .stream().findFirst().map(FixVerdict::valueOf);
    }

    private void lock(UUID alertId) {
        List<UUID> locked = jdbc.queryForList("SELECT id FROM alerts WHERE id = ? FOR UPDATE", UUID.class, alertId);
        if (locked.isEmpty()) {
            throw new AlertNotFoundException(AlertId.of(alertId));
        }
    }

    private FixCheckTally recount(UUID alertId) {
        return jdbc.queryForObject(RECOUNT, (rs, row) -> {
            String reporter = rs.getString("reporter_fix_verdict");
            return new FixCheckTally(
                rs.getInt("fix_fixed_count"),
                rs.getInt("fix_partly_count"),
                rs.getInt("fix_not_fixed_count"),
                reporter == null ? null : FixVerdict.valueOf(reporter));
        }, alertId, alertId, alertId, alertId, alertId);
    }
}
