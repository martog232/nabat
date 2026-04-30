package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.UserSubscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSubscriptionRepository {

    /** Distinct user ids whose subscription matches the alert type and overlaps the given circle. */
    List<UUID> findUsersSubscribedToAlertType(AlertType type, Location center, double radiusKm);

    UserSubscription save(UserSubscription subscription);

    List<UserSubscription> findByUserId(UserId userId);

    Optional<UserSubscription> findById(UUID id);

    void deleteById(UUID id);
}
