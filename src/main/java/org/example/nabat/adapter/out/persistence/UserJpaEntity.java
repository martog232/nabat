package org.example.nabat.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "users")
public class UserJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "notification_radius_km", nullable = false)
    private int notificationRadiusKm;

    @Column(name = "last_known_lat")
    private Double lastKnownLat;

    @Column(name = "last_known_lng")
    private Double lastKnownLng;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    protected UserJpaEntity() {
    }

    public static UserJpaEntity from(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.id = user.id().value();
        entity.email = user.email();
        entity.password = user.password();
        entity.displayName = user.displayName();
        entity.role = user.role();
        entity.enabled = user.enabled();
        entity.emailVerified = user.emailVerified();
        entity.createdAt = user.createdAt();
        entity.updatedAt = user.updatedAt();
        entity.notificationRadiusKm = user.notificationRadiusKm();
        entity.lastKnownLat = user.lastKnownLat();
        entity.lastKnownLng = user.lastKnownLng();
        entity.locationUpdatedAt = user.locationUpdatedAt();
        return entity;
    }

    public User toDomain() {
        return new User(
            UserId.of(id),
            email,
            password,
            displayName,
            role,
            enabled,
            emailVerified,
            createdAt,
            updatedAt,
            notificationRadiusKm,
            lastKnownLat,
            lastKnownLng,
            locationUpdatedAt
        );
    }
}
