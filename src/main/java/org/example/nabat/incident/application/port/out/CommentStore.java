package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.CommentRemoval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A report's comments.
 */
public interface CommentStore {

    /**
     * Holds the report's row for the rest of the transaction and says what state it is in.
     *
     * <p>A shared lock, so comments do not wait on one another, but a status change — which
     * updates the row — waits for the comment, and a comment waits for a status change in
     * flight and then sees its result. Without it a comment checked against an open report
     * could commit just after the report closed.
     *
     * @throws org.example.nabat.incident.domain.AlertNotFoundException if there is no such report
     */
    AlertStatus holdReport(UUID alertId);

    void insert(Comment comment);

    Optional<Comment> find(UUID alertId, UUID commentId);

    void markRemoved(UUID commentId, Instant at, CommentRemoval by);

    List<Comment> thread(UUID alertId);

    /** Everyone who has a comment standing on the report. */
    Set<UUID> authorsOn(UUID alertId);
}
