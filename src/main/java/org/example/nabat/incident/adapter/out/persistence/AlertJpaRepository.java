package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.incident.domain.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertJpaRepository extends JpaRepository<AlertJpaEntity, UUID> {

    List<AlertJpaEntity> findByStatus(AlertStatus status);

    /** PostGIS path — used when the location_geog generated column exists. */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status = 'ACTIVE'
        AND ST_DWithin(
            a.location_geog,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
            :radius * 1000.0
        )
        ORDER BY a.created_at DESC
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadius(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm
    );

    /**
     * PostGIS path, restricted to alerts created at or after {@code since}.
     * Backs the WebSocket reconnect catch-up, which previously passed a
     * {@code since} parameter the API silently ignored.
     */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status = 'ACTIVE'
        AND a.created_at >= :since
        AND ST_DWithin(
            a.location_geog,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
            :radius * 1000.0
        )
        ORDER BY a.created_at DESC
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadiusSince(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("since") Instant since
    );

    /** Haversine equivalent of {@link #findActiveAlertsWithinRadiusSince}. */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status = 'ACTIVE'
        AND a.created_at >= :since
        AND (6371 * acos(
            LEAST(1.0, cos(radians(:lat)) * cos(radians(a.latitude))
            * cos(radians(a.longitude) - radians(:lon))
            + sin(radians(:lat)) * sin(radians(a.latitude)))
        )) <= :radius
        ORDER BY a.created_at DESC
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadiusSinceHaversine(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("since") Instant since
    );

    /** Haversine fallback — used when PostGIS is not installed on the server. */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status = 'ACTIVE'
        AND (6371 * acos(
            LEAST(1.0, cos(radians(:lat)) * cos(radians(a.latitude))
            * cos(radians(a.longitude) - radians(:lon))
            + sin(radians(:lat)) * sin(radians(a.latitude)))
        )) <= :radius
        ORDER BY a.created_at DESC
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadiusHaversine(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm
    );

    /**
     * {@code flushAutomatically} so pending changes are not lost, and
     * {@code clearAutomatically} so a subsequent {@code findById} in the same
     * transaction re-reads from the database instead of returning the stale
     * first-level-cache copy. Without the latter, whether a caller saw the new
     * counts depended purely on whether it had already loaded the entity.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE AlertJpaEntity a
        SET a.upvoteCount = :upvotes,
            a.downvoteCount = :downvotes,
            a.confirmationCount = :confirmations,
            a.credibilityScore = :credibilityScore
        WHERE a.id = :id
        """)
    int updateVoteCounts(
            @Param("id") UUID id,
            @Param("upvotes") int upvotes,
            @Param("downvotes") int downvotes,
            @Param("confirmations") int confirmations,
            @Param("credibilityScore") int credibilityScore
    );

}
