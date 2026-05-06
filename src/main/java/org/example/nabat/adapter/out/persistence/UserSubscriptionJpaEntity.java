package org.example.nabat.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.UserSubscription;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "user_subscriptions")
public class UserSubscriptionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "radius_km", nullable = false)
    private double radiusKm;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static UserSubscriptionJpaEntity from(UserSubscription s) {
        UserSubscriptionJpaEntity e = new UserSubscriptionJpaEntity();
        e.id = s.id();
        e.userId = s.userId().value();
        e.alertType = s.alertType();
        e.latitude = s.center().latitude();
        e.longitude = s.center().longitude();
        e.radiusKm = s.radiusKm();
        e.active = s.active();
        e.createdAt = s.createdAt();
        return e;
    }

    public UserSubscription toDomain() {
        return new UserSubscription(
                id,
                UserId.of(userId),
                alertType,
                Location.of(latitude, longitude),
                radiusKm,
                active,
                createdAt
        );
    }
}

