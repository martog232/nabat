package org.example.nabat.adapter.out.persistence;

import org.example.nabat.application.port.out.UserSubscriptionRepository;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Temporary in-memory implementation for user subscriptions.
 * Replace with JPA-backed implementation when user subscription feature is fully implemented.
 */
@Component
public class InMemoryUserSubscriptionRepository implements UserSubscriptionRepository {

    @Override
    public List<UUID> findUsersSubscribedToAlertType(AlertType type, Location center, double radiusKm) {
        // TODO: Implement with real data storage
        // For now, return empty list - no users subscribed
        return List.of();
    }
}

