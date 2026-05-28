package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.Alert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AlertJpaMapper {
    AlertJpaEntity toEntity(Alert alert);
    Alert toDomain(AlertJpaEntity entity);
}
