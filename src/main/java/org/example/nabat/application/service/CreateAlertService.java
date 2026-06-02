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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@UseCase
public class CreateAlertService implements CreateAlertUseCase {

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

    @Override
    public Alert createAlert(CreateAlertCommand command) {
        Location location = Location.of(command.latitude(), command.longitude());

        Alert alert = Alert.create(
            command.title(),
            command.description(),
            command.type(),
            command.severity(),
            location,
            command.reportedBy()
        );

        Alert savedAlert = alertRepository.save(alert);

        // Find users subscribed to this alert type within the notification radius.
        List<UUID> subscribedUsers = subscriptionRepository
            .findUsersSubscribedToAlertType(command.type(), location, getNotificationRadius(command.severity()));

        List<UUID> nearbyUsers = userRepository.findUsersNearLocation(location);
        Set<UUID> allUsers = new HashSet<>(subscribedUsers);
        allUsers.addAll(nearbyUsers);

        if (!allUsers.isEmpty()) {
            notificationPort.broadcastAlert(savedAlert, new ArrayList<>(allUsers));
        }

        return savedAlert;
    }

    private double getNotificationRadius(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 10.0;
            case HIGH -> 5.0;
            case MEDIUM -> 2.0;
            case LOW -> 1.0;
        };
    }
}
