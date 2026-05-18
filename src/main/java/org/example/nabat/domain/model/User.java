package org.example.nabat.domain.model;

import java.time.Instant;

public record User(
    UserId id,
    String email,
    String password,
    String displayName,
    Role role,
    boolean enabled,
    boolean emailVerified,
    Instant createdAt,
    Instant updatedAt
) {
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
            now
        );
    }

    public User withPassword(String newPassword) {
        return new User(id, email, newPassword, displayName, role, enabled, emailVerified, createdAt, Instant.now());
    }

    public User withRole(Role newRole) {
        return new User(id, email, password, displayName, newRole, enabled, emailVerified, createdAt, Instant.now());
    }

    public User disable() {
        return new User(id, email, password, displayName, role, false, emailVerified, createdAt, Instant.now());
    }

    public User enable() {
        return new User(id, email, password, displayName, role, true, emailVerified, createdAt, Instant.now());
    }

    public User verifyEmail() {
        return new User(id, email, password, displayName, role, enabled, true, createdAt, Instant.now());
    }
}
