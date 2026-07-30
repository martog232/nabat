package org.example.nabat.notification.adapter.out.persistence;

import org.example.nabat.testsupport.Fixtures;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.notification.domain.NotificationType;
import org.example.nabat.identity.domain.Role;
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
        // Built through the domain record and UserJpaEntity.from, matching how the
        // identity module's own tests do it. The field-by-field setter version this
        // replaced relied on same-package access to a protected constructor, which
        // stopped being available once users moved into their own module.
        return userRepository.saveAndFlush(UserJpaEntity.from(Fixtures.user(email))).getId();
    }

    private UUID saveAlert(UUID reportedBy) {
        // Built through the domain factory rather than field setters: AlertJpaEntity no
        // longer exposes a blanket @Setter, which had allowed callers to change an
        // alert's status without going through Alert.resolve().
        Alert alert = new Alert(
                AlertId.generate(),
                "Related alert",
                "desc",
                AlertType.HAZARD,
                AlertSeverity.HIGH,
                Location.of(42.6977, 23.3219),
                Instant.now(),
                AlertStatus.ACTIVE,
                reportedBy,
                0, 0, 0, 0,
                null,
                null
        );
        return alertRepository.saveAndFlush(AlertJpaEntity.from(alert)).getId();
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
