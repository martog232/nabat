package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.incident.application.port.out.DeadlineStepStore;
import org.example.nabat.incident.domain.AnswerOverdue.Stage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Deadline steps, in plain SQL like the forwards and rejections (V33). */
@Component
public class JdbcDeadlineStepStore implements DeadlineStepStore {

    private final JdbcTemplate jdbc;

    public JdbcDeadlineStepStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> dueForReminder(Instant now, int limit) {
        return jdbc.queryForList("""
            SELECT a.id FROM alerts a
            WHERE a.answer_due_at < ?
              AND NOT EXISTS (SELECT 1 FROM report_deadline_steps s
                              WHERE s.alert_id = a.id AND s.due_at = a.answer_due_at AND s.stage = 'REMINDER')
            ORDER BY a.answer_due_at
            LIMIT ?
            """, UUID.class, now.atOffset(ZoneOffset.UTC), limit);
    }

    /** The reminder must be about the deadline the report has now; an older one is history. */
    @Override
    public List<UUID> dueForEscalation(Instant remindedBy, int limit) {
        return jdbc.queryForList("""
            SELECT a.id FROM alerts a
            JOIN report_deadline_steps r
              ON r.alert_id = a.id AND r.due_at = a.answer_due_at AND r.stage = 'REMINDER'
            WHERE r.taken_at <= ?
              AND NOT EXISTS (SELECT 1 FROM report_deadline_steps s
                              WHERE s.alert_id = a.id AND s.due_at = a.answer_due_at AND s.stage = 'ESCALATION')
            ORDER BY r.taken_at
            LIMIT ?
            """, UUID.class, remindedBy.atOffset(ZoneOffset.UTC), limit);
    }

    /**
     * {@code ON CONFLICT DO NOTHING}: the primary key is what lets two runs at once — two
     * replicas — find the same report and only one of them act on it.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean take(UUID alertId, Instant dueAt, Stage stage, Instant at) {
        return jdbc.update("""
            INSERT INTO report_deadline_steps (alert_id, due_at, stage, taken_at) VALUES (?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """, alertId, dueAt.atOffset(ZoneOffset.UTC), stage.name(), at.atOffset(ZoneOffset.UTC)) == 1;
    }
}
