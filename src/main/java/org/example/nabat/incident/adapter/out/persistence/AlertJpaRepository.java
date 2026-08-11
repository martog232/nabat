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

    /*
     * The four spatial queries below share a shape: circle predicate, optional type and
     * severity filters, newest first, LIMIT.
     *
     * On the filters — `CAST(:type AS text) IS NULL OR ...` rather than the obvious
     * `:type IS NULL OR ...`. PostgreSQL infers parameter types from context, and in a
     * bare `NULL` comparison there is no context, so it fails the statement with
     * "could not determine data type of parameter". The cast supplies the type.
     * `a.type` is compared as text because the column is a varchar holding the enum name.
     *
     * On the LIMIT — it belongs here rather than in Java. Trimming the list after the
     * fact would still make the database materialise and ship every row in the radius,
     * which is exactly the cost this is meant to avoid. Combined with ORDER BY
     * created_at DESC it means a truncated response drops the *oldest* matches, which is
     * the right end to lose for a live incident map.
     */

    /** PostGIS path — used when the location_geog generated column exists. */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status = 'ACTIVE'
        AND (CAST(:type AS text) IS NULL OR a.type = CAST(:type AS text))
        AND (CAST(:severity AS text) IS NULL OR a.severity = CAST(:severity AS text))
        AND ST_DWithin(
            a.location_geog,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
            :radius * 1000.0
        )
        ORDER BY a.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadius(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("type") String type,
        @Param("severity") String severity,
        @Param("limit") int limit
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
        AND (CAST(:type AS text) IS NULL OR a.type = CAST(:type AS text))
        AND (CAST(:severity AS text) IS NULL OR a.severity = CAST(:severity AS text))
        AND ST_DWithin(
            a.location_geog,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
            :radius * 1000.0
        )
        ORDER BY a.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadiusSince(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("since") Instant since,
        @Param("type") String type,
        @Param("severity") String severity,
        @Param("limit") int limit
    );

    /** Haversine equivalent of {@link #findActiveAlertsWithinRadiusSince}. */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status = 'ACTIVE'
        AND a.created_at >= :since
        AND (CAST(:type AS text) IS NULL OR a.type = CAST(:type AS text))
        AND (CAST(:severity AS text) IS NULL OR a.severity = CAST(:severity AS text))
        AND (6371 * acos(
            LEAST(1.0, cos(radians(:lat)) * cos(radians(a.latitude))
            * cos(radians(a.longitude) - radians(:lon))
            + sin(radians(:lat)) * sin(radians(a.latitude)))
        )) <= :radius
        ORDER BY a.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadiusSinceHaversine(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("since") Instant since,
        @Param("type") String type,
        @Param("severity") String severity,
        @Param("limit") int limit
    );

    /** Haversine fallback — used when PostGIS is not installed on the server. */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status = 'ACTIVE'
        AND (CAST(:type AS text) IS NULL OR a.type = CAST(:type AS text))
        AND (CAST(:severity AS text) IS NULL OR a.severity = CAST(:severity AS text))
        AND (6371 * acos(
            LEAST(1.0, cos(radians(:lat)) * cos(radians(a.latitude))
            * cos(radians(a.longitude) - radians(:lon))
            + sin(radians(:lat)) * sin(radians(a.latitude)))
        )) <= :radius
        ORDER BY a.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadiusHaversine(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("type") String type,
        @Param("severity") String severity,
        @Param("limit") int limit
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
