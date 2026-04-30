package org.example.nabat.application.service;

import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.domain.exception.AlertNotFoundException;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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

    private AlertLifecycleService service;

    private final AlertId alertId = AlertId.generate();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AlertLifecycleService(alertRepository);
    }

    private Alert active() {
        return new Alert(alertId, "T", "D", AlertType.FIRE, AlertSeverity.HIGH,
                Location.of(0, 0), Instant.now(), AlertStatus.ACTIVE,
                ownerId, 0, 0, 0, null);
    }

    private User user(UUID id, Role role) {
        return new User(UserId.of(id), "x@y.z", "p", "n", role, true, Instant.now(), Instant.now());
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
    void resolve_byStranger_throwsAccessDenied() {
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(active()));
        assertThrows(AccessDeniedException.class,
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

