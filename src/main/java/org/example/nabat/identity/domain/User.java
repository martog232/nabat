package org.example.nabat.identity.domain;

import org.example.nabat.shared.domain.Location;
import java.time.Instant;

/**
 * A registered user.
 *
 * <p>Copies are made through {@link #toBuilder()}. The six {@code withX} methods
 * this record used to expose each re-listed all of its components, so every new
 * field meant editing seven places — and a copy that forgot a field would compile
 * silently. {@code UpdateUserPreferencesService} had accumulated a seventh,
 * hand-inlined copy for the same reason.
 *
 * <p>{@code password} is the bcrypt hash. It is a component of this record because
 * the record doubles as the Spring Security principal; take care never to serialise
 * a {@code User} directly to a client — the REST layer has {@code UserResponse} for
 * that, and the WebSocket layer must use response DTOs too.
 */
public record User(
    UserId id,
    String email,
    String password,
    String displayName,
    Role role,
    boolean enabled,
    boolean emailVerified,
    Instant createdAt,
    Instant updatedAt,
    int notificationRadiusKm,
    Double lastKnownLat,
    Double lastKnownLng,
    Instant locationUpdatedAt,
    /**
     * Incremented to invalidate every token previously issued to this user.
     *
     * <p>Access and refresh tokens carry the version they were minted at; the
     * authentication filter rejects any token whose version is behind the user's
     * current one. This is what makes a password reset actually end existing
     * sessions — before, a reset left every already-issued token valid for its
     * full lifetime.
     */
    int tokenVersion
) {
    private static final int DEFAULT_NOTIFICATION_RADIUS_KM = 5;

    public static User create(
        String email,
        String password,
        String displayName
    ) {
        Instant now = Instant.now();
        return new User(
            UserId.generate(),
            email,
            password,
            displayName,
            Role.USER,
            true,
            false,   // emailVerified = false until they click the link
            now,
            now,
            DEFAULT_NOTIFICATION_RADIUS_KM,
            null,
            null,
            null,
            0
        );
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Replaces the password hash and invalidates all existing sessions. */
    public User withPassword(String newPassword) {
        return toBuilder()
            .password(newPassword)
            .tokenVersion(tokenVersion + 1)
            .touch()
            .build();
    }

    public User withRole(Role newRole) {
        return toBuilder().role(newRole).touch().build();
    }

    /** Disables the account and invalidates all existing sessions. */
    public User disable() {
        return toBuilder().enabled(false).tokenVersion(tokenVersion + 1).touch().build();
    }

    public User enable() {
        return toBuilder().enabled(true).touch().build();
    }

    public User verifyEmail() {
        return toBuilder().emailVerified(true).touch().build();
    }

    public User withNotificationRadius(int radiusKm) {
        return toBuilder().notificationRadiusKm(radiusKm).touch().build();
    }

    public User withLocation(double lat, double lng, int radiusKm) {
        Location validated = Location.of(lat, lng);
        Instant now = Instant.now();
        return toBuilder()
            .notificationRadiusKm(radiusKm)
            .lastKnownLat(validated.latitude())
            .lastKnownLng(validated.longitude())
            .locationUpdatedAt(now)
            .updatedAt(now)
            .build();
    }

    /** Invalidates every token issued to this user so far. */
    public User invalidateSessions() {
        return toBuilder().tokenVersion(tokenVersion + 1).touch().build();
    }

    public static final class Builder {
        private UserId id;
        private String email;
        private String password;
        private String displayName;
        private Role role;
        private boolean enabled;
        private boolean emailVerified;
        private Instant createdAt;
        private Instant updatedAt;
        private int notificationRadiusKm;
        private Double lastKnownLat;
        private Double lastKnownLng;
        private Instant locationUpdatedAt;
        private int tokenVersion;

        private Builder(User source) {
            this.id = source.id;
            this.email = source.email;
            this.password = source.password;
            this.displayName = source.displayName;
            this.role = source.role;
            this.enabled = source.enabled;
            this.emailVerified = source.emailVerified;
            this.createdAt = source.createdAt;
            this.updatedAt = source.updatedAt;
            this.notificationRadiusKm = source.notificationRadiusKm;
            this.lastKnownLat = source.lastKnownLat;
            this.lastKnownLng = source.lastKnownLng;
            this.locationUpdatedAt = source.locationUpdatedAt;
            this.tokenVersion = source.tokenVersion;
        }

        public Builder id(UserId value) { this.id = value; return this; }
        public Builder email(String value) { this.email = value; return this; }
        public Builder password(String value) { this.password = value; return this; }
        public Builder displayName(String value) { this.displayName = value; return this; }
        public Builder role(Role value) { this.role = value; return this; }
        public Builder enabled(boolean value) { this.enabled = value; return this; }
        public Builder emailVerified(boolean value) { this.emailVerified = value; return this; }
        public Builder createdAt(Instant value) { this.createdAt = value; return this; }
        public Builder updatedAt(Instant value) { this.updatedAt = value; return this; }
        public Builder notificationRadiusKm(int value) { this.notificationRadiusKm = value; return this; }
        public Builder lastKnownLat(Double value) { this.lastKnownLat = value; return this; }
        public Builder lastKnownLng(Double value) { this.lastKnownLng = value; return this; }
        public Builder locationUpdatedAt(Instant value) { this.locationUpdatedAt = value; return this; }
        public Builder tokenVersion(int value) { this.tokenVersion = value; return this; }

        /** Sets {@code updatedAt} to now. */
        public Builder touch() { this.updatedAt = Instant.now(); return this; }

        public User build() {
            return new User(
                id, email, password, displayName, role, enabled, emailVerified,
                createdAt, updatedAt, notificationRadiusKm,
                lastKnownLat, lastKnownLng, locationUpdatedAt, tokenVersion
            );
        }
    }
}
