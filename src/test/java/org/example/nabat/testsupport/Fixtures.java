package org.example.nabat.testsupport;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.notification.domain.Notification;
import org.example.nabat.notification.domain.NotificationId;
import org.example.nabat.notification.domain.NotificationType;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain fixtures for tests.
 *
 * <p>Tests used to call the record canonical constructors directly with a dozen-plus
 * positional arguments, so adding a component to {@link User} or {@link Alert} broke
 * every test that built one. Building from these helpers plus
 * {@link User#toBuilder()} keeps that churn in one place.
 */
public final class Fixtures {

    private Fixtures() {
    }

    /** An enabled, unverified USER with a sensible default profile. */
    public static User user() {
        return User.create("test@example.com", "hashedPassword", "Test User");
    }

    public static User user(String email) {
        return User.create(email, "hashedPassword", "Test User");
    }

    public static User userWithId(UUID id) {
        return user().toBuilder().id(UserId.of(id)).build();
    }

    public static User admin() {
        return user().toBuilder().role(Role.ADMIN).build();
    }

    /** An ACTIVE alert with no votes. */
    public static Alert alert() {
        return alert(AlertId.generate(), UUID.randomUUID());
    }

    public static Alert alert(UUID reportedBy) {
        return alert(AlertId.generate(), reportedBy);
    }

    public static Alert alert(AlertId id, UUID reportedBy) {
        return new Alert(
            id,
            "Road closure",
            "Main road is blocked",
            AlertType.OTHER,
            AlertSeverity.MEDIUM,
            Location.of(42.695, 23.329),
            Instant.now(),
            AlertStatus.ACTIVE,
            reportedBy,
            0, 0, 0, 0,
            null,
            null
        );
    }

    public static Alert alertAt(double latitude, double longitude) {
        return new Alert(
            AlertId.generate(),
            "Nearby Alert",
            "Some description",
            AlertType.FIRE,
            AlertSeverity.HIGH,
            Location.of(latitude, longitude),
            Instant.now(),
            AlertStatus.ACTIVE,
            UUID.randomUUID(),
            0, 0, 0, 0,
            null,
            null
        );
    }

    public static Notification notification(UserId recipientId) {
        return new Notification(
            NotificationId.generate(),
            recipientId,
            NotificationType.ALERT_UPVOTED,
            "Notice",
            "Someone voted",
            AlertId.generate(),
            UserId.generate(),
            false,
            Instant.now()
        );
    }
}
