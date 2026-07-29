package org.example.nabat.application.service;

import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.CreateAlertUseCase;
import org.example.nabat.application.port.out.AlertNotificationPort;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.application.port.out.UserSubscriptionRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@UseCase
public class CreateAlertService implements CreateAlertUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAlertService.class);

    private final AlertRepository alertRepository;
    private final AlertNotificationPort notificationPort;
    private final UserSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public CreateAlertService(
        AlertRepository alertRepository,
        AlertNotificationPort notificationPort,
        UserSubscriptionRepository subscriptionRepository,
        UserRepository userRepository
    ) {
        this.alertRepository = alertRepository;
        this.notificationPort = notificationPort;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Persists a new alert and pushes it to interested users.
     *
     * <p>{@code @Transactional} — every other use case in this package was
     * transactional and this one was not, so the save and the recipient lookups could
     * observe inconsistent state.
     *
     * <p>{@code @CacheEvict} — a new alert invalidates every cached nearby-alerts
     * result. Without it, a report could sit invisible for the full cache TTL, which
     * for a safety-alert product is the wrong tradeoff even at 15 seconds.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert createAlert(CreateAlertCommand command) {
        Location location = Location.of(command.latitude(), command.longitude());

        Alert alert = Alert.create(
            command.title(),
            command.description(),
            command.type(),
            command.severity(),
            location,
            command.reportedBy(),
            command.photoUrl()
        );

        Alert savedAlert = alertRepository.save(alert);

        notifyInterestedUsers(savedAlert, location, command.severity());

        return savedAlert;
    }

    /**
     * Union of two audiences:
     * <ul>
     *   <li>users subscribed to this alert type whose subscription circle covers the
     *       incident, bounded by the severity's broadcast radius;</li>
     *   <li>users whose own configured notification radius covers the incident.</li>
     * </ul>
     *
     * <p>The two mechanisms overlap by design — one is opt-in by category, the other
     * is proximity — so the sets are merged before pushing to avoid notifying anybody
     * twice.
     */
    private void notifyInterestedUsers(Alert alert, Location location, AlertSeverity severity) {
        List<UUID> subscribedUsers = subscriptionRepository
            .findUsersSubscribedToAlertType(alert.type(), location, broadcastRadiusKm(severity));

        List<UUID> nearbyUsers = userRepository.findUsersNearLocation(location);

        Set<UUID> recipients = new HashSet<>(subscribedUsers);
        recipients.addAll(nearbyUsers);
        // The reporter does not need to be told about their own report.
        recipients.remove(alert.reportedBy());

        if (recipients.isEmpty()) {
            return;
        }

        log.debug("Pushing new {} alert to {} recipient(s)", severity, recipients.size());
        notificationPort.broadcastAlert(alert, new ArrayList<>(recipients));
    }

    /**
     * How far a new alert is fanned out to <em>subscribers</em>, by severity.
     *
     * <p>Distinct from a user's own {@code notificationRadiusKm} preference, which is
     * applied by {@code findUsersNearLocation}. See {@code NotificationRadius} — these
     * values are not drawn from that allow-list and are not user-selectable.
     */
    private double broadcastRadiusKm(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 10.0;
            case HIGH -> 5.0;
            case MEDIUM -> 2.0;
            case LOW -> 1.0;
        };
    }
}
