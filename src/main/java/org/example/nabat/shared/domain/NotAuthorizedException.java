package org.example.nabat.shared.domain;

/**
 * The actor is authenticated but not permitted to perform this operation on this
 * resource.
 *
 * <p>Maps to {@code 403 Forbidden}.
 *
 * <p>Exists so the application layer can express authorization failures without
 * importing Spring Security. {@code AGENTS.md} states that Spring Security
 * exceptions must never be thrown from the domain or application layers, but
 * {@code AlertLifecycleService} threw {@code AccessDeniedException} and
 * {@code UpdateUserPreferencesService} threw {@code UsernameNotFoundException}.
 *
 * <p>It also replaces the pattern of signalling "not yours" with
 * {@code IllegalArgumentException}, which mapped an authorization failure to a
 * 400 — and, by using a different message than the not-found case, gave an
 * attacker an oracle for probing which ids exist.
 */
public class NotAuthorizedException extends RuntimeException {

    public NotAuthorizedException(String message) {
        super(message);
    }
}
