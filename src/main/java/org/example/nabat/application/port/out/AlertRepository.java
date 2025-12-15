package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.Location;

import java.util.List;
import java.util.Optional;

public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findById(AlertId id);

    List<Alert> findActiveAlertsWithinRadius(Location center, double radiusKm);

    List<Alert> findByStatus(AlertStatus status);

    void updateVoteCounts(AlertId alertId, int upvotes, int downvotes, int confirmations);
}
