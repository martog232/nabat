package org.example.nabat.identity.domain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * What counts as an acceptable password.
 *
 * <p>Lives in the domain because it is a rule about accounts, not about HTTP: registration
 * and password reset both have to enforce it, and when they each carried their own
 * {@code @Size(min = 6)} the two could drift apart without anything noticing. The bean
 * validation annotation ({@code @StrongPassword}) is only the adapter that surfaces it.
 *
 * <h2>On the upper bound</h2>
 * The maximum is not padding. Passwords are hashed with BCrypt, which uses only the first
 * <strong>72 bytes</strong> of its input and discards the rest without complaint. A user
 * who sets a 100-character passphrase therefore has a password that is silently 72 bytes
 * long, and any string sharing that prefix unlocks the account. Rejecting the input is the
 * only honest option: accepting it would be promising strength that is not stored.
 *
 * <p>The limit is counted in <strong>UTF-8 bytes, not characters</strong>, because that is
 * what BCrypt truncates. Cyrillic costs two bytes per character, so a 40-character
 * Bulgarian passphrase is already 80 bytes — a character-based check would pass it and let
 * the truncation happen anyway.
 */
public final class PasswordPolicy {

    /**
     * Was 6, which is inside the range a commodity GPU exhausts by brute force. Ten with a
     * mixed alphabet is not strong in isolation, but it is a floor rather than a formality.
     */
    public static final int MIN_LENGTH = 10;

    /** BCrypt's input limit. See the class javadoc — this is a truncation boundary. */
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** Why a password was rejected. One reason, the first that applies. */
    public enum Violation {
        TOO_SHORT,
        TOO_LONG,
        NO_LETTER,
        NO_DIGIT
    }

    /**
     * @return the first rule the password breaks, or empty when it is acceptable
     */
    public static Optional<Violation> check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Optional.of(Violation.TOO_SHORT);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of(Violation.TOO_LONG);
        }
        if (password.chars().noneMatch(Character::isLetter)) {
            return Optional.of(Violation.NO_LETTER);
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            return Optional.of(Violation.NO_DIGIT);
        }
        return Optional.empty();
    }

    public static boolean isAcceptable(String password) {
        return check(password).isEmpty();
    }
}
