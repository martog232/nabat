package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.Alert;

import java.util.List;

public interface GetNearbyAlertsUseCase {

    List<Alert> getNearbyAlerts(NearbyAlertsQuery query);

    record NearbyAlertsQuery(
        double latitude,
        double longitude,
        double radiusKm
    ) {
        public NearbyAlertsQuery {
            if (radiusKm <= 0 || radiusKm > 100) {
                throw new IllegalArgumentException("Radius must be between 0 and 100 km");
            }
        }
    }
}
