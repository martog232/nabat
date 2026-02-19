package org.example.nabat.application.service;

import org.example.nabat.application.port.in.CreateAlertUseCase.CreateAlertCommand;
import org.example.nabat.application.port.out.AlertNotificationPort;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.UserSubscriptionRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertNotificationPort notificationPort;

    @Mock
    private UserSubscriptionRepository subscriptionRepository;

    private CreateAlertService createAlertService;

    @BeforeEach
    void setUp() {
        createAlertService = new CreateAlertService(alertRepository, notificationPort, subscriptionRepository);
    }

    private Alert buildAlert(AlertSeverity severity) {
        return new Alert(
                AlertId.generate(),
                "Test Alert",
                "Test description",
                AlertType.CRIME,
                severity,
                Location.of(42.0, 23.0),
                Instant.now(),
                AlertStatus.ACTIVE,
                UUID.randomUUID(),
                0, 0, 0
        );
    }

    @Test
    void shouldCreateAlertAndSaveToRepository() {
        UUID reportedBy = UUID.randomUUID();
        CreateAlertCommand command = new CreateAlertCommand(
                "Test Alert", "Description", AlertType.FIRE, AlertSeverity.HIGH, 42.0, 23.0, reportedBy
        );

        Alert savedAlert = buildAlert(AlertSeverity.HIGH);
        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);
        when(subscriptionRepository.findUsersSubscribedToAlertType(any(), any(), anyDouble()))
                .thenReturn(Collections.emptyList());

        Alert result = createAlertService.createAlert(command);

        assertNotNull(result);
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void shouldBroadcastNotificationToSubscribedUsers() {
        UUID reportedBy = UUID.randomUUID();
        CreateAlertCommand command = new CreateAlertCommand(
                "Test Alert", "Description", AlertType.CRIME, AlertSeverity.MEDIUM, 42.0, 23.0, reportedBy
        );

        Alert savedAlert = buildAlert(AlertSeverity.MEDIUM);
        List<UUID> subscribers = List.of(UUID.randomUUID(), UUID.randomUUID());

        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);
        when(subscriptionRepository.findUsersSubscribedToAlertType(any(), any(), anyDouble()))
                .thenReturn(subscribers);

        createAlertService.createAlert(command);

        verify(notificationPort).broadcastAlert(eq(savedAlert), eq(subscribers));
    }

    @Test
    void shouldNotBroadcastWhenNoUsersSubscribed() {
        UUID reportedBy = UUID.randomUUID();
        CreateAlertCommand command = new CreateAlertCommand(
                "Test Alert", "Description", AlertType.ACCIDENT, AlertSeverity.LOW, 42.0, 23.0, reportedBy
        );

        Alert savedAlert = buildAlert(AlertSeverity.LOW);
        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);
        when(subscriptionRepository.findUsersSubscribedToAlertType(any(), any(), anyDouble()))
                .thenReturn(Collections.emptyList());

        createAlertService.createAlert(command);

        verify(notificationPort, never()).broadcastAlert(any(), any());
    }

    @Test
    void shouldUseCriticalRadiusOf10Km() {
        UUID reportedBy = UUID.randomUUID();
        CreateAlertCommand command = new CreateAlertCommand(
                "Critical Alert", "Description", AlertType.FIRE, AlertSeverity.CRITICAL, 42.0, 23.0, reportedBy
        );

        Alert savedAlert = buildAlert(AlertSeverity.CRITICAL);
        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);
        when(subscriptionRepository.findUsersSubscribedToAlertType(any(), any(), anyDouble()))
                .thenReturn(Collections.emptyList());

        createAlertService.createAlert(command);

        ArgumentCaptor<Double> radiusCaptor = ArgumentCaptor.forClass(Double.class);
        verify(subscriptionRepository).findUsersSubscribedToAlertType(any(), any(), radiusCaptor.capture());
        assertEquals(10.0, radiusCaptor.getValue());
    }

    @Test
    void shouldUseHighRadiusOf5Km() {
        UUID reportedBy = UUID.randomUUID();
        CreateAlertCommand command = new CreateAlertCommand(
                "High Alert", "Description", AlertType.CRIME, AlertSeverity.HIGH, 42.0, 23.0, reportedBy
        );

        Alert savedAlert = buildAlert(AlertSeverity.HIGH);
        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);
        when(subscriptionRepository.findUsersSubscribedToAlertType(any(), any(), anyDouble()))
                .thenReturn(Collections.emptyList());

        createAlertService.createAlert(command);

        ArgumentCaptor<Double> radiusCaptor = ArgumentCaptor.forClass(Double.class);
        verify(subscriptionRepository).findUsersSubscribedToAlertType(any(), any(), radiusCaptor.capture());
        assertEquals(5.0, radiusCaptor.getValue());
    }

    @Test
    void shouldUseMediumRadiusOf2Km() {
        UUID reportedBy = UUID.randomUUID();
        CreateAlertCommand command = new CreateAlertCommand(
                "Medium Alert", "Description", AlertType.ACCIDENT, AlertSeverity.MEDIUM, 42.0, 23.0, reportedBy
        );

        Alert savedAlert = buildAlert(AlertSeverity.MEDIUM);
        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);
        when(subscriptionRepository.findUsersSubscribedToAlertType(any(), any(), anyDouble()))
                .thenReturn(Collections.emptyList());

        createAlertService.createAlert(command);

        ArgumentCaptor<Double> radiusCaptor = ArgumentCaptor.forClass(Double.class);
        verify(subscriptionRepository).findUsersSubscribedToAlertType(any(), any(), radiusCaptor.capture());
        assertEquals(2.0, radiusCaptor.getValue());
    }

    @Test
    void shouldUseLowRadiusOf1Km() {
        UUID reportedBy = UUID.randomUUID();
        CreateAlertCommand command = new CreateAlertCommand(
                "Low Alert", "Description", AlertType.MISSING_PERSON, AlertSeverity.LOW, 42.0, 23.0, reportedBy
        );

        Alert savedAlert = buildAlert(AlertSeverity.LOW);
        when(alertRepository.save(any(Alert.class))).thenReturn(savedAlert);
        when(subscriptionRepository.findUsersSubscribedToAlertType(any(), any(), anyDouble()))
                .thenReturn(Collections.emptyList());

        createAlertService.createAlert(command);

        ArgumentCaptor<Double> radiusCaptor = ArgumentCaptor.forClass(Double.class);
        verify(subscriptionRepository).findUsersSubscribedToAlertType(any(), any(), radiusCaptor.capture());
        assertEquals(1.0, radiusCaptor.getValue());
    }
}
