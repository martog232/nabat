package org.example.nabat.incident.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One message in a report's thread.
 *
 * <p>{@code byAuthority} and {@code byReporter} are the author's standing on this report when
 * they wrote, kept rather than looked up: an official later moved to another authority still
 * spoke for this one, and the thread should keep saying so.
 *
 * @param authorName the author's display name when they wrote — see V27 for why it is kept
 * @param body       null once removed; the row stays so that the replies after it still read
 *                   as replies
 * @param removedBy  who took it down; null while it stands
 */
public record Comment(
    UUID id,
    AlertId alertId,
    UUID authorId,
    String authorName,
    boolean byAuthority,
    boolean byReporter,
    String body,
    Instant createdAt,
    Instant removedAt,
    CommentRemoval removedBy
) {

    /** As long as the column allows; a longer one is a request to refuse, not to cut short. */
    public static final int MAX_LENGTH = 2000;

    public boolean isRemoved() {
        return removedBy != null;
    }

    public boolean isBy(UUID userId) {
        return authorId.equals(userId);
    }
}
