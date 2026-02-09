package org.example.nabat.adapter.out.persistence;

import org.example.nabat.application.port.out.NotificationRepository;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = NotificationJpaEntity.from(notification);
        NotificationJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Notification> findById(NotificationId id) {
        return jpaRepository.findById(id.value())
                .map(NotificationJpaEntity::toDomain);
    }

    @Override
    public List<Notification> findByRecipientIdOrderByCreatedAtDesc(UserId recipientId) {
        return jpaRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId.value())
                .stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(UserId recipientId) {
        return jpaRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId.value())
                .stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public int countByRecipientIdAndIsReadFalse(UserId recipientId) {
        return jpaRepository.countByRecipientIdAndIsReadFalse(recipientId.value());
    }

    @Override
    public void markAllAsReadByRecipientId(UserId recipientId) {
        jpaRepository.markAllAsReadByRecipientId(recipientId.value());
    }
}
