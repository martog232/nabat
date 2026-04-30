package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.UserSubscription;

import java.util.List;
import java.util.UUID;

public interface SubscribeToAlertsUseCase {

    UserSubscription subscribe(SubscribeCommand command);

    List<UserSubscription> listMine(UserId userId);

    /**
     * Removes the subscription owned by {@code actor}.
     * @throws org.springframework.security.access.AccessDeniedException if not owner
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
