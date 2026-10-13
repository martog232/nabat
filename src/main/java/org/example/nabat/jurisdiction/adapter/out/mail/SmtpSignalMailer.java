package org.example.nabat.jurisdiction.adapter.out.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writes a report as a plain-text email, in Bulgarian, for a district administration.
 *
 * <p>Nothing is caught. Any {@link MailException} propagates to {@code SignalDispatcher},
 * whose publication then stays outstanding — see {@link SignalMailer} for why that matters.
 *
 * <p>The photo is not linked. {@code GET /api/v1/uploads/*} requires a nabat session, so a
 * link would be a 401 for everyone who receives this; it belongs here once photos are served
 * through presigned URLs (phase 4).
 */
@Component
public class SmtpSignalMailer implements SignalMailer {

    /** The district administrations are in Sofia, and so are the people reading this. */
    private static final DateTimeFormatter REPORTED_AT = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm", Locale.forLanguageTag("bg"))
        .withZone(ZoneId.of("Europe/Sofia"));

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpSignalMailer(
        JavaMailSender mailSender,
        @Value("${nabat.mail.from:noreply@nabat.local}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(SignalMail mail) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(mail.to());
            // An answer from the district goes to the citizen who reported, not to nabat.
            helper.setReplyTo(mail.reporterEmail(), mail.reporterName());
            helper.setSubject(subject(mail));
            helper.setText(body(mail));
            // Stable across retries, so a receiving system can recognise a second copy.
            message.setHeader("X-Nabat-Signal-Id", mail.alertId().toString());
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new MailSendException("Could not build the report email for " + mail.alertId(), e);
        }
        mailSender.send(message);
    }

    static String subject(SignalMail mail) {
        return "Сигнал: " + mail.categoryLabel() + " — " + mail.title();
    }

    static String body(SignalMail mail) {
        String coordinates = String.format(Locale.ROOT, "%.6f, %.6f", mail.latitude(), mail.longitude());
        String map = String.format(Locale.ROOT,
            "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=19/%.6f/%.6f",
            mail.latitude(), mail.longitude(), mail.latitude(), mail.longitude());

        return """
            Уважаеми служители на %s,

            Чрез nabat е подаден сигнал за проблем на територията на района.

            Вид:       %s
            Заглавие:  %s
            Описание:  %s

            Място:     %s
            Карта:     %s
            Подаден:   %s

            Подател:   %s <%s>
            Отговорът на този имейл отива директно при подателя.

            Номер на сигнала: %s
            """.formatted(
                mail.districtName(),
                mail.categoryLabel(),
                mail.title(),
                mail.description(),
                coordinates,
                map,
                REPORTED_AT.format(mail.reportedAt()),
                mail.reporterName(),
                mail.reporterEmail(),
                mail.alertId()
            );
    }

}
