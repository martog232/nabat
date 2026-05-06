package org.example.nabat.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.port.out.UserSubscriptionRepository;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.UserSubscription;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserSubscriptionRepositoryAdapter implements UserSubscriptionRepository {

    private final UserSubscriptionJpaRepository jpaRepository;

    @Override
    public List<UUID> findUsersSubscribedToAlertType(AlertType type, Location center, double radiusKm) {
        return jpaRepository.findUserIdsMatching(
                type, center.latitude(), center.longitude(), radiusKm);
    }

    @Override
    public UserSubscription save(UserSubscription subscription) {
        return jpaRepository.save(UserSubscriptionJpaEntity.from(subscription)).toDomain();
    }

    @Override
    public List<UserSubscription> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.value()).stream()
                .map(UserSubscriptionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<UserSubscription> findById(UUID id) {
        return jpaRepository.findById(id).map(UserSubscriptionJpaEntity::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}

