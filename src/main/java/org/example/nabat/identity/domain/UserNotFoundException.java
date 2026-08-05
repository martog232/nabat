package org.example.nabat.identity.domain;

import org.example.nabat.identity.domain.UserId;

/**
 * No user exists for the given identifier.
 *
 * <p>Maps to {@code 404 Not Found}. Replaces the application layer's use of Spring
 * Security's {@code UsernameNotFoundException}.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UserId id) {
        super("User not found: " + id.value());
    }

    public UserNotFoundException(String message) {
        super(message);
    }
}
