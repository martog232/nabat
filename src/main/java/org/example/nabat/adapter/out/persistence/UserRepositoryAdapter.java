package org.example.nabat.adapter.out.persistence;

import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final SpatialCapabilityDetector spatialCapabilityDetector;

    public UserRepositoryAdapter(
        UserJpaRepository jpaRepository,
        SpatialCapabilityDetector spatialCapabilityDetector
    ) {
        this.jpaRepository = jpaRepository;
        this.spatialCapabilityDetector = spatialCapabilityDetector;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = UserJpaEntity.from(user);
        UserJpaEntity savedEntity = jpaRepository.save(entity);
        return savedEntity.toDomain();
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.value())
            .map(UserJpaEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
            .map(UserJpaEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public List<UUID> findUsersNearLocation(Location alertLocation) {
        return spatialCapabilityDetector.isPostgisAvailable()
            ? jpaRepository.findUsersNearLocationPostgis(alertLocation.latitude(), alertLocation.longitude())
            : jpaRepository.findUsersNearLocationHaversine(alertLocation.latitude(), alertLocation.longitude());
    }
}
