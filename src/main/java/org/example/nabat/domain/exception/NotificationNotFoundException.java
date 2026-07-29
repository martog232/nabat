package org.example.nabat.domain.exception;

import org.example.nabat.domain.model.NotificationId;

/**
 * No notification exists for the given identifier, or it is not visible to the
 * requesting user.
 *
 * <p>Maps to {@code 404 Not Found}.
 *
 * <p>Used for the "belongs to another user" case as well as genuine absence, so
 * that the two are indistinguishable to the caller. Reporting them differently —
 * as the previous two distinct {@code IllegalArgumentException} messages did —
 * lets an attacker enumerate valid notification ids.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(NotificationId id) {
        super("Notification not found or not accessible: " + id.value());
    }
}
