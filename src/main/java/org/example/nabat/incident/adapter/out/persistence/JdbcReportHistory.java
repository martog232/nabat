package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.incident.application.port.out.ReportHistory;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.ReportStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** A report's history, in plain SQL like its comments, forwards and rejections (V36). */
@Component
public class JdbcReportHistory implements ReportHistory {

    private static final String COLUMNS =
        "id, alert_id, step, actor_id, actor_name, by_authority, authority_code, happened_at";

    private static final RowMapper<ReportStep> STEP = (rs, row) -> new ReportStep(
        rs.getObject("id", UUID.class),
        AlertId.of(rs.getObject("alert_id", UUID.class)),
        ReportStep.Kind.valueOf(rs.getString("step")),
        rs.getObject("actor_id", UUID.class),
        rs.getString("actor_name"),
        rs.getBoolean("by_authority"),
        rs.getString("authority_code"),
        rs.getTimestamp("happened_at").toInstant());

    private final JdbcTemplate jdbc;

    public JdbcReportHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(ReportStep s) {
        jdbc.update("INSERT INTO report_history (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            s.id(), s.alertId().value(), s.kind().name(), s.actorId(), s.actorName(), s.byAuthority(),
            s.authorityCode(), s.happenedAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public List<ReportStep> of(UUID alertId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM report_history WHERE alert_id = ? ORDER BY happened_at, id",
            STEP, alertId);
    }
}
