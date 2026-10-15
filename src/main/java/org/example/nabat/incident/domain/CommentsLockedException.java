package org.example.nabat.incident.domain;

/**
 * The report is resolved or rejected, so its thread is closed.
 *
 * <p>Its own exception rather than {@link IllegalStateException}, so that the client gets a
 * code it can switch on — {@code COMMENTS_LOCKED} — and can say why the box has gone, instead
 * of the generic conflict every other refused state change shares.
 */
public class CommentsLockedException extends RuntimeException {

    public CommentsLockedException(AlertId id, AlertStatus status) {
        super("Report " + id + " is " + status + "; its comments are closed");
    }
}
