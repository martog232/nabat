package org.example.nabat.incident.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A report handed from one authority to another, and why (V30).
 *
 * @param sequence    1 for the report's first forward, then 2, 3... — the creation mail is 0
 * @param byModerator a moderator made it, correcting the routing, rather than the authority
 *                    that had the report
 */
public record ReportForward(
    UUID id,
    AlertId alertId,
    int sequence,
    String fromAuthority,
    String toAuthority,
    UUID forwardedBy,
    boolean byModerator,
    String reason,
    Instant forwardedAt
) {

    /** As long as the column allows. */
    public static final int MAX_REASON_LENGTH = 1000;
}
