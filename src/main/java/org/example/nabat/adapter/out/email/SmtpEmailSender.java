package org.example.nabat.adapter.out.email;

import org.example.nabat.application.port.out.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Driven adapter — delivers emails via a JavaMailSender (SMTP).
 * In local dev, this points at MailHog (port 1025); in production, set
 * MAIL_HOST / MAIL_PORT / MAIL_FROM environment variables.
 *
 * <p>SMTP delivery failures (e.g. MailHog not running) are caught and logged as
 * errors rather than propagated. This keeps registration and password-reset flows
 * functional even when the local SMTP relay is absent — the token is still persisted
 * in the database and can be retrieved by the developer from the logs.</p>
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${nabat.mail.from:noreply@nabat.local}") String from,
            @Value("${nabat.mail.base-url:http://localhost:8080}") String baseUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String displayName, String verificationToken) {
        String link = baseUrl + "/api/v1/auth/verify?token=" + verificationToken;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(toEmail);
        msg.setSubject("Nabat — Please verify your email address");
        msg.setText(
                "Hi " + displayName + ",\n\n"
                + "Thank you for registering with Nabat. "
                + "Please verify your email address by clicking the link below:\n\n"
                + link + "\n\n"
                + "This link expires in 24 hours.\n\n"
                + "If you did not create an account, please ignore this message.\n\n"
                + "— The Nabat Team"
        );

        trySend(msg, "verification email to " + toEmail);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String displayName, String resetToken) {
        String link = baseUrl + "/api/v1/auth/reset-password?token=" + resetToken;

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(toEmail);
        msg.setSubject("Nabat — Password reset request");
        msg.setText(
                "Hi " + displayName + ",\n\n"
                + "We received a request to reset the password for your Nabat account.\n\n"
                + "Click the link below to choose a new password:\n\n"
                + link + "\n\n"
                + "This link expires in 1 hour.\n\n"
                + "If you did not request a password reset, please ignore this message "
                + "— your password will not be changed.\n\n"
                + "— The Nabat Team"
        );

        trySend(msg, "password-reset email to " + toEmail);
    }

    /**
     * Sends {@code msg} and logs the outcome. A {@link MailException} (e.g. SMTP
     * relay unreachable) is caught and recorded as an ERROR so that callers are not
     * interrupted by infrastructure unavailability.
     */
    private void trySend(SimpleMailMessage msg, String description) {
        try {
            mailSender.send(msg);
            log.info("Sent {}", description);
        } catch (MailException ex) {
            log.error("Failed to send {} — SMTP relay may be unavailable. "
                    + "Start MailHog or set MAIL_HOST/MAIL_PORT. Cause: {}", description, ex.getMessage());
        }
    }
}
