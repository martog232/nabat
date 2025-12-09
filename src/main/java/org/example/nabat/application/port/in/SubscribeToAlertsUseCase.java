package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.AlertType;

import java.util.Set;
import java.util.UUID;

public interface SubscribeToAlertsUseCase {

    void subscribe(SubscriptionCommand command);

    record SubscriptionCommand(
        UUID userId,
        double latitude,
        double longitude,
        double radiusKm,
        Set<AlertType> alertTypes
    ) {
    }
}
