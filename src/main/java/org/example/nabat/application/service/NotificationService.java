package org.example.nabat.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.GetNotificationUseCase;
import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.application.port.out.NotificationRepository;
import org.example.nabat.application.port.out.NotificationSender;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.UserId;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class NotificationService implements SendNotificationUseCase, GetNotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;

    private static final int[] MILESTONES = {10, 25, 50, 100, 250, 500, 1000};

    @Override
    public List<Notification> getNotifications(UserId userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<Notification> getUnreadNotifications(UserId userId) {
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Override
    public int countUnreadNotifications(UserId userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Override
    public Notification markAsRead(NotificationId notificationId, UserId userId) {
        Notification findedNotification = notificationRepository.findById(notificationId).orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!findedNotification.recipientId().equals(userId)) {
            throw new IllegalArgumentException("Notification does not belong to user");
        }

        // Ако вече е прочетено, просто го върни
        if (findedNotification.isRead()) {
            return findedNotification;
        }

        findedNotification.markAsRead();

        return notificationRepository.save(findedNotification);
    }

    @Override
    public void markAllAsRead(UserId userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
    }

    @Override
    public Notification sendVoteNotification(VoteNotificationCommand command) {
        return null;
    }

    @Override
    public Notification sendMilestoneNotification(MilestoneNotificationCommand command) {
        return null;
    }

    private boolean isMilestone(int count) {
        for (int milestone : MILESTONES) {
            if (count == milestone) {
                return true;
            }
        }
        return false;
    }
}
