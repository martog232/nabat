package org.example.nabat.subscription.adapter.out.incident;

import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.incident.application.port.out.AlertAudiencePort;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.subscription.application.port.out.UserSubscriptionRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Assembles the audience for a new alert from the two opt-in mechanisms that exist.
 *
 * <p>The union of:
 * <ul>
 *   <li>users subscribed to this alert type whose subscription circle covers the
 *       incident, bounded by the severity's broadcast radius;</li>
 *   <li>users whose own configured notification radius covers the incident.</li>
 * </ul>
 *
 * <p>The two overlap by design — one is opt-in by category, the other is proximity —
 * so they are merged here rather than by the caller, which would otherwise have to
 * know that there are two sources at all.
 */
@Component
public class AlertAudienceAdapter implements AlertAudiencePort {

    private final UserSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public AlertAudienceAdapter(
        UserSubscriptionRepository subscriptionRepository,
        UserRepository userRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<UUID> recipientsFor(AlertType type, Location location, double broadcastRadiusKm) {
        List<UUID> subscribed =
            subscriptionRepository.findUsersSubscribedToAlertType(type, location, broadcastRadiusKm);
        List<UUID> nearby = userRepository.findUsersNearLocation(location);

        // LinkedHashSet so the result is deduplicated but stable, which keeps tests and
        // log output reproducible.
        Set<UUID> recipients = new LinkedHashSet<>(subscribed);
        recipients.addAll(nearby);
        return List.copyOf(recipients);
    }
}
