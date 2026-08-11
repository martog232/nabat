package org.example.nabat.identity.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "passw0rd12",       // exactly the minimum
        "s3cr3tP@ssphrase",
        "correct1horse2battery3staple"
    })
    void acceptsPasswordsThatMeetEveryRule(String password) {
        assertTrue(PasswordPolicy.isAcceptable(password), password);
    }

    @Test
    void rejectsPasswordsBelowTheMinimumLength() {
        assertEquals(
            PasswordPolicy.Violation.TOO_SHORT,
            PasswordPolicy.check("passw0rd").orElseThrow()
        );
    }

    @Test
    void rejectsPasswordsWithNoLetter() {
        assertEquals(
            PasswordPolicy.Violation.NO_LETTER,
            PasswordPolicy.check("1234567890").orElseThrow()
        );
    }

    @Test
    void rejectsPasswordsWithNoDigit() {
        assertEquals(
            PasswordPolicy.Violation.NO_DIGIT,
            PasswordPolicy.check("passwordonly").orElseThrow()
        );
    }

    @Test
    void treatsNullAsTooShortRatherThanThrowing() {
        // The validator hands whatever arrived straight through; @NotBlank reports absence.
        assertEquals(PasswordPolicy.Violation.TOO_SHORT, PasswordPolicy.check(null).orElseThrow());
    }

    /**
     * BCrypt reads only the first 72 bytes. Accepting a longer password would store a
     * silently truncated one and tell the user their long passphrase was accepted, when in
     * fact any string sharing its first 72 bytes opens the account.
     */
    @Test
    void rejectsPasswordsLongerThanBcryptWillHash() {
        String justRight = "a1" + "x".repeat(PasswordPolicy.MAX_BYTES - 2);
        String oneTooMany = justRight + "x";

        assertEquals(PasswordPolicy.MAX_BYTES, justRight.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(PasswordPolicy.isAcceptable(justRight));
        assertEquals(
            PasswordPolicy.Violation.TOO_LONG,
            PasswordPolicy.check(oneTooMany).orElseThrow()
        );
    }

    /**
     * The limit is bytes, not characters — the distinction the truncation actually turns
     * on. Cyrillic is two bytes per character, so this passphrase is well under 72
     * characters and well over 72 bytes; a character-based check would wave it through and
     * let BCrypt cut it anyway.
     */
    @Test
    void countsBytesNotCharactersForTheUpperBound() {
        String cyrillic = "парола1" + "а".repeat(40);

        assertTrue(cyrillic.length() < PasswordPolicy.MAX_BYTES, "precondition: under the limit in characters");
        assertTrue(cyrillic.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES,
            "precondition: over the limit in bytes");

        assertFalse(PasswordPolicy.isAcceptable(cyrillic));
        assertEquals(PasswordPolicy.Violation.TOO_LONG, PasswordPolicy.check(cyrillic).orElseThrow());
    }

    /** A non-Latin password is fine as long as it fits: letters are letters. */
    @Test
    void acceptsNonLatinLetters() {
        assertTrue(PasswordPolicy.isAcceptable("паролата1"  + "аб"));
    }
}
