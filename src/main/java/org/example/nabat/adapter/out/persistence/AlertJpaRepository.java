package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertJpaRepository extends JpaRepository<AlertJpaEntity, UUID> {

    interface VoteStatsProjection {
        int getUpvoteCount();

        int getDownvoteCount();

        int getConfirmationCount();

        int getCredibilityScore();
    }

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

    @Query("""
        SELECT a.upvoteCount AS upvoteCount,
               a.downvoteCount AS downvoteCount,
               a.confirmationCount AS confirmationCount,
               a.credibilityScore AS credibilityScore
        FROM AlertJpaEntity a
        WHERE a.id = :id
        """)
    Optional<VoteStatsProjection> findVoteStatsById(@Param("id") UUID id);

    @Modifying
    @Query("""
        UPDATE AlertJpaEntity a
        SET a.upvoteCount = :upvotes,
            a.downvoteCount = :downvotes,
            a.confirmationCount = :confirmations,
            a.credibilityScore = :credibilityScore
        WHERE a.id = :id
        """)
    void updateVoteCounts(
            @Param("id") UUID id,
            @Param("upvotes") int upvotes,
            @Param("downvotes") int downvotes,
            @Param("confirmations") int confirmations,
            @Param("credibilityScore") int credibilityScore
    );

}
