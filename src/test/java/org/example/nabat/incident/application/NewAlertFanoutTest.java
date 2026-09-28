package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.out.AlertAudiencePort;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The fan-out half of alert creation, which used to live inside
 * {@code CreateAlertService.createAlert} and inside its transaction.
 */
@ExtendWith(MockitoExtension.class)
class NewAlertFanoutTest {

    @Mock
    private AlertAudiencePort audiencePort;

    @Mock
    private AlertNotificationPort notificationPort;

    private NewAlertFanout fanout() {
        return new NewAlertFanout(audiencePort, notificationPort);
    }

    private static Alert alert(UUID reportedBy) {
        return new Alert(
            AlertId.generate(),
            "Test Alert",
            "Test description",
            AlertType.WASTE,
            Location.of(42.0, 23.0),
            Instant.now(),
            AlertStatus.SUBMITTED,
            reportedBy,
            0, 0, 0, 0,
            null,
            null
        );
    }

    @Test
    void broadcastsToEveryoneTheAudiencePortResolves() {
        Alert alert = alert(UUID.randomUUID());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(audiencePort.recipientsFor(any(), any(), anyDouble())).thenReturn(List.of(first, second));

        fanout().on(new AlertCreated(alert));

        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPort).broadcastAlert(eq(alert), captor.capture());
        assertEquals(Set.of(first, second), new HashSet<>(captor.getValue()));
    }

    @Test
    void doesNotBroadcastToTheReporter() {
        UUID reporter = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        Alert alert = alert(reporter);
        when(audiencePort.recipientsFor(any(), any(), anyDouble()))
            .thenReturn(List.of(reporter, someoneElse));

        fanout().on(new AlertCreated(alert));

        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPort).broadcastAlert(eq(alert), captor.capture());
        assertEquals(Set.of(someoneElse), new HashSet<>(captor.getValue()));
    }

    @Test
    void doesNotBroadcastWhenNobodyIsInterested() {
        Alert alert = alert(UUID.randomUUID());
        when(audiencePort.recipientsFor(any(), any(), anyDouble())).thenReturn(List.of());

        fanout().on(new AlertCreated(alert));

        verify(notificationPort, never()).broadcastAlert(any(), any());
    }

    @Test
    void doesNotBroadcastWhenTheReporterIsTheOnlyRecipient() {
        UUID reporter = UUID.randomUUID();
        Alert alert = alert(reporter);
        when(audiencePort.recipientsFor(any(), any(), anyDouble())).thenReturn(List.of(reporter));

        fanout().on(new AlertCreated(alert));

        verify(notificationPort, never()).broadcastAlert(any(), any());
    }

    /** One radius for every report: the severity-driven scale described danger, not proximity. */
    @Test
    void appliesTheSameBroadcastRadiusToEveryReport() {
        Alert alert = alert(UUID.randomUUID());
        when(audiencePort.recipientsFor(any(), any(), anyDouble())).thenReturn(List.of());

        fanout().on(new AlertCreated(alert));

        ArgumentCaptor<Double> radiusCaptor = ArgumentCaptor.forClass(Double.class);
        verify(audiencePort).recipientsFor(eq(alert.type()), eq(alert.location()), radiusCaptor.capture());
        assertEquals(NewAlertFanout.BROADCAST_RADIUS_KM, radiusCaptor.getValue());
    }
}
