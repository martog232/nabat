package org.example.nabat.identity.application.port.out;

/**
 * Driven port — abstracts any email delivery mechanism so the domain stays
 * free of SMTP/Jakarta Mail coupling.
 */
public interface EmailSender {
    void sendVerificationEmail(String toEmail, String displayName, String verificationToken);
    void sendPasswordResetEmail(String toEmail, String displayName, String resetToken);

    /** Invites an official to their authority: a link to set their first password, valid a week. */
    void sendInvitationEmail(String toEmail, String displayName, String authorityName, String invitedBy,
                             String invitationToken);
}

