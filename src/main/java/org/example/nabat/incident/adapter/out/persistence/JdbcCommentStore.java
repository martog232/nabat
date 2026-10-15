package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.incident.application.port.out.CommentStore;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.CommentRemoval;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Comments, in plain SQL like the fix checks beside them.
 *
 * <p>The thing this class exists to get right is {@link #holdReport}: {@code FOR SHARE} on
 * the report's row. A status change is a JPA update of that row, which needs the row
 * exclusively, so the two serialise — a comment taken before a resolve commits first, and
 * one taken during a resolve waits for it and then reads the closed status. Shared rather
 * than {@code FOR UPDATE} because two comments have nothing to wait for from each other.
 */
@Component
public class JdbcCommentStore implements CommentStore {

    private static final String COLUMNS =
        "id, alert_id, author_id, author_name, by_district, by_reporter, body, created_at, removed_at, removed_by";

    private static final RowMapper<Comment> COMMENT = (rs, row) -> {
        String removedBy = rs.getString("removed_by");
        Timestamp removedAt = rs.getTimestamp("removed_at");
        return new Comment(
            rs.getObject("id", UUID.class),
            AlertId.of(rs.getObject("alert_id", UUID.class)),
            rs.getObject("author_id", UUID.class),
            rs.getString("author_name"),
            rs.getBoolean("by_district"),
            rs.getBoolean("by_reporter"),
            // The text stays in the row for moderation, but a removed comment does not say it.
            removedBy == null ? rs.getString("body") : null,
            rs.getTimestamp("created_at").toInstant(),
            removedAt == null ? null : removedAt.toInstant(),
            removedBy == null ? null : CommentRemoval.valueOf(removedBy));
    };

    private final JdbcTemplate jdbc;

    public JdbcCommentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AlertStatus holdReport(UUID alertId) {
        List<String> status = jdbc.queryForList("SELECT status FROM alerts WHERE id = ? FOR SHARE", String.class, alertId);
        if (status.isEmpty()) {
            throw new AlertNotFoundException(AlertId.of(alertId));
        }
        return AlertStatus.valueOf(status.getFirst());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Comment c) {
        jdbc.update("INSERT INTO alert_comments (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)",
            c.id(), c.alertId().value(), c.authorId(), c.authorName(), c.byDistrict(), c.byReporter(), c.body(),
            c.createdAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public Optional<Comment> find(UUID alertId, UUID commentId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM alert_comments WHERE id = ? AND alert_id = ?",
            COMMENT, commentId, alertId).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markRemoved(UUID commentId, Instant at, CommentRemoval by) {
        // Only if it still stands: two removals at once leave the first one's record.
        jdbc.update("UPDATE alert_comments SET removed_at = ?, removed_by = ? WHERE id = ? AND removed_at IS NULL",
            at.atOffset(ZoneOffset.UTC), by.name(), commentId);
    }

    @Override
    public List<Comment> thread(UUID alertId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM alert_comments WHERE alert_id = ? ORDER BY created_at, id",
            COMMENT, alertId);
    }

    @Override
    public Set<UUID> authorsOn(UUID alertId) {
        return new HashSet<>(jdbc.queryForList(
            "SELECT DISTINCT author_id FROM alert_comments WHERE alert_id = ? AND removed_at IS NULL",
            UUID.class, alertId));
    }
}
