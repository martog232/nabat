package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertType;
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

    /**
     * @param type     optional filter; null means every type. Likewise {@code severity}.
     * @param limit    maximum alerts to return. There is always one — an unbounded result
     *                 is a denial of service that a legitimate client can trigger by
     *                 asking for a 100 km radius over a dense city.
     */
    record NearbyAlertsQuery(
        double latitude,
        double longitude,
        double radiusKm,
        AlertType type,
        AlertSeverity severity,
        int limit
    ) {
        /** Cache keys are built from coordinates rounded to this many decimal places. */
        private static final double CACHE_GRID_DECIMALS = 1_000d; // ~110 m at the equator

        /** Applied when a caller does not choose one. */
        public static final int DEFAULT_LIMIT = 100;

        /** Ceiling on what a caller may ask for, whatever they send. */
        public static final int MAX_LIMIT = 500;

        public NearbyAlertsQuery {
            if (radiusKm <= 0 || radiusKm > 100) {
                throw new IllegalArgumentException("Radius must be between 0 and 100 km");
            }
            if (limit <= 0 || limit > MAX_LIMIT) {
                throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
            }
            // Rejects NaN/out-of-range coordinates at the boundary rather than deeper in.
            Location.of(latitude, longitude);
        }

        /** An unfiltered query at the default limit — the common case. */
        public static NearbyAlertsQuery of(double latitude, double longitude, double radiusKm) {
            return new NearbyAlertsQuery(latitude, longitude, radiusKm, null, null, DEFAULT_LIMIT);
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
         *
         * <p><strong>Every field that changes the result set must appear here.</strong>
         * The filters and the limit narrow the rows returned, so leaving them out would
         * let a request for {@code type=FIRE&limit=10} be served the cached response of an
         * unfiltered one from the same grid square — wrong alerts on a safety map, and
         * invisible except under concurrent traffic with mixed filters.
         */
        public String cacheKey() {
            return quantize(latitude) + "_" + quantize(longitude) + "_" + radiusKm
                + "_" + (type == null ? "*" : type.name())
                + "_" + (severity == null ? "*" : severity.name())
                + "_" + limit;
        }

        private static long quantize(double coordinate) {
            return Math.round(coordinate * CACHE_GRID_DECIMALS);
        }
    }
}
