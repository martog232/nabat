package org.example.nabat.application.service;

import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.CreateAlertUseCase;
import org.example.nabat.application.port.out.AlertNotificationPort;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.UserSubscriptionRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.Location;

import java.util.List;
import java.util.UUID;

@UseCase
public class CreateAlertService implements CreateAlertUseCase {

    private final AlertRepository alertRepository;
    private final AlertNotificationPort notificationPort;
    private final UserSubscriptionRepository subscriptionRepository;

    public CreateAlertService(
        AlertRepository alertRepository,
        AlertNotificationPort notificationPort,
        UserSubscriptionRepository subscriptionRepository
    ) {
        this.alertRepository = alertRepository;
        this.notificationPort = notificationPort;
        this.subscriptionRepository = subscriptionRepository;
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

        // Намери всички потребители, абонирани за този тип alert в радиуса
        List<UUID> subscribedUsers = subscriptionRepository
            .findUsersSubscribedToAlertType(command.type(), location, getNotificationRadius(command.severity()));

        if (!subscribedUsers.isEmpty()) {
            notificationPort.broadcastAlert(savedAlert, subscribedUsers);
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
