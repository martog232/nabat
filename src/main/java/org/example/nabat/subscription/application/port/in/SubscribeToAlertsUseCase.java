package org.example.nabat.subscription.application.port.in;

import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.subscription.domain.UserSubscription;

import java.util.List;
import java.util.UUID;

public interface SubscribeToAlertsUseCase {

    UserSubscription subscribe(SubscribeCommand command);

    List<UserSubscription> listMine(UserId userId);

    /**
     * Removes the subscription owned by {@code actor}.
     * @throws org.example.nabat.shared.domain.NotAuthorizedException if not owner
     * @throws IllegalArgumentException if no such subscription
     */
    void unsubscribe(UUID subscriptionId, UserId actor);

    record SubscribeCommand(
            UserId userId,
            AlertType alertType,
            double latitude,
            double longitude,
            double radiusKm
    ) {
    }
}
