package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.shared.domain.Location;

import java.time.Instant;
import java.util.List;

public interface GetNearbyAlertsUseCase {

    List<Alert> getNearbyAlerts(NearbyAlertsQuery query);

    /**
     * Alerts created at or after {@code since} within the query circle.
     *
     * <p>Used by clients catching up after a dropped WebSocket connection. Not cached:
     * {@code since} is effectively unique per call, so caching would only fill Redis
     * with entries that are never read again.
     */
    List<Alert> getAlertsSince(NearbyAlertsQuery query, Instant since);

    record NearbyAlertsQuery(
        double latitude,
        double longitude,
        double radiusKm
    ) {
        /** Cache keys are built from coordinates rounded to this many decimal places. */
        private static final double CACHE_GRID_DECIMALS = 1_000d; // ~110 m at the equator

        public NearbyAlertsQuery {
            if (radiusKm <= 0 || radiusKm > 100) {
                throw new IllegalArgumentException("Radius must be between 0 and 100 km");
            }
            // Rejects NaN/out-of-range coordinates at the boundary rather than deeper in.
            Location.of(latitude, longitude);
        }

        public Location center() {
            return Location.of(latitude, longitude);
        }

        /**
         * Cache key that groups nearby requests onto a coarse grid.
         *
         * <p>The key used to be the raw coordinates concatenated, which made it
         * effectively unique per request: GPS jitter and every map pan minted a fresh
         * Redis entry, so the hit rate was near zero while the cache filled with
         * single-use values. Rounding to ~110 m means requests from the same
         * neighbourhood share an entry, which is the point of a 15-second cache.
         */
        public String cacheKey() {
            return quantize(latitude) + "_" + quantize(longitude) + "_" + radiusKm;
        }

        private static long quantize(double coordinate) {
            return Math.round(coordinate * CACHE_GRID_DECIMALS);
        }
    }
}
