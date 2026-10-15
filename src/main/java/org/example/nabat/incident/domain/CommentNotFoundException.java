package org.example.nabat.incident.domain;

import java.util.UUID;

/** No such comment on that report — including one that exists, but on another report. */
public class CommentNotFoundException extends RuntimeException {

    public CommentNotFoundException(AlertId alertId, UUID commentId) {
        super("Comment " + commentId + " not found on report " + alertId);
    }
}
