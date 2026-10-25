package org.example.nabat.identity.domain;

public enum VerificationTokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    /**
     * Signs an official in from the button in a report mail. Unlike the other two it is not
     * spent on use — every official reading that mailbox may follow it — and lives for weeks,
     * not hours. See {@link VerificationToken#createSignInLink}.
     */
    SIGN_IN_LINK,
    /**
     * An official invited by their authority's lead sets their first password with it (valid
     * a week, single-use), which also verifies the address the invitation went to.
     */
    INVITATION
}
