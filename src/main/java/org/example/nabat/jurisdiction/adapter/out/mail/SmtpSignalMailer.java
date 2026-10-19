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
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writes a report as an email, in Bulgarian, for the authority that answers for it: plain text,
 * and the same in HTML with a button that opens the report in nabat, where the authority
 * answers.
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

    /** The authorities are in Sofia, and so are the people reading this. */
    private static final DateTimeFormatter REPORTED_AT = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm", Locale.forLanguageTag("bg"))
        .withZone(ZoneId.of("Europe/Sofia"));

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendBaseUrl;

    public SmtpSignalMailer(
        JavaMailSender mailSender,
        @Value("${nabat.mail.from:noreply@nabat.local}") String from,
        @Value("${nabat.frontend.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
            ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
            : frontendBaseUrl;
    }

    @Override
    public void send(SignalMail mail) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            // Multipart: the HTML carries the button, the text is there for clients that show no HTML.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(mail.to());
            // No Reply-To. An answer by mail would reach one person and leave nabat at SENT;
            // the button takes the authority to the report, where the answer is public and
            // moves the status. A reply goes to the From address, which nobody reads.
            helper.setSubject(subject(mail));
            String link = reportLink(mail);
            helper.setText(body(mail, link), html(mail, link));
            // Stable across retries, so a receiving system can recognise a second copy.
            message.setHeader("X-Nabat-Signal-Id", mail.alertId().toString());
        } catch (MessagingException e) {
            throw new MailSendException("Could not build the report email for " + mail.alertId(), e);
        }
        mailSender.send(message);
    }

    static String subject(SignalMail mail) {
        String kind = switch (mail.kind()) {
            case NEW -> "Сигнал: ";
            case FORWARDED -> "Препратен сигнал: ";
            case REMINDER -> "Напомняне — сигнал без отговор: ";
            case ESCALATION -> "Ескалиран сигнал без отговор: ";
        };
        return kind + mail.categoryLabel() + " — " + mail.title();
    }

    /**
     * What the mail is: a new report — or, said first because it is what the recipient has to
     * judge, who forwarded it and why, or how long it has waited for an answer.
     */
    private static String intro(SignalMail mail) {
        return mail.note() == null
            ? "Чрез nabat е подаден сигнал за проблем, за който отговаряте."
            : mail.note();
    }

    /**
     * The report on nabat's map. The authority answers there, signed in with the account that
     * acts for it: acknowledge, start work, resolve or reject, and a comment marked as the
     * authority's.
     *
     * <p>With a sign-in key, the link goes through nabat-fe's {@code /link}, which trades the key
     * for a session and opens the report: the official has no password to type, and should not
     * need one. Without one — an authority with no account — it opens the report, and the login
     * page brings whoever signs in back to it. The key is base64url, safe in a URL as it is.
     */
    String reportLink(SignalMail mail) {
        return mail.signInKey() == null
            ? frontendBaseUrl + "/map?alert=" + mail.alertId()
            : frontendBaseUrl + "/link?key=" + mail.signInKey() + "&alert=" + mail.alertId();
    }

    private static String coordinates(SignalMail mail) {
        return String.format(Locale.ROOT, "%.6f, %.6f", mail.latitude(), mail.longitude());
    }

    private static String mapLink(SignalMail mail) {
        return String.format(Locale.ROOT,
            "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=19/%.6f/%.6f",
            mail.latitude(), mail.longitude(), mail.latitude(), mail.longitude());
    }

    static String body(SignalMail mail, String reportLink) {
        return """
            Уважаеми служители на %s,

            %s

            Вид:       %s
            Заглавие:  %s
            Описание:  %s

            Район:     %s
            Място:     %s
            Карта:     %s
            Подаден:   %s

            Подател:   %s <%s>

            Отговорете и сменете статуса в nabat:
            %s

            Моля, не отговаряйте на този имейл: отговорът няма да бъде прочетен.

            Номер на сигнала: %s
            """.formatted(
                mail.recipientName(),
                intro(mail),
                mail.categoryLabel(),
                mail.title(),
                mail.description(),
                mail.districtName(),
                coordinates(mail),
                mapLink(mail),
                REPORTED_AT.format(mail.reportedAt()),
                mail.reporterName(),
                mail.reporterEmail(),
                reportLink,
                mail.alertId()
            );
    }

    /**
     * The same content as {@link #body}, with the button. Everything the reporter wrote is
     * escaped: a title is not markup, and an unescaped one would let any citizen put a link
     * of their choosing into a mail that reaches an administration as nabat's.
     *
     * <p>The button is a table cell with inline styles, because that is what mail clients
     * render alike. Outlook ignores most CSS on an {@code <a>}.
     */
    static String html(SignalMail mail, String reportLink) {
        return """
            <div style="font-family:Arial,sans-serif;font-size:14px;color:#1e293b;line-height:1.5">
            <p>Уважаеми служители на %s,</p>
            <p>%s</p>
            <table style="border-collapse:collapse;margin:12px 0">
            <tr><td style="padding:2px 12px 2px 0;color:#64748b">Вид</td><td>%s</td></tr>
            <tr><td style="padding:2px 12px 2px 0;color:#64748b">Заглавие</td><td><b>%s</b></td></tr>
            <tr><td style="padding:2px 12px 2px 0;color:#64748b;vertical-align:top">Описание</td><td style="white-space:pre-wrap">%s</td></tr>
            <tr><td style="padding:2px 12px 2px 0;color:#64748b">Район</td><td>%s</td></tr>
            <tr><td style="padding:2px 12px 2px 0;color:#64748b">Място</td><td><a href="%s">%s</a></td></tr>
            <tr><td style="padding:2px 12px 2px 0;color:#64748b">Подаден</td><td>%s</td></tr>
            <tr><td style="padding:2px 12px 2px 0;color:#64748b">Подател</td><td>%s &lt;%s&gt;</td></tr>
            </table>
            <table role="presentation" style="border-collapse:collapse;margin:20px 0">
            <tr><td style="background:#2563eb;border-radius:8px">
            <a href="%s" style="display:inline-block;padding:12px 28px;color:#ffffff;text-decoration:none;font-weight:bold">Отговори</a>
            </td></tr>
            </table>
            <p style="color:#64748b;font-size:12px">Бутонът отваря сигнала в nabat, където отговаряте и сменяте статуса му. Моля, не отговаряйте на този имейл: отговорът няма да бъде прочетен.<br>Номер на сигнала: %s</p>
            </div>
            """.formatted(
                esc(mail.recipientName()),
                esc(intro(mail)),
                esc(mail.categoryLabel()),
                esc(mail.title()),
                esc(mail.description()),
                esc(mail.districtName()),
                esc(mapLink(mail)),
                esc(coordinates(mail)),
                esc(REPORTED_AT.format(mail.reportedAt())),
                esc(mail.reporterName()),
                esc(mail.reporterEmail()),
                esc(reportLink),
                mail.alertId()
            );
    }

    private static String esc(String text) {
        return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }

}
