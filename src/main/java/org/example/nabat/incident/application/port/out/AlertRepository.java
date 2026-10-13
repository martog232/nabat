package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ReportRanking;
import org.example.nabat.shared.domain.Location;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;

public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findById(AlertId id);

    List<Alert> findActiveAlertsWithinRadius(NearbySearch search);

    /**
     * The circle, the optional filters and the cap, as one value.
     *
     * <p>Grouped rather than passed as six positional arguments, and grouped <em>here</em>
     * rather than reusing the in-port's query record — an out-port that imports an in-port
     * would point the dependency backwards.
     *
     * @param category a group code or a subcategory code; null means every category
     * @param limit    hard cap on rows returned. Applied in SQL, not afterwards in Java:
     *                 trimming a list still makes the database materialise and ship every
     *                 row inside the radius, which is the cost being avoided.
     */
    record NearbySearch(
        Location center,
        double radiusKm,
        Integer category,
        int limit
    ) {
        public NearbySearch {
            if (limit <= 0) {
                throw new IllegalArgumentException("Limit must be positive");
            }
        }
    }

    List<Alert> findByStatus(AlertStatus status);

    /** A district's reports in any of {@code statuses}, newest first, at most {@code limit}. */
    List<Alert> findInDistrict(String districtCode, Set<AlertStatus> statuses, int limit);

    /** One page of the public list, in the order {@code search} asks for. */
    List<Alert> findRanked(RankedSearch search);

    /** How many reports {@code search} matches in all, ignoring its page. */
    long countRanked(RankedSearch search);

    /**
     * The public list's filters, order and page. Its own record rather than the in-port's
     * query, for the same reason as {@link NearbySearch}: an out-port that imports an in-port
     * points the dependency backwards.
     *
     * @param districtCode null for the whole city
     */
    record RankedSearch(String districtCode, Set<AlertStatus> statuses, ReportRanking ranking, int offset, int limit) {

        /** The status names the native query compares against. */
        public List<String> statusNames() {
            return statuses.stream().map(Enum::name).sorted().toList();
        }
    }

    /**
     * Writes the denormalised vote counts and returns the refreshed alert, in one
     * transaction.
     *
     * <p>Callers used to issue {@code updateVoteCounts} and then a separate
     * {@code findById} — twice, in fact, once for the notification and once for the
     * broadcast. Combining them removes two redundant reads per vote and guarantees
     * the returned alert reflects the counts just written.
     *
     * @return the updated alert, or empty when no alert has that id
     */
    Optional<Alert> applyVoteCounts(
        AlertId alertId, int upvotes, int downvotes, int confirmations, int credibilityScore);

    /**
     * Alerts created at or after {@code since} matching {@code search}, newest first.
     * Backs the WebSocket reconnect catch-up.
     */
    List<Alert> findActiveAlertsWithinRadiusSince(NearbySearch search, Instant since);
}
