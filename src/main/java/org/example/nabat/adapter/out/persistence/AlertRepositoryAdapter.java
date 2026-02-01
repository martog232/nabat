package org.example.nabat.adapter.out.persistence;

import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.Location;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AlertRepositoryAdapter implements AlertRepository {

    private final AlertJpaRepository jpaRepository;

    public AlertRepositoryAdapter(AlertJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
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
        return jpaRepository.findActiveAlertsWithinRadius(
            center.latitude(),
            center.longitude(),
            radiusKm
        ).stream()
         .map(AlertJpaEntity::toDomain)
         .toList();
    }

    @Override
    public List<Alert> findByStatus(AlertStatus status) {
        return jpaRepository.findByStatus(status)
            .stream()
            .map(AlertJpaEntity::toDomain)
            .toList();
    }

    @Override
    public void updateVoteCounts(AlertId alertId, int upvotes, int downvotes, int confirmations) {
        //todo
    }
}

