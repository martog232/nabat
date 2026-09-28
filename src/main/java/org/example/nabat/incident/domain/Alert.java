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
        AlertType type,
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
         * The district the report is in, which decides who is responsible for it. Written
         * once at creation and never changed: the report does not move. Null only on reports
         * that predate districts and lie outside the city (V18).
         */
        String districtCode
) {
    public static Alert create(
            String title,
            String description,
            AlertType type,
            Location location,
            UUID reportedBy,
            String photoUrl,
            String districtCode
    ) {
        return new Alert(
                AlertId.generate(),
                title,
                description,
                type,
                location,
                Instant.now(),
                AlertStatus.SUBMITTED,
                reportedBy,
                0, 0, 0, 0,
                null,
                photoUrl,
                districtCode
        );
    }

    /**
     * Returns a copy of this alert marked as RESOLVED.
     *
     * @throws IllegalStateException if the alert is already closed — this is the only
     *                               supported way to make the transition, so that a
     *                               double-resolve surfaces as a 409 rather than silently
     *                               overwriting {@code resolvedAt}
     */
    public Alert resolve() {
        return moveTo(AlertStatus.RESOLVED, Instant.now());
    }

    /** Closed without a fix. {@code resolvedAt} stays empty: nothing was resolved. */
    public Alert reject() {
        return moveTo(AlertStatus.REJECTED, null);
    }

    /** The signal has been delivered to the institution responsible for it. */
    public Alert markSent() {
        return moveTo(AlertStatus.SENT, null);
    }

    /** The institution has confirmed it received the signal. */
    public Alert acknowledge() {
        return moveTo(AlertStatus.ACKNOWLEDGED, null);
    }

    /** The institution reports that work has started. */
    public Alert startWork() {
        return moveTo(AlertStatus.IN_PROGRESS, null);
    }

    /**
     * The one place a status changes. Every transition goes through
     * {@link AlertStatus#canMoveTo}, so the rules cannot differ between two callers.
     */
    private Alert moveTo(AlertStatus target, Instant resolvedAt) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Alert cannot move from " + status + " to " + target);
        }
        return new Alert(
                id, title, description, type, location,
                createdAt, target, reportedBy,
                upvoteCount, downvoteCount, confirmationCount, credibilityScore,
                resolvedAt, photoUrl, districtCode
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
                id, title, description, type, location,
                createdAt, status, reportedBy,
                upvotes, downvotes, confirmations, credibilityScore,
                resolvedAt, photoUrl, districtCode
        );
    }
}
