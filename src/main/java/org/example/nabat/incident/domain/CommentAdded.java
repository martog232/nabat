package org.example.nabat.incident.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Someone wrote in a report's thread. Published so the people in that conversation hear of
 * it: the reporter, and everyone who has written there before.
 *
 * <p>The recipients are worked out here, where the thread is, and carried on the event, so
 * the notification module needs no way to read comments. The author is never among them.
 *
 * <p>Stored by the Event Publication Registry and replayed from JSON, like
 * {@link AlertResolved}: anything added here must survive a Jackson round-trip.
 *
 * @param byAuthority whether the report's authority wrote it, which the notification says
 */
public record CommentAdded(
    AlertId alertId,
    String alertTitle,
    UUID authorId,
    String authorName,
    boolean byAuthority,
    Set<UUID> recipients
) {
}
