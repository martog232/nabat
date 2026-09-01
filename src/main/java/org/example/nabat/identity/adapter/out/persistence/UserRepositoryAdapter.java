package org.example.nabat.identity.adapter.out.persistence;

import org.example.nabat.shared.persistence.SpatialCapabilityDetector;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    /**
     * Newest first, so a page-one admin sees the accounts that just registered — the ones any
     * moderation question is usually about — without paging to the end.
     */
    @Override
    public UserPage findAll(int page, int size) {
        Page<UserJpaEntity> found = jpaRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return new UserPage(
            found.getContent().stream().map(UserJpaEntity::toDomain).toList(),
            found.getTotalElements());
    }

    @Override
    public List<UUID> findUsersNearLocation(Location alertLocation) {
        return spatialCapabilityDetector.isPostgisAvailable()
            ? jpaRepository.findUsersNearLocationPostgis(alertLocation.latitude(), alertLocation.longitude())
            : jpaRepository.findUsersNearLocationHaversine(alertLocation.latitude(), alertLocation.longitude());
    }
}
