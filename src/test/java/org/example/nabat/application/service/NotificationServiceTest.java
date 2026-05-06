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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
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
    void getUnreadNotifications_delegates() {
        Notification unread = existing(false);
        when(notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(user))
                .thenReturn(List.of(unread));

        List<Notification> result = service.getUnreadNotifications(user);

        assertEquals(List.of(unread), result);
        verify(notificationRepository).findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(user);
    }

    @Test
    void countUnread_delegates() {
        when(notificationRepository.countByRecipientIdAndIsReadFalse(user)).thenReturn(7);
        assertEquals(7, service.countUnreadNotifications(user));
    }

    @ParameterizedTest
    @CsvSource({
            "UPVOTE,ALERT_UPVOTED,Your alert was upvoted",
            "DOWNVOTE,ALERT_DOWNVOTED,Your alert was downvoted",
            "CONFIRM,ALERT_CONFIRMED,Your alert was confirmed"
    })
    void sendVoteNotification_persistsAndDeliversMappedNotification(
            VoteType voteType,
            NotificationType expectedType,
            String expectedTitle
    ) {
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        UserId voter = UserId.generate();

        Notification result = service.sendVoteNotification(
                new SendNotificationUseCase.VoteNotificationCommand(
                        user, voter, alert, "Title", voteType));

        assertEquals(expectedType, result.type());
        assertEquals(expectedTitle, result.title());
        assertEquals("Someone voted on 'Title'", result.message());
        assertEquals(user, result.recipientId());
        assertEquals(alert, result.relatedAlertId());
        assertEquals(voter, result.triggeredByUserId());
        assertFalse(result.isRead());

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(savedCaptor.capture());
        assertEquals(expectedType, savedCaptor.getValue().type());
        verify(notificationSender).sendToUser(user, result);
    }

    @Test
    void sendMilestoneNotification_persistsAndDelivers() {
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Notification result = service.sendMilestoneNotification(
                new SendNotificationUseCase.MilestoneNotificationCommand(
                        user, alert, "Title", 10));

        assertEquals(NotificationType.ALERT_MILESTONE, result.type());
        assertEquals("🎉 Milestone reached!", result.title());
        assertEquals("'Title' now has 10 confirmations!", result.message());
        assertEquals(user, result.recipientId());
        assertEquals(alert, result.relatedAlertId());
        assertNull(result.triggeredByUserId());
        assertFalse(result.isRead());
        verify(notificationRepository).save(result);
        verify(notificationSender).sendToUser(user, result);
    }
}

