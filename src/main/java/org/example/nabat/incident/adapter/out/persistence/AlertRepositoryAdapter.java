package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.shared.persistence.SpatialCapabilityDetector;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.shared.domain.Location;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class AlertRepositoryAdapter implements AlertRepository {

    private final AlertJpaRepository jpaRepository;
    private final SpatialCapabilityDetector spatialCapabilityDetector;

    public AlertRepositoryAdapter(AlertJpaRepository jpaRepository,
                                  SpatialCapabilityDetector spatialCapabilityDetector) {
        this.jpaRepository = jpaRepository;
        this.spatialCapabilityDetector = spatialCapabilityDetector;
    }

    @Override
    public Alert save(Alert alert) {
        AlertJpaEntity entity = AlertJpaEntity.from(alert);
        AlertJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Alert> findById(AlertId id) {
        return jpaRepository.findById(id.value())
            .map(AlertJpaEntity::toDomain);
    }

    @Override
    public List<Alert> findActiveAlertsWithinRadius(Location center, double radiusKm) {
        List<AlertJpaEntity> results = spatialCapabilityDetector.isPostgisAvailable()
                ? jpaRepository.findActiveAlertsWithinRadius(center.latitude(), center.longitude(), radiusKm)
                : jpaRepository.findActiveAlertsWithinRadiusHaversine(center.latitude(), center.longitude(), radiusKm);
        return toDomain(results);
    }

    @Override
    public List<Alert> findActiveAlertsWithinRadiusSince(Location center, double radiusKm, Instant since) {
        List<AlertJpaEntity> results = spatialCapabilityDetector.isPostgisAvailable()
                ? jpaRepository.findActiveAlertsWithinRadiusSince(
                    center.latitude(), center.longitude(), radiusKm, since)
                : jpaRepository.findActiveAlertsWithinRadiusSinceHaversine(
                    center.latitude(), center.longitude(), radiusKm, since);
        return toDomain(results);
    }

    @Override
    public List<Alert> findByStatus(AlertStatus status) {
        return toDomain(jpaRepository.findByStatus(status));
    }

    /**
     * Transactional here rather than relying on an ambient transaction, because the
     * callers deliberately do <em>not</em> wrap the surrounding vote flow in one —
     * that flow makes network calls to the voting service, and holding a database
     * transaction across them exhausted the connection pool under load.
     */
    @Override
    @Transactional
    public Optional<Alert> applyVoteCounts(
        AlertId alertId, int upvotes, int downvotes, int confirmations, int credibilityScore) {

        int updated = jpaRepository.updateVoteCounts(
            alertId.value(), upvotes, downvotes, confirmations, credibilityScore);

        if (updated == 0) {
            return Optional.empty();
        }

        // The @Modifying query clears the persistence context, so this re-reads the
        // freshly written row rather than a cached copy.
        return findById(alertId);
    }

    private static List<Alert> toDomain(List<AlertJpaEntity> entities) {
        return entities.stream()
                .map(AlertJpaEntity::toDomain)
                .toList();
    }
}
