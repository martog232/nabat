package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserSubscriptionJpaRepository extends JpaRepository<UserSubscriptionJpaEntity, UUID> {

    List<UserSubscriptionJpaEntity> findByUserId(UUID userId);

    /**
     * Distinct subscriber userIds whose active subscription matches the alert type and whose
     * radius (combined with the alert's own radius) covers the alert location.
     * PostGIS path — used when the center_geog generated column exists.
     */
    @Query(value = """
        SELECT DISTINCT s.user_id FROM user_subscriptions s
        WHERE s.is_active = true
          AND s.alert_type = :#{#type.name()}
          AND ST_DWithin(
              s.center_geog,
              ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
              (s.radius_km + :radiusKm) * 1000.0
          )
        """, nativeQuery = true)
    List<UUID> findUserIdsMatching(
            @Param("type") AlertType type,
            @Param("lat") double latitude,
            @Param("lon") double longitude,
            @Param("radiusKm") double radiusKm
    );

    /** Haversine fallback — used when PostGIS is not installed on the server. */
    @Query(value = """
        SELECT DISTINCT s.user_id FROM user_subscriptions s
        WHERE s.is_active = true
          AND s.alert_type = :#{#type.name()}
          AND (6371 * acos(
              LEAST(1.0, cos(radians(:lat)) * cos(radians(s.latitude))
              * cos(radians(s.longitude) - radians(:lon))
              + sin(radians(:lat)) * sin(radians(s.latitude)))
          )) <= (s.radius_km + :radiusKm)
        """, nativeQuery = true)
    List<UUID> findUserIdsMatchingHaversine(
            @Param("type") AlertType type,
            @Param("lat") double latitude,
            @Param("lon") double longitude,
            @Param("radiusKm") double radiusKm
    );
}

