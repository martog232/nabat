package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface AlertJpaRepository extends JpaRepository<AlertJpaEntity, UUID> {

    List<AlertJpaEntity> findByStatus(AlertStatus status);

    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status = 'ACTIVE'
        AND (
            6371 * acos(
                cos(radians(:lat)) * cos(radians(a.latitude)) *
                cos(radians(a.longitude) - radians(:lon)) +
                sin(radians(:lat)) * sin(radians(a.latitude))
            )
        ) <= :radius
        ORDER BY a.created_at DESC
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadius(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm
    );

    @Modifying
    @Transactional
    @Query("UPDATE AlertJpaEntity a SET a.upvoteCount = :upvotes, a.downvoteCount = :downvotes, a.confirmationCount = :confirmations WHERE a.id = :id")
    void updateVoteCounts(@Param("id") UUID id, @Param("upvotes") int upvotes, @Param("downvotes") int downvotes, @Param("confirmations") int confirmations);

}
