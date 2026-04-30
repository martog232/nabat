package org.example.nabat.application.service;

import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.application.port.out.NotificationRepository;
import org.example.nabat.application.port.out.NotificationSender;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.NotificationType;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationSender notificationSender;

    private NotificationService service;

    private final UserId user = UserId.generate();
    private final AlertId alert = AlertId.generate();

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, notificationSender);
    }

    private Notification existing(boolean read) {
        return new Notification(
                NotificationId.generate(), user, NotificationType.ALERT_UPVOTED,
                "t", "m", alert, null, read, Instant.now());
    }

    @Test
    void markAsRead_marksAndSaves() {
        Notification n = existing(false);
        when(notificationRepository.findById(n.id())).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Notification result = service.markAsRead(n.id(), user);

        assertTrue(result.isRead());
        verify(notificationRepository).save(any());
    }

    @Test
    void markAsRead_alreadyRead_shortCircuits() {
        Notification n = existing(true);
        when(notificationRepository.findById(n.id())).thenReturn(Optional.of(n));

        Notification result = service.markAsRead(n.id(), user);

        assertSame(n, result);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_wrongUser_throws() {
        Notification n = existing(false);
        when(notificationRepository.findById(n.id())).thenReturn(Optional.of(n));

        assertThrows(IllegalArgumentException.class,
                () -> service.markAsRead(n.id(), UserId.generate()));
    }

    @Test
    void markAsRead_notFound_throws() {
        NotificationId id = NotificationId.generate();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.markAsRead(id, user));
    }

    @Test
    void markAllAsRead_delegates() {
        service.markAllAsRead(user);
        verify(notificationRepository).markAllAsReadByRecipientId(user);
    }

    @Test
    void getNotifications_delegates() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user))
                .thenReturn(List.of());
        assertEquals(0, service.getNotifications(user).size());
    }

    @Test
    void countUnread_delegates() {
        when(notificationRepository.countByRecipientIdAndIsReadFalse(user)).thenReturn(7);
        assertEquals(7, service.countUnreadNotifications(user));
    }

    @Test
    void sendVoteNotification_persistsAndDelivers() {
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Notification result = service.sendVoteNotification(
                new SendNotificationUseCase.VoteNotificationCommand(
                        user, UserId.generate(), alert, "Title", VoteType.UPVOTE));

        assertEquals(NotificationType.ALERT_UPVOTED, result.type());
        verify(notificationRepository).save(any());
        verify(notificationSender).sendToUser(eq(user), any());
    }

    @Test
    void sendMilestoneNotification_persistsAndDelivers() {
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Notification result = service.sendMilestoneNotification(
                new SendNotificationUseCase.MilestoneNotificationCommand(
                        user, alert, "Title", 10));

        assertEquals(NotificationType.ALERT_MILESTONE, result.type());
        verify(notificationRepository).save(any());
        verify(notificationSender).sendToUser(eq(user), any());
    }
}

