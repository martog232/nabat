package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.NotificationType;
import org.example.nabat.domain.model.Role;
import org.example.nabat.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationJpaRepositoryTest extends PostgresTestSupport {

    @Autowired
    private NotificationJpaRepository notificationRepository;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private AlertJpaRepository alertRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        alertRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void findUnreadCountAndMarkAllAsReadWork() {
        UUID recipientId = saveUser("recipient@example.com");
        UUID triggerUserId = saveUser("trigger@example.com");
        UUID relatedAlertId = saveAlert(recipientId);

        NotificationJpaEntity oldUnread = notification(recipientId, triggerUserId, relatedAlertId, false,
            Instant.parse("2026-05-01T10:00:00Z"), "old");
        NotificationJpaEntity newUnread = notification(recipientId, triggerUserId, relatedAlertId, false,
            Instant.parse("2026-05-01T11:00:00Z"), "new");
        NotificationJpaEntity read = notification(recipientId, triggerUserId, relatedAlertId, true,
            Instant.parse("2026-05-01T12:00:00Z"), "read");

        notificationRepository.saveAll(List.of(oldUnread, newUnread, read));
        notificationRepository.flush();

        List<NotificationJpaEntity> allDesc = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        assertThat(allDesc).extracting(NotificationJpaEntity::getTitle)
            .containsExactly("read", "new", "old");

        List<NotificationJpaEntity> unreadDesc =
            notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId);
        assertThat(unreadDesc).extracting(NotificationJpaEntity::getTitle)
            .containsExactly("new", "old");
        assertThat(notificationRepository.countByRecipientIdAndIsReadFalse(recipientId)).isEqualTo(2);

        notificationRepository.markAllAsReadByRecipientId(recipientId);
        notificationRepository.flush();

        assertThat(notificationRepository.countByRecipientIdAndIsReadFalse(recipientId)).isZero();
    }

    private UUID saveUser(String email) {
        UserJpaEntity user = new UserJpaEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword("hash");
        user.setDisplayName("User");
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setNotificationRadiusKm(5);
        return userRepository.saveAndFlush(user).getId();
    }

    private UUID saveAlert(UUID reportedBy) {
        AlertJpaEntity alert = new AlertJpaEntity();
        alert.setId(UUID.randomUUID());
        alert.setTitle("Related alert");
        alert.setDescription("desc");
        alert.setType(AlertType.HAZARD);
        alert.setSeverity(AlertSeverity.HIGH);
        alert.setLatitude(42.6977);
        alert.setLongitude(23.3219);
        alert.setCreatedAt(Instant.now());
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setReportedBy(reportedBy);
        return alertRepository.saveAndFlush(alert).getId();
    }

    private NotificationJpaEntity notification(
            UUID recipientId,
            UUID triggeredByUserId,
            UUID relatedAlertId,
            boolean read,
            Instant createdAt,
            String title
    ) {
        NotificationJpaEntity e = new NotificationJpaEntity();
        e.setId(UUID.randomUUID());
        e.setRecipientId(recipientId);
        e.setType(NotificationType.ALERT_UPVOTED);
        e.setTitle(title);
        e.setMessage("msg");
        e.setRelatedAlertId(relatedAlertId);
        e.setTriggeredByUserId(triggeredByUserId);
        e.setRead(read);
        e.setCreatedAt(createdAt);
        return e;
    }
}
