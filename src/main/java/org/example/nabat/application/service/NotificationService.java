package org.example.nabat.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.GetNotificationUseCase;
import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.application.port.out.NotificationRepository;
import org.example.nabat.application.port.out.NotificationSender;
import org.example.nabat.domain.exception.NotificationNotFoundException;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.NotificationType;
import org.example.nabat.domain.model.UserId;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class NotificationService implements SendNotificationUseCase, GetNotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;
    private final MessageSource messageSource;

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
        // Absent and not-yours are reported identically, as a 404. Reporting them
        // differently — which the two distinct IllegalArgumentException messages here
        // used to do, both surfacing as 400 — let a caller probe which notification ids
        // exist, and mapped an authorization failure to "bad request".
        Notification found = notificationRepository.findById(notificationId)
                .filter(notification -> notification.recipientId().equals(userId))
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (found.isRead()) {
            return found;
        }

        Notification updated = found.markAsRead();
        return notificationRepository.save(updated);
    }

    @Override
    @Transactional
    public void markAllAsRead(UserId userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
    }

    @Override
    @Transactional
    public Notification sendVoteNotification(VoteNotificationCommand command) {
        NotificationType type = switch (command.voteType()) {
            case UPVOTE   -> NotificationType.ALERT_UPVOTED;
            case DOWNVOTE -> NotificationType.ALERT_DOWNVOTED;
            case CONFIRM  -> NotificationType.ALERT_CONFIRMED;
        };

        String title = messageSource.getMessage(
                voteTitleKey(type),
                null,
                LocaleContextHolder.getLocale()
        );
        String message = messageSource.getMessage(
                "notification.vote.message",
                new Object[]{command.alertTitle()},
                LocaleContextHolder.getLocale()
        );

        Notification n = Notification.createVoteNotification(
                command.alertOwnerId(),
                type,
                command.alertId(),
                command.voterId(),
                title,
                message
        );
        Notification saved = notificationRepository.save(n);
        notificationSender.sendToUser(command.alertOwnerId(), saved);
        return saved;
    }

    @Override
    @Transactional
    public Notification sendMilestoneNotification(MilestoneNotificationCommand command) {
        String title = messageSource.getMessage(
                "notification.milestone.title",
                null,
                LocaleContextHolder.getLocale()
        );
        String message = messageSource.getMessage(
                "notification.milestone.message",
                new Object[]{command.milestoneTitle(), command.confirmationCount()},
                LocaleContextHolder.getLocale()
        );

        Notification n = Notification.createMilestoneNotification(
                command.alertOwnerId(),
                command.alertId(),
                title,
                message
        );
        Notification saved = notificationRepository.save(n);
        notificationSender.sendToUser(command.alertOwnerId(), saved);
        return saved;
    }

    private String voteTitleKey(NotificationType type) {
        return switch (type) {
            case ALERT_UPVOTED -> "notification.vote.title.alert_upvoted";
            case ALERT_DOWNVOTED -> "notification.vote.title.alert_downvoted";
            case ALERT_CONFIRMED -> "notification.vote.title.alert_confirmed";
            default -> "notification.vote.title.default";
        };
    }
}
