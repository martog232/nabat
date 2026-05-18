package org.example.nabat.application.port.out;

/**
 * Driven port — abstracts any email delivery mechanism so the domain stays
 * free of SMTP/Jakarta Mail coupling.
 */
public interface EmailSender {
    void sendVerificationEmail(String toEmail, String displayName, String verificationToken);
    void sendPasswordResetEmail(String toEmail, String displayName, String resetToken);
}

