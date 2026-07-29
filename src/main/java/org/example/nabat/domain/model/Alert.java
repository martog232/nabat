package org.example.nabat.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A safety alert.
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
        AlertSeverity severity,
        Location location,
        Instant createdAt,
        AlertStatus status,
        UUID reportedBy,
        int upvoteCount,
        int downvoteCount,
        int confirmationCount,
        int credibilityScore,
        Instant resolvedAt,
        String photoUrl
) {
    public static Alert create(
            String title,
            String description,
            AlertType type,
            AlertSeverity severity,
            Location location,
            UUID reportedBy,
            String photoUrl
    ) {
        return new Alert(
                AlertId.generate(),
                title,
                description,
                type,
                severity,
                location,
                Instant.now(),
                AlertStatus.ACTIVE,
                reportedBy,
                0, 0, 0, 0,
                null,
                photoUrl
        );
    }

    /**
     * Returns a copy of this alert marked as RESOLVED.
     *
     * @throws IllegalStateException if the alert is already resolved — this is the
     *                               only supported way to make the transition, so
     *                               that a double-resolve surfaces as a 409 rather
     *                               than silently overwriting {@code resolvedAt}
     */
    public Alert resolve() {
        if (status == AlertStatus.RESOLVED) {
            throw new IllegalStateException("Alert is already resolved");
        }
        return new Alert(
                id, title, description, type, severity, location,
                createdAt, AlertStatus.RESOLVED, reportedBy,
                upvoteCount, downvoteCount, confirmationCount, credibilityScore,
                Instant.now(), photoUrl
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
                id, title, description, type, severity, location,
                createdAt, status, reportedBy,
                upvotes, downvotes, confirmations, credibilityScore,
                resolvedAt, photoUrl
        );
    }
}
