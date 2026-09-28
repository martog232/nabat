package org.example.nabat.jurisdiction.adapter.out.persistence;

import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.jurisdiction.domain.District;
import org.example.nabat.shared.domain.Location;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DistrictRepositoryAdapter implements DistrictRepository {

    private final DistrictJpaRepository jpaRepository;

    public DistrictRepositoryAdapter(DistrictJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<District> findAllOrderedByName() {
        return jpaRepository.findAllByOrderByNameAsc().stream()
            .map(DistrictJpaEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<District> findByCode(String code) {
        return jpaRepository.findById(code).map(DistrictJpaEntity::toDomain);
    }

    @Override
    public Optional<District> findCovering(Location location) {
        return jpaRepository.findCovering(location.latitude(), location.longitude())
            .map(DistrictJpaEntity::toDomain);
    }
}
