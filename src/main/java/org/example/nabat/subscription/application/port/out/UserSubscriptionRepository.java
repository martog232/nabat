package org.example.nabat.subscription.application.port.out;

import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.subscription.domain.UserSubscription;

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
