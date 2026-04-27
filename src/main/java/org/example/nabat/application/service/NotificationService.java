package org.example.nabat.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.GetNotificationUseCase;
import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.application.port.out.NotificationRepository;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.UserId;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class NotificationService implements SendNotificationUseCase, GetNotificationUseCase {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getNotifications(UserId userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotifications(UserId userId) {
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public int countUnreadNotifications(UserId userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public Notification markAsRead(NotificationId notificationId, UserId userId) {
        Notification found = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!found.recipientId().equals(userId)) {
            throw new IllegalArgumentException("Notification does not belong to user");
        }

        // Already read — return as-is.
        if (found.isRead()) {
            return found;
        }

        // Notification is an immutable record; markAsRead() returns a new instance.
        Notification updated = found.markAsRead();
        return notificationRepository.save(updated);
    }

    @Override
    @Transactional
    public void markAllAsRead(UserId userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
    }

    // TODO: implement notification creation when AlertVoteService starts triggering events.
    @Override
    public Notification sendVoteNotification(VoteNotificationCommand command) {
        return null;
    }

    @Override
    public Notification sendMilestoneNotification(MilestoneNotificationCommand command) {
        return null;
    }
}
