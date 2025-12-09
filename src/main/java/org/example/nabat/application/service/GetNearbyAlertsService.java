package org.example.nabat.application.service;

import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.GetNearbyAlertsUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.Location;

import java.util.List;

@UseCase
public class GetNearbyAlertsService implements GetNearbyAlertsUseCase {

    private final AlertRepository alertRepository;

    public GetNearbyAlertsService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    public List<Alert> getNearbyAlerts(NearbyAlertsQuery query) {
        Location userLocation = Location.of(query. latitude(), query.longitude());
        return alertRepository.findActiveAlertsWithinRadius(userLocation, query.radiusKm());
    }
}
