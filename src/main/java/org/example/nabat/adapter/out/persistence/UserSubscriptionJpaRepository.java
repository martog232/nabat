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
     * Uses the Haversine formula — same approach as {@code AlertJpaRepository}.
     */
    @Query("""
        SELECT DISTINCT s.userId FROM UserSubscriptionJpaEntity s
        WHERE s.active = true
          AND s.alertType = :type
          AND (
            6371 * acos(
              cos(radians(:lat)) * cos(radians(s.latitude)) *
              cos(radians(s.longitude) - radians(:lon)) +
              sin(radians(:lat)) * sin(radians(s.latitude))
            )
          ) <= (s.radiusKm + :radiusKm)
        """)
    List<UUID> findUserIdsMatching(
            @Param("type") AlertType type,
            @Param("lat") double latitude,
            @Param("lon") double longitude,
            @Param("radiusKm") double radiusKm
    );
}

