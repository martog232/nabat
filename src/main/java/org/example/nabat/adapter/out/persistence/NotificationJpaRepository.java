package org.example.nabat.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    List<NotificationJpaEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    List<NotificationJpaEntity> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(UUID recipientId);

    int countByRecipientIdAndIsReadFalse(UUID recipientId);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationJpaEntity n SET n.isRead = true WHERE n.recipientId = :recipientId")
    void markAllAsReadByRecipientId(@Param("recipientId") UUID recipientId);
}

