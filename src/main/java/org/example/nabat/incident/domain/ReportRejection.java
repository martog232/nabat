package org.example.nabat.incident.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A report closed without a fix, why, and what came of it (V31).
 *
 * <p>The reporter may dispute it once, saying why; a moderator then upholds it or reopens the
 * report. Each step happens once and in that order — {@link #dispute} and {@link #decide}
 * refuse anything else — so the record reads as what happened.
 *
 * @param byModerator   rejected by a moderator rather than by the report's authority
 * @param authorityCode the authority the report was with when it was rejected
 */
public record ReportRejection(
    UUID id,
    AlertId alertId,
    UUID rejectedBy,
    boolean byModerator,
    String authorityCode,
    String reason,
    Instant rejectedAt,
    String disputeReason,
    Instant disputedAt,
    Decision decision,
    UUID decidedBy,
    Instant decidedAt
) {

    /** As long as the columns allow. */
    public static final int MAX_REASON_LENGTH = 1000;

    public enum Decision {
        /** The rejection stands. */
        UPHELD,
        /** The report is open again, with the same authority. */
        REOPENED
    }

    public static ReportRejection of(
        AlertId alertId, UUID rejectedBy, boolean byModerator, String authorityCode, String reason, Instant at
    ) {
        return new ReportRejection(UUID.randomUUID(), alertId, rejectedBy, byModerator, authorityCode, reason, at,
            null, null, null, null, null);
    }

    public boolean isDisputed() {
        return disputedAt != null;
    }

    public boolean isDecided() {
        return decision != null;
    }

    /** @throws IllegalStateException if it has been disputed already — once is the right */
    public ReportRejection dispute(String reason, Instant at) {
        if (isDisputed()) {
            throw new IllegalStateException("This rejection has been disputed already");
        }
        return new ReportRejection(id, alertId, rejectedBy, byModerator, authorityCode, this.reason, rejectedAt,
            reason, at, null, null, null);
    }

    /** @throws IllegalStateException if nobody disputed it, or it has been decided already */
    public ReportRejection decide(Decision decision, UUID by, Instant at) {
        if (!isDisputed()) {
            throw new IllegalStateException("Only a disputed rejection is decided");
        }
        if (isDecided()) {
            throw new IllegalStateException("This dispute has been decided already");
        }
        return new ReportRejection(id, alertId, rejectedBy, byModerator, authorityCode, reason, rejectedAt,
            disputeReason, disputedAt, decision, by, at);
    }
}
