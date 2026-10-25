package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.incident.application.port.out.ForwardStore;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.ReportForward;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Forwards, in plain SQL like the comments and fix checks beside them (V30). */
@Component
public class JdbcForwardStore implements ForwardStore {

    private static final String COLUMNS =
        "id, alert_id, sequence, from_authority, to_authority, forwarded_by, by_moderator, reason, forwarded_at";

    private static final RowMapper<ReportForward> FORWARD = (rs, row) -> new ReportForward(
        rs.getObject("id", UUID.class),
        AlertId.of(rs.getObject("alert_id", UUID.class)),
        rs.getInt("sequence"),
        rs.getString("from_authority"),
        rs.getString("to_authority"),
        rs.getObject("forwarded_by", UUID.class),
        rs.getBoolean("by_moderator"),
        rs.getString("reason"),
        rs.getTimestamp("forwarded_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcForwardStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Mandatory, so it is part of the transaction that moves the report. The sequence is
     * unique per report; two forwards of one report at once already collide on the report's
     * version, and this is the second line.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(ReportForward f) {
        jdbc.update("INSERT INTO report_forwards (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            f.id(), f.alertId().value(), f.sequence(), f.fromAuthority(), f.toAuthority(), f.forwardedBy(),
            f.byModerator(), f.reason(), f.forwardedAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public List<ReportForward> forwardsOf(UUID alertId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM report_forwards WHERE alert_id = ? ORDER BY sequence",
            FORWARD, alertId);
    }
}
