package org.example.nabat.incident.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.Comment;

import java.util.List;
import java.util.UUID;

/**
 * A report's thread: anyone signed in writes in it while the report is open, and everyone
 * reads it, open or not.
 */
public interface CommentOnReportUseCase {

    /**
     * @throws org.example.nabat.incident.domain.CommentsLockedException if the report is resolved
     *         or rejected
     * @throws IllegalArgumentException if the text is blank or longer than
     *         {@link Comment#MAX_LENGTH}
     */
    Comment add(AlertId alertId, User author, String body);

    /**
     * Takes a comment down. Its author may, while the report is open; a moderator may, at any
     * time — a closed thread is frozen for its participants, not for abuse. Taking down one
     * already taken down changes nothing.
     *
     * @throws org.example.nabat.incident.domain.CommentNotFoundException if it is not on this report
     * @throws org.example.nabat.incident.domain.CommentsLockedException if its author tries after
     *         the report closed
     * @throws org.example.nabat.shared.domain.NotAuthorizedException if it is someone else's and
     *         the actor is no moderator
     */
    Comment remove(AlertId alertId, UUID commentId, User actor);

    /** Oldest first, removed ones included, so a conversation reads in order. */
    List<Comment> thread(AlertId alertId);
}
