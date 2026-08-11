package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findById(AlertId id);

    List<Alert> findActiveAlertsWithinRadius(NearbySearch search);

    /**
     * The circle, the optional filters and the cap, as one value.
     *
     * <p>Grouped rather than passed as six positional arguments, and grouped <em>here</em>
     * rather than reusing the in-port's query record — an out-port that imports an in-port
     * would point the dependency backwards.
     *
     * @param type     null means every type; likewise {@code severity}
     * @param limit    hard cap on rows returned. Applied in SQL, not afterwards in Java:
     *                 trimming a list still makes the database materialise and ship every
     *                 row inside the radius, which is the cost being avoided.
     */
    record NearbySearch(
        Location center,
        double radiusKm,
        AlertType type,
        AlertSeverity severity,
        int limit
    ) {
        public NearbySearch {
            if (limit <= 0) {
                throw new IllegalArgumentException("Limit must be positive");
            }
        }

        /** The enum name the native queries compare against, or null for "no filter". */
        public String typeName() {
            return type == null ? null : type.name();
        }

        public String severityName() {
            return severity == null ? null : severity.name();
        }
    }

    List<Alert> findByStatus(AlertStatus status);

    /**
     * Writes the denormalised vote counts and returns the refreshed alert, in one
     * transaction.
     *
     * <p>Callers used to issue {@code updateVoteCounts} and then a separate
     * {@code findById} — twice, in fact, once for the notification and once for the
     * broadcast. Combining them removes two redundant reads per vote and guarantees
     * the returned alert reflects the counts just written.
     *
     * @return the updated alert, or empty when no alert has that id
     */
    Optional<Alert> applyVoteCounts(
        AlertId alertId, int upvotes, int downvotes, int confirmations, int credibilityScore);

    /**
     * Alerts created at or after {@code since} matching {@code search}, newest first.
     * Backs the WebSocket reconnect catch-up.
     */
    List<Alert> findActiveAlertsWithinRadiusSince(NearbySearch search, Instant since);
}
