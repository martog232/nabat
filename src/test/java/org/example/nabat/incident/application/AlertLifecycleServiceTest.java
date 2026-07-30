package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.example.nabat.shared.domain.NotAuthorizedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertLifecycleServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private AlertNotificationPort alertNotificationPort;

    private AlertLifecycleService service;

    private final AlertId alertId = AlertId.generate();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AlertLifecycleService(alertRepository, alertNotificationPort);
    }

    private Alert active() {
        return new Alert(alertId, "T", "D", AlertType.FIRE, AlertSeverity.HIGH,
                Location.of(0, 0), Instant.now(), AlertStatus.ACTIVE,
                ownerId, 0, 0, 0,
        0, null, null);
    }

    private User user(UUID id, Role role) {
        return new User(UserId.of(id), "x@y.z", "p", "n", role, true, false, Instant.now(), Instant.now(),
                5, null, null, null,
        0);
    }

    @Test
    void getById_existing_returns() {
        Alert a = active();
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(a));
        assertSame(a, service.getById(alertId));
    }

    @Test
    void getById_missing_throws() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());
        assertThrows(AlertNotFoundException.class, () -> service.getById(alertId));
    }

    @Test
    void resolve_byOwner_succeeds() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Alert result = service.resolve(alertId, user(ownerId, Role.USER));

        assertEquals(AlertStatus.RESOLVED, result.status());
        assertNotNull(result.resolvedAt());
    }

    @Test
    void resolve_byAdmin_succeeds() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Alert result = service.resolve(alertId, user(UUID.randomUUID(), Role.ADMIN));

        assertEquals(AlertStatus.RESOLVED, result.status());
    }

    @Test
    void resolve_alreadyResolved_throwsIllegalState() {
        Alert resolved = new Alert(alertId, "T", "D", AlertType.FIRE, AlertSeverity.HIGH,
                Location.of(0, 0), Instant.now(), AlertStatus.RESOLVED,
                ownerId, 0, 0, 0,
        0, Instant.now(), null);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(resolved));

        assertThrows(IllegalStateException.class,
                () -> service.resolve(alertId, user(ownerId, Role.USER)));
        verify(alertRepository, never()).save(any());
    }

    @Test
    void resolve_byStranger_throwsAccessDenied() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        assertThrows(NotAuthorizedException.class,
                () -> service.resolve(alertId, user(UUID.randomUUID(), Role.USER)));
        verify(alertRepository, never()).save(any());
    }

    @Test
    void resolve_missing_throwsNotFound() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());
        assertThrows(AlertNotFoundException.class,
                () -> service.resolve(alertId, user(ownerId, Role.USER)));
    }
}
