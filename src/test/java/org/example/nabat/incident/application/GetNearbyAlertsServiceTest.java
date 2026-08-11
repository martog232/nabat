package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.in.GetNearbyAlertsUseCase.NearbyAlertsQuery;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.AlertRepository.NearbySearch;
import org.example.nabat.incident.domain.Alert;
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
                0, 0, 0,

                0,
                null,
                null
        );
    }

    @Test
    void shouldReturnNearbyAlertsWithinRadius() {
        List<Alert> expectedAlerts = List.of(buildAlert(42.0, 23.0));
        when(alertRepository.findActiveAlertsWithinRadius(any(NearbySearch.class)))
                .thenReturn(expectedAlerts);

        NearbyAlertsQuery query = NearbyAlertsQuery.of(42.0, 23.0, 5.0);
        List<Alert> result = getNearbyAlertsService.getNearbyAlerts(query);

        assertEquals(expectedAlerts, result);
        verify(alertRepository).findActiveAlertsWithinRadius(any(NearbySearch.class));
    }

    @Test
    void shouldReturnEmptyListWhenNoNearbyAlerts() {
        when(alertRepository.findActiveAlertsWithinRadius(any(NearbySearch.class)))
                .thenReturn(Collections.emptyList());

        NearbyAlertsQuery query = NearbyAlertsQuery.of(42.0, 23.0, 5.0);
        List<Alert> result = getNearbyAlertsService.getNearbyAlerts(query);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPassCorrectLocationAndRadiusToRepository() {
        when(alertRepository.findActiveAlertsWithinRadius(any(NearbySearch.class)))
                .thenReturn(Collections.emptyList());

        NearbyAlertsQuery query = NearbyAlertsQuery.of(51.5, -0.12, 10.0);
        getNearbyAlertsService.getNearbyAlerts(query);

        ArgumentCaptor<NearbySearch> searchCaptor = ArgumentCaptor.forClass(NearbySearch.class);
        verify(alertRepository).findActiveAlertsWithinRadius(searchCaptor.capture());

        NearbySearch search = searchCaptor.getValue();
        assertEquals(51.5, search.center().latitude());
        assertEquals(-0.12, search.center().longitude());
        assertEquals(10.0, search.radiusKm());
    }

    @Test
    void shouldCarryFiltersAndLimitThroughToTheRepository() {
        when(alertRepository.findActiveAlertsWithinRadius(any(NearbySearch.class)))
                .thenReturn(Collections.emptyList());

        var query = new NearbyAlertsQuery(42.0, 23.0, 5.0, AlertType.FIRE, AlertSeverity.CRITICAL, 25);
        getNearbyAlertsService.getNearbyAlerts(query);

        ArgumentCaptor<NearbySearch> searchCaptor = ArgumentCaptor.forClass(NearbySearch.class);
        verify(alertRepository).findActiveAlertsWithinRadius(searchCaptor.capture());

        NearbySearch search = searchCaptor.getValue();
        // Dropping any of these on the way down would silently widen the query — the
        // caller asked for 25 critical fires and would get every alert in the circle.
        assertEquals(AlertType.FIRE, search.type());
        assertEquals(AlertSeverity.CRITICAL, search.severity());
        assertEquals(25, search.limit());
    }

    /**
     * The cached value is keyed on this string, so anything that narrows the result set
     * has to be part of it. Without the filters in the key, a request for critical fires
     * would be served whatever an unfiltered request from the same grid square cached
     * moments earlier — wrong alerts on a safety map, and only under mixed traffic.
     */
    @Test
    void cacheKeyDistinguishesQueriesThatReturnDifferentResults() {
        var unfiltered = NearbyAlertsQuery.of(42.0, 23.0, 5.0);
        var byType = new NearbyAlertsQuery(42.0, 23.0, 5.0, AlertType.FIRE, null, 100);
        var bySeverity = new NearbyAlertsQuery(42.0, 23.0, 5.0, null, AlertSeverity.CRITICAL, 100);
        var smallerLimit = new NearbyAlertsQuery(42.0, 23.0, 5.0, null, null, 10);

        assertNotEquals(unfiltered.cacheKey(), byType.cacheKey());
        assertNotEquals(unfiltered.cacheKey(), bySeverity.cacheKey());
        assertNotEquals(unfiltered.cacheKey(), smallerLimit.cacheKey());
        assertNotEquals(byType.cacheKey(), bySeverity.cacheKey());
    }

    /** The grid rounding is the whole reason the cache has a usable hit rate. */
    @Test
    void cacheKeyStillGroupsNeighbouringCoordinates() {
        var here = NearbyAlertsQuery.of(42.6977, 23.3219, 5.0);
        var aFewMetresAway = NearbyAlertsQuery.of(42.69772, 23.32188, 5.0);

        assertEquals(here.cacheKey(), aFewMetresAway.cacheKey());
    }

    @Test
    void rejectsALimitAboveTheCeiling() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new NearbyAlertsQuery(42.0, 23.0, 5.0, null, null, NearbyAlertsQuery.MAX_LIMIT + 1)
        );
    }

    @Test
    void rejectsANonPositiveLimit() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new NearbyAlertsQuery(42.0, 23.0, 5.0, null, null, 0)
        );
    }
}
