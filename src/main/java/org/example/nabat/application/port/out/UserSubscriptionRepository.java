package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;

import java.util.List;
import java.util.UUID;

public interface UserSubscriptionRepository {
    List<UUID> findUsersSubscribedToAlertType(AlertType type, Location center, double radiusKm);
}

