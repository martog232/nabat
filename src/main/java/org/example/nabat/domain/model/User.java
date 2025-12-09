package org.example.nabat.domain.model;

import java.time.Instant;

public record User(
    UserId id,
    String email,
    String password,
    String displayName,
    Role role,
    boolean enabled,
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
            now,
            now
        );
    }

    public User withPassword(String newPassword) {
        return new User(id, email, newPassword, displayName, role, enabled, createdAt, Instant.now());
    }

    public User withRole(Role newRole) {
        return new User(id, email, password, displayName, newRole, enabled, createdAt, Instant.now());
    }

    public User disable() {
        return new User(id, email, password, displayName, role, false, createdAt, Instant.now());
    }

    public User enable() {
        return new User(id, email, password, displayName, role, true, createdAt, Instant.now());
    }
}
