package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.Alert;

public interface AlertJpaMapper {
    default AlertJpaEntity toEntity(Alert alert) {
        return AlertJpaEntity.from(alert);
    }

    default Alert toDomain(AlertJpaEntity entity) {
        return entity.toDomain();
    }
}
