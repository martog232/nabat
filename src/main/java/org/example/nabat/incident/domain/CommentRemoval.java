package org.example.nabat.incident.domain;

/**
 * Who took a comment down. The thread says which, because "removed by its author" and
 * "removed by a moderator" are different things to read in the middle of a conversation.
 *
 * <p>Persisted by name and pinned by a {@code CHECK} in {@code V27__alert_comments.sql}.
 */
public enum CommentRemoval {
    AUTHOR,
    MODERATOR
}
