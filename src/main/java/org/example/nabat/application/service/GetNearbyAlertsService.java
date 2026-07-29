package org.example.nabat.application.service;

import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.GetNearbyAlertsUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.domain.model.Alert;
import org.springframework.cache.annotation.Cacheable;

import java.time.Instant;
import java.util.List;

@UseCase
public class GetNearbyAlertsService implements GetNearbyAlertsUseCase {

    private final AlertRepository alertRepository;

    public GetNearbyAlertsService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * {@code sync = true} so that a cache miss on a hot key does not let every
     * concurrent request through to PostGIS at once (a cache stampede); the first
     * caller populates the entry and the rest wait for it.
     */
    @Override
    @Cacheable(cacheNames = "nearbyAlerts", key = "#query.cacheKey()", sync = true)
    public List<Alert> getNearbyAlerts(NearbyAlertsQuery query) {
        return alertRepository.findActiveAlertsWithinRadius(query.center(), query.radiusKm());
    }

    @Override
    public List<Alert> getAlertsSince(NearbyAlertsQuery query, Instant since) {
        return alertRepository.findActiveAlertsWithinRadiusSince(query.center(), query.radiusKm(), since);
    }
}
