package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.in.CreateAlertUseCase.CreateAlertCommand;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service now does two things: persist, and say so. Resolving the audience and
 * pushing to it moved to {@link NewAlertFanout}, which runs after the transaction
 * commits — see {@code NewAlertFanoutTest} for that half.
 */
@ExtendWith(MockitoExtension.class)
class CreateAlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private ApplicationEventPublisher events;

    private CreateAlertService createAlertService;

    @BeforeEach
    void setUp() {
        createAlertService = new CreateAlertService(alertRepository, events);
    }

    private static Alert alertReportedBy(UUID reportedBy) {
        return new Alert(
            AlertId.generate(),
            "Test Alert",
            "Test description",
            AlertType.CRIME,
            AlertSeverity.HIGH,
            Location.of(42.0, 23.0),
            Instant.now(),
            AlertStatus.ACTIVE,
            reportedBy,
            0, 0, 0, 0,
            null,
            null
        );
    }

    private static CreateAlertCommand command(UUID reportedBy) {
        return new CreateAlertCommand(
            "Test Alert", "Description", AlertType.FIRE, AlertSeverity.HIGH, 42.0, 23.0, reportedBy, null
        );
    }

    @Test
    void savesTheAlertAndReturnsWhatWasPersisted() {
        UUID reportedBy = UUID.randomUUID();
        Alert saved = alertReportedBy(reportedBy);
        when(alertRepository.save(any(Alert.class))).thenReturn(saved);

        Alert result = createAlertService.createAlert(command(reportedBy));

        assertSame(saved, result);
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void publishesAlertCreatedCarryingThePersistedAlert() {
        UUID reportedBy = UUID.randomUUID();
        Alert saved = alertReportedBy(reportedBy);
        when(alertRepository.save(any(Alert.class))).thenReturn(saved);

        createAlertService.createAlert(command(reportedBy));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());

        // The saved instance, not the one handed to save(): the repository assigns
        // persistence-managed values, and the fan-out must describe what is in the
        // database rather than what was proposed.
        assertEquals(new AlertCreated(saved), captor.getValue());
    }

    @Test
    void buildsTheAlertFromTheCommand() {
        UUID reportedBy = UUID.randomUUID();
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));

        createAlertService.createAlert(command(reportedBy));

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert proposed = captor.getValue();

        assertEquals("Test Alert", proposed.title());
        assertEquals(AlertType.FIRE, proposed.type());
        assertEquals(AlertSeverity.HIGH, proposed.severity());
        assertEquals(Location.of(42.0, 23.0), proposed.location());
        assertEquals(reportedBy, proposed.reportedBy());
    }
}
