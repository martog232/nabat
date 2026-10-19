package org.example.nabat.incident.domain;

import org.example.nabat.shared.domain.Location;
import java.time.Instant;
import java.util.UUID;

/**
 * A signal about a problem in the city: a pothole, a missing manhole cover, a dump.
 *
 * <p>{@code upvoteCount}, {@code downvoteCount}, {@code confirmationCount} and
 * {@code credibilityScore} are a <em>denormalised projection</em> of state owned by
 * the nabat-voting service. They are carried here so that listing alerts does not
 * require a fan-out of per-alert calls, and they are only ever written from what
 * that service reports.
 *
 * <p>In particular {@code credibilityScore} is <strong>not</strong> recomputed
 * locally. It used to be: {@code AlertJpaEntity.from} called a
 * {@code getCredibilityScore()} method on this record that applied its own formula,
 * so any {@code save} — resolving an alert, for instance — overwrote the score the
 * voting service had just synced, with a value derived from possibly-stale counts.
 * Two writers, two formulas, one column.
 */
public record Alert(
        AlertId id,
        String title,
        String description,
        int categoryCode,
        Location location,
        Instant createdAt,
        AlertStatus status,
        UUID reportedBy,
        int upvoteCount,
        int downvoteCount,
        int confirmationCount,
        int credibilityScore,
        Instant resolvedAt,
        String photoUrl,
        /**
         * The district the report is in. Written once at creation and never changed: the report
         * does not move. Null only on reports that predate districts and lie outside the city
         * (V18). Where it is, not who answers for it: see {@link #authorityCode}.
         */
        String districtCode,
        /** Who closed the report; null while it is open, and on reports closed before V20. */
        ClosedBy closedBy,
        /**
         * What people say about whether a resolved report is really fixed. Never null:
         * {@link FixCheckTally#NONE} until someone answers. Written by the fix-check store,
         * never by saving the report.
         */
        FixCheckTally fixCheck,
        /**
         * Who answers for the report: its district, or the city-wide institution its category
         * belongs to (V29). Officials of this authority, and only they, speak for it. Decided
         * at creation, and changed only by a forward ({@link #forwardTo}, V30).
         */
        String authorityCode
) {
    /**
     * Null becomes {@link FixCheckTally#NONE}: events stored before V24 replay from JSON that
     * has no {@code fixCheck}, and a report must not lose its outbox row over that. A null
     * authority becomes the district for the same reason — before V28, the district was the
     * only authority there was.
     */
    public Alert {
        if (fixCheck == null) {
            fixCheck = FixCheckTally.NONE;
        }
        if (authorityCode == null) {
            authorityCode = districtCode;
        }
    }

    /** Answered for by its district — what everything before V28 constructed. */
    public Alert(
            AlertId id, String title, String description, int categoryCode, Location location,
            Instant createdAt, AlertStatus status, UUID reportedBy,
            int upvoteCount, int downvoteCount, int confirmationCount, int credibilityScore,
            Instant resolvedAt, String photoUrl, String districtCode, ClosedBy closedBy,
            FixCheckTally fixCheck
    ) {
        this(id, title, description, categoryCode, location, createdAt, status, reportedBy,
                upvoteCount, downvoteCount, confirmationCount, credibilityScore,
                resolvedAt, photoUrl, districtCode, closedBy, fixCheck, districtCode);
    }

    /** A report nobody has checked yet — what everything before V24 constructed. */
    public Alert(
            AlertId id, String title, String description, int categoryCode, Location location,
            Instant createdAt, AlertStatus status, UUID reportedBy,
            int upvoteCount, int downvoteCount, int confirmationCount, int credibilityScore,
            Instant resolvedAt, String photoUrl, String districtCode, ClosedBy closedBy
    ) {
        this(id, title, description, categoryCode, location, createdAt, status, reportedBy,
                upvoteCount, downvoteCount, confirmationCount, credibilityScore,
                resolvedAt, photoUrl, districtCode, closedBy, FixCheckTally.NONE, districtCode);
    }

    public static Alert create(
            String title,
            String description,
            int categoryCode,
            Location location,
            UUID reportedBy,
            String photoUrl,
            String districtCode,
            String authorityCode
    ) {
        return new Alert(
                AlertId.generate(),
                title,
                description,
                categoryCode,
                location,
                Instant.now(),
                AlertStatus.SUBMITTED,
                reportedBy,
                0, 0, 0, 0,
                null,
                photoUrl,
                districtCode,
                null,
                FixCheckTally.NONE,
                authorityCode
        );
    }

    /**
     * Returns a copy of this alert marked as RESOLVED, by {@code by}.
     *
     * @throws IllegalStateException if the alert is already closed — this is the only
     *                               supported way to make the transition, so that a
     *                               double-resolve surfaces as a 409 rather than silently
     *                               overwriting {@code resolvedAt}
     */
    public Alert resolve(ClosedBy by) {
        return moveTo(AlertStatus.RESOLVED, Instant.now(), requireCloser(by));
    }

    /** Closed without a fix. {@code resolvedAt} stays empty: nothing was resolved. */
    public Alert reject(ClosedBy by) {
        return moveTo(AlertStatus.REJECTED, null, requireCloser(by));
    }

    /** The signal has been delivered to the institution responsible for it. */
    public Alert markSent() {
        return moveTo(AlertStatus.SENT, null, null);
    }

    /** The institution has confirmed it received the signal. */
    public Alert acknowledge() {
        return moveTo(AlertStatus.ACKNOWLEDGED, null, null);
    }

    /** The institution reports that work has started. */
    public Alert startWork() {
        return moveTo(AlertStatus.IN_PROGRESS, null, null);
    }

    /**
     * Handed on to another authority: "not ours, it is {@code authority}'s".
     *
     * <p>The one move that goes back. The report returns to {@code SUBMITTED}, because the
     * new authority has received nothing yet, and becomes {@code SENT} again when the mail to
     * it leaves; whatever the old authority acknowledged or started was its own word, not the
     * new one's. It does not go through {@link AlertStatus#canMoveTo}, which only knows
     * forward, so this is the only way to make it.
     *
     * @throws IllegalStateException    if the report is closed — a closed report goes nowhere
     * @throws IllegalArgumentException if {@code authority} is the one it already has
     */
    public Alert forwardTo(String authority) {
        if (!status.isOpen()) {
            throw new IllegalStateException("A " + status + " report cannot be forwarded");
        }
        if (authority == null || authority.isBlank()) {
            throw new IllegalArgumentException("Forwarding needs an authority to forward to");
        }
        if (authority.equals(authorityCode)) {
            throw new IllegalArgumentException("The report is already " + authority + "'s");
        }
        return new Alert(
                id, title, description, categoryCode, location,
                createdAt, AlertStatus.SUBMITTED, reportedBy,
                upvoteCount, downvoteCount, confirmationCount, credibilityScore,
                null, photoUrl, districtCode, null, fixCheck, authority
        );
    }

    private static ClosedBy requireCloser(ClosedBy by) {
        if (by == null) {
            throw new IllegalArgumentException("A closed report records who closed it");
        }
        return by;
    }

    /**
     * The one place a status changes. Every transition goes through
     * {@link AlertStatus#canMoveTo}, so the rules cannot differ between two callers.
     */
    private Alert moveTo(AlertStatus target, Instant resolvedAt, ClosedBy by) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Alert cannot move from " + status + " to " + target);
        }
        return new Alert(
                id, title, description, categoryCode, location,
                createdAt, target, reportedBy,
                upvoteCount, downvoteCount, confirmationCount, credibilityScore,
                resolvedAt, photoUrl, districtCode, by, fixCheck, authorityCode
        );
    }

    /**
     * Returns a copy carrying the vote tallies reported by the voting service.
     *
     * <p>The score is supplied rather than derived: the voting service owns the
     * formula, and duplicating it here is what allowed the two to disagree.
     */
    public Alert withVoteCounts(int upvotes, int downvotes, int confirmations, int credibilityScore) {
        return new Alert(
                id, title, description, categoryCode, location,
                createdAt, status, reportedBy,
                upvotes, downvotes, confirmations, credibilityScore,
                resolvedAt, photoUrl, districtCode, closedBy, fixCheck, authorityCode
        );
    }

    /** Returns a copy carrying a fresh fix-check tally, as the store has just counted it. */
    public Alert withFixCheck(FixCheckTally tally) {
        return new Alert(
                id, title, description, categoryCode, location,
                createdAt, status, reportedBy,
                upvoteCount, downvoteCount, confirmationCount, credibilityScore,
                resolvedAt, photoUrl, districtCode, closedBy, tally, authorityCode
        );
    }
}
