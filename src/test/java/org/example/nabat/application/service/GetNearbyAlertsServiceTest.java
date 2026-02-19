package org.example.nabat.application.service;

import org.example.nabat.application.port.in.GetNearbyAlertsUseCase.NearbyAlertsQuery;
import org.example.nabat.application.port.out.AlertRepository;
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
class GetNearbyAlertsServiceTest {

    @Mock
    private AlertRepository alertRepository;

    private GetNearbyAlertsService getNearbyAlertsService;

    @BeforeEach
    void setUp() {
        getNearbyAlertsService = new GetNearbyAlertsService(alertRepository);
    }

    private Alert buildAlert(double lat, double lon) {
        return new Alert(
                AlertId.generate(),
                "Nearby Alert",
                "Some description",
                AlertType.FIRE,
                AlertSeverity.HIGH,
                Location.of(lat, lon),
                Instant.now(),
                AlertStatus.ACTIVE,
                UUID.randomUUID(),
                0, 0, 0
        );
    }

    @Test
    void shouldReturnNearbyAlertsWithinRadius() {
        List<Alert> expectedAlerts = List.of(buildAlert(42.0, 23.0));
        when(alertRepository.findActiveAlertsWithinRadius(any(Location.class), anyDouble()))
                .thenReturn(expectedAlerts);

        NearbyAlertsQuery query = new NearbyAlertsQuery(42.0, 23.0, 5.0);
        List<Alert> result = getNearbyAlertsService.getNearbyAlerts(query);

        assertEquals(expectedAlerts, result);
        verify(alertRepository).findActiveAlertsWithinRadius(any(Location.class), eq(5.0));
    }

    @Test
    void shouldReturnEmptyListWhenNoNearbyAlerts() {
        when(alertRepository.findActiveAlertsWithinRadius(any(Location.class), anyDouble()))
                .thenReturn(Collections.emptyList());

        NearbyAlertsQuery query = new NearbyAlertsQuery(42.0, 23.0, 5.0);
        List<Alert> result = getNearbyAlertsService.getNearbyAlerts(query);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPassCorrectLocationAndRadiusToRepository() {
        when(alertRepository.findActiveAlertsWithinRadius(any(Location.class), anyDouble()))
                .thenReturn(Collections.emptyList());

        NearbyAlertsQuery query = new NearbyAlertsQuery(51.5, -0.12, 10.0);
        getNearbyAlertsService.getNearbyAlerts(query);

        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        ArgumentCaptor<Double> radiusCaptor = ArgumentCaptor.forClass(Double.class);
        verify(alertRepository).findActiveAlertsWithinRadius(locationCaptor.capture(), radiusCaptor.capture());

        assertEquals(51.5, locationCaptor.getValue().latitude());
        assertEquals(-0.12, locationCaptor.getValue().longitude());
        assertEquals(10.0, radiusCaptor.getValue());
    }
}
