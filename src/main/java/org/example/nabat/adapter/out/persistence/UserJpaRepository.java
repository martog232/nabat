package org.example.nabat.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query(value = """
        SELECT u.id FROM users u
        WHERE u.last_known_lat IS NOT NULL
          AND u.last_known_lng IS NOT NULL
          AND u.location_updated_at > NOW() - INTERVAL '24 hours'
          AND ST_DWithin(
                ST_MakePoint(u.last_known_lng, u.last_known_lat)::geography,
                ST_MakePoint(:alertLng, :alertLat)::geography,
                u.notification_radius_km * 1000
              )
        """, nativeQuery = true)
    List<UUID> findUsersNearLocationPostgis(
        @Param("alertLat") double alertLat,
        @Param("alertLng") double alertLng
    );

    @Query(value = """
        SELECT u.id FROM users u
        WHERE u.last_known_lat IS NOT NULL
          AND u.last_known_lng IS NOT NULL
          AND u.location_updated_at > NOW() - INTERVAL '24 hours'
          AND (
            6371 * acos(
              LEAST(1.0, cos(radians(:alertLat)) * cos(radians(u.last_known_lat))
              * cos(radians(u.last_known_lng) - radians(:alertLng))
              + sin(radians(:alertLat)) * sin(radians(u.last_known_lat)))
            )
          ) <= u.notification_radius_km
        """, nativeQuery = true)
    List<UUID> findUsersNearLocationHaversine(
        @Param("alertLat") double alertLat,
        @Param("alertLng") double alertLng
    );
}
