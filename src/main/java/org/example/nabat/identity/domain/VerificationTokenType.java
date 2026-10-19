package org.example.nabat.identity.domain;

public enum VerificationTokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    /**
     * Signs an official in from the button in a report mail. Unlike the other two it is not
     * spent on use — every official reading that mailbox may follow it — and lives for weeks,
     * not hours. See {@link VerificationToken#createSignInLink}.
     */
    SIGN_IN_LINK
}
