package org.example.nabat.incident.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One step in a report's life, and who took it (V36).
 *
 * @param actorId       null for what nabat itself does — delivering it
 * @param actorName     the actor's name when they acted, kept like a comment's author name
 * @param byAuthority   the actor spoke for the authority the report was with
 * @param authorityCode the authority the report was with when this happened
 */
public record ReportStep(
    UUID id,
    AlertId alertId,
    Kind kind,
    UUID actorId,
    String actorName,
    boolean byAuthority,
    String authorityCode,
    Instant happenedAt
) {

    public enum Kind {
        CREATED, SENT, ACKNOWLEDGED, WORK_STARTED, RESOLVED, REJECTED, FORWARDED, REOPENED, WITHDRAWN
    }
}
