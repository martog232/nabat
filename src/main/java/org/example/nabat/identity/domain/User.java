package org.example.nabat.identity.domain;

import java.time.Instant;

/**
 * A registered user.
 *
 * <p>Copies are made through {@link #toBuilder()}. The six {@code withX} methods
 * this record used to expose each re-listed all of its components, so every new
 * field meant editing seven places — and a copy that forgot a field would compile
 * silently.
 *
 * <p>{@code password} is the bcrypt hash. It is a component of this record because
 * the record doubles as the Spring Security principal; take care never to serialise
 * a {@code User} directly to a client — the REST layer has {@code UserResponse} for
 * that, and the WebSocket layer must use response DTOs too.
 *
 * <p>A user used to carry a last known location and a notification radius, so that a
 * new alert could be pushed to whoever was near it. Reports about the city are not
 * something a passer-by needs pushed to them, and both went with that fan-out (V17).
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
    /**
     * Incremented to invalidate every token previously issued to this user.
     *
     * <p>Access and refresh tokens carry the version they were minted at; the
     * authentication filter rejects any token whose version is behind the user's
     * current one. This is what makes a password reset actually end existing
     * sessions — before, a reset left every already-issued token valid for its
     * full lifetime.
     */
    int tokenVersion,
    /**
     * The authority this account acts for: a district or an institution. Set exactly when
     * the role is {@link Role#OFFICIAL}, and null otherwise — enforced here and by a CHECK
     * constraint (V28), because an official with no authority acts nowhere and an authority
     * on any other role is a grant waiting to be misread.
     */
    String authorityCode
) {
    public User {
        if ((role == Role.OFFICIAL) != (authorityCode != null)) {
            throw new IllegalArgumentException(
                "An authority is set exactly for OFFICIAL, not for " + role);
        }
    }

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
            0,
            null
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

    /**
     * Any role but {@link Role#OFFICIAL}, which needs an authority — see
     * {@link #appointTo}. Clears the authority, so moving an official to another role takes
     * their reports away with the role.
     */
    public User withRole(Role newRole) {
        if (newRole == Role.OFFICIAL) {
            throw new IllegalArgumentException("An official needs an authority: use appointTo");
        }
        return toBuilder().role(newRole).authorityCode(null).touch().build();
    }

    /** Makes this account the voice of one authority. */
    public User appointTo(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authority code is required");
        }
        return toBuilder().role(Role.OFFICIAL).authorityCode(code).touch().build();
    }

    /**
     * Whether this account may act on a report that authority {@code code} answers for: the
     * right role, and that authority.
     */
    public boolean actsFor(String code) {
        return role.canActForAuthority() && code != null && code.equals(authorityCode);
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
        private int tokenVersion;
        private String authorityCode;

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
            this.tokenVersion = source.tokenVersion;
            this.authorityCode = source.authorityCode;
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
        public Builder tokenVersion(int value) { this.tokenVersion = value; return this; }
        public Builder authorityCode(String value) { this.authorityCode = value; return this; }

        /** Sets {@code updatedAt} to now. */
        public Builder touch() { this.updatedAt = Instant.now(); return this; }

        public User build() {
            return new User(
                id, email, password, displayName, role, enabled, emailVerified,
                createdAt, updatedAt, tokenVersion, authorityCode
            );
        }
    }
}
