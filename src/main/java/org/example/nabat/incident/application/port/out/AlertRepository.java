package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.shared.domain.Location;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findById(AlertId id);

    List<Alert> findActiveAlertsWithinRadius(Location center, double radiusKm);

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
     * Alerts created at or after {@code since} within {@code radiusKm} of
     * {@code center}, newest first. Backs the WebSocket reconnect catch-up.
     */
    List<Alert> findActiveAlertsWithinRadiusSince(Location center, double radiusKm, Instant since);
}
