package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.in.CreateAlertUseCase.CreateAlertCommand;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.DistrictLookupPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.incident.domain.OutsideServiceAreaException;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The service does three things: find the district, persist, and say so. Pushing the new
 * report moved to {@link NewAlertFanout}, which runs after the transaction commits — see
 * {@code NewAlertFanoutTest} for that half.
 */
@ExtendWith(MockitoExtension.class)
class CreateAlertServiceTest {

    /** Oborishte, Sofia — anywhere inside the city will do; the lookup is mocked. */
    private static final double LAT = 42.6977;
    private static final double LON = 23.3219;

    private static final DistrictLookupPort.District LOZENETS =
        new DistrictLookupPort.District("LOZENETS", "Район Лозенец");

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private DistrictLookupPort districts;

    @Mock
    private ApplicationEventPublisher events;

    private CreateAlertService createAlertService;

    @BeforeEach
    void setUp() {
        createAlertService = new CreateAlertService(alertRepository, districts, events);
    }

    private void insideLozenets() {
        when(districts.districtAt(Location.of(LAT, LON))).thenReturn(Optional.of(LOZENETS));
    }

    private static Alert alertReportedBy(UUID reportedBy) {
        return new Alert(
            AlertId.generate(),
            "Test Alert",
            "Test description",
            AlertType.WASTE,
            Location.of(LAT, LON),
            Instant.now(),
            AlertStatus.SUBMITTED,
            reportedBy,
            0, 0, 0, 0,
            null,
            null, "LOZENETS"
        );
    }

    private static CreateAlertCommand command(UUID reportedBy) {
        return new CreateAlertCommand(
            "Test Alert", "Description", AlertType.POTHOLE, LAT, LON, reportedBy, null
        );
    }

    @Test
    void savesTheAlertAndReturnsWhatWasPersisted() {
        insideLozenets();
        UUID reportedBy = UUID.randomUUID();
        Alert saved = alertReportedBy(reportedBy);
        when(alertRepository.save(any(Alert.class))).thenReturn(saved);

        Alert result = createAlertService.createAlert(command(reportedBy));

        assertSame(saved, result);
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void publishesAlertCreatedCarryingThePersistedAlert() {
        insideLozenets();
        UUID reportedBy = UUID.randomUUID();
        Alert saved = alertReportedBy(reportedBy);
        when(alertRepository.save(any(Alert.class))).thenReturn(saved);

        createAlertService.createAlert(command(reportedBy));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());

        // The saved instance, not the one handed to save(): the repository assigns
        // persistence-managed values, and the push must describe what is in the
        // database rather than what was proposed.
        assertEquals(new AlertCreated(saved), captor.getValue());
    }

    @Test
    void buildsTheAlertFromTheCommandInTheDistrictItFallsIn() {
        insideLozenets();
        UUID reportedBy = UUID.randomUUID();
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));

        createAlertService.createAlert(command(reportedBy));

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());
        Alert proposed = captor.getValue();

        assertEquals("Test Alert", proposed.title());
        assertEquals(AlertType.POTHOLE, proposed.type());
        assertEquals(Location.of(LAT, LON), proposed.location());
        assertEquals(reportedBy, proposed.reportedBy());
        assertEquals("LOZENETS", proposed.districtCode());
    }

    /**
     * Refused before anything is written or announced: a report nobody will receive must
     * not be stored as "submitted", and must not appear on anyone's map.
     */
    @Test
    void refusesAReportOutsideEveryDistrict() {
        when(districts.districtAt(any(Location.class))).thenReturn(Optional.empty());

        assertThrows(
            OutsideServiceAreaException.class,
            () -> createAlertService.createAlert(command(UUID.randomUUID()))
        );

        verifyNoInteractions(alertRepository, events);
    }
}
