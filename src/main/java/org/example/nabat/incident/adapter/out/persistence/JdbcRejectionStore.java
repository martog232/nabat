package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.incident.application.port.out.RejectionStore;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.ReportRejection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Rejections, in plain SQL like the forwards and comments beside them (V31). */
@Component
public class JdbcRejectionStore implements RejectionStore {

    private static final String COLUMNS = "id, alert_id, rejected_by, by_moderator, authority_code, reason, "
        + "rejected_at, dispute_reason, disputed_at, decision, decided_by, decided_at";

    private static final RowMapper<ReportRejection> REJECTION = (rs, row) -> {
        String decision = rs.getString("decision");
        return new ReportRejection(
            rs.getObject("id", UUID.class),
            AlertId.of(rs.getObject("alert_id", UUID.class)),
            rs.getObject("rejected_by", UUID.class),
            rs.getBoolean("by_moderator"),
            rs.getString("authority_code"),
            rs.getString("reason"),
            instant(rs.getTimestamp("rejected_at")),
            rs.getString("dispute_reason"),
            instant(rs.getTimestamp("disputed_at")),
            decision == null ? null : ReportRejection.Decision.valueOf(decision),
            rs.getObject("decided_by", UUID.class),
            instant(rs.getTimestamp("decided_at")));
    };

    private final JdbcTemplate jdbc;

    public JdbcRejectionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(ReportRejection r) {
        jdbc.update("INSERT INTO report_rejections (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL)",
            r.id(), r.alertId().value(), r.rejectedBy(), r.byModerator(), r.authorityCode(), r.reason(),
            utc(r.rejectedAt()));
    }

    /**
     * {@code FOR UPDATE}: a dispute and a decision on one rejection, or two of either, are
     * made one after the other, and the second reads what the first wrote.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReportRejection> holdLatest(UUID alertId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM report_rejections WHERE alert_id = ? "
                + "ORDER BY rejected_at DESC LIMIT 1 FOR UPDATE", REJECTION, alertId)
            .stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(ReportRejection r) {
        jdbc.update("""
            UPDATE report_rejections
            SET dispute_reason = ?, disputed_at = ?, decision = ?, decided_by = ?, decided_at = ?
            WHERE id = ?
            """,
            r.disputeReason(), utc(r.disputedAt()), r.decision() == null ? null : r.decision().name(),
            r.decidedBy(), utc(r.decidedAt()), r.id());
    }

    @Override
    public List<ReportRejection> rejectionsOf(UUID alertId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM report_rejections WHERE alert_id = ? ORDER BY rejected_at",
            REJECTION, alertId);
    }

    /**
     * Only a report's latest rejection counts: an older one, disputed and then overtaken by a
     * reopening and a new rejection, is history, not a question still waiting.
     */
    @Override
    public List<UUID> openDisputes(int limit) {
        return jdbc.queryForList("""
            SELECT r.alert_id FROM report_rejections r
            WHERE r.disputed_at IS NOT NULL AND r.decision IS NULL
              AND r.rejected_at = (SELECT max(l.rejected_at) FROM report_rejections l WHERE l.alert_id = r.alert_id)
            ORDER BY r.disputed_at
            LIMIT ?
            """, UUID.class, limit);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
