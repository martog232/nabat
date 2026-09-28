package org.example.nabat.jurisdiction.adapter.out.mail;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpSignalMailerTest {

    private static final UUID ALERT_ID = UUID.fromString("0b7f5a4e-2f3c-4c6e-9d1a-2a3b4c5d6e7f");

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SmtpSignalMailer mailer = new SmtpSignalMailer(sender, "noreply@nabat.local");

    private static SignalMail mail() {
        return new SignalMail(
            ALERT_ID, "lozenets@rayon.nabat.test", "Район Лозенец", AlertType.POTHOLE,
            "Дупка на Черни връх", "Дълбока дупка в дясното платно",
            42.671234, 23.318765,
            Instant.parse("2026-09-28T16:42:00Z"),
            "Иван Петров", "ivan@example.com"
        );
    }

    private MimeMessage sent() throws Exception {
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        mailer.send(mail());
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void addressesTheDistrictAndLetsItAnswerTheCitizen() throws Exception {
        MimeMessage message = sent();

        assertThat(message.getRecipients(Message.RecipientType.TO))
            .extracting(Address::toString)
            .containsExactly("lozenets@rayon.nabat.test");
        assertThat(((InternetAddress) message.getReplyTo()[0]).getAddress()).isEqualTo("ivan@example.com");
        assertThat(message.getHeader("X-Nabat-Signal-Id")).containsExactly(ALERT_ID.toString());
    }

    /** Cyrillic survives the trip: a subject mangled into '?' is a report nobody can file. */
    @Test
    void writesTheSubjectAndBodyInBulgarian() throws Exception {
        MimeMessage message = sent();

        assertThat(message.getSubject()).isEqualTo("Сигнал: Дупка на пътя или тротоара — Дупка на Черни връх");
        assertThat((String) message.getContent())
            .contains("Уважаеми служители на Район Лозенец")
            .contains("Дълбока дупка в дясното платно")
            .contains("Подател:   Иван Петров <ivan@example.com>")
            .contains("Номер на сигнала: " + ALERT_ID);
    }

    /** Sofia time, not UTC: 16:42Z is 19:42 in late September. */
    @Test
    void givesTheTimeInSofia() {
        assertThat(SmtpSignalMailer.body(mail())).contains("Подаден:   28.09.2026 19:42");
    }

    /** Dot decimals whatever the JVM locale — a comma would break the map link. */
    @Test
    void linksToThePlaceOnAMap() {
        assertThat(SmtpSignalMailer.body(mail()))
            .contains("Място:     42.671234, 23.318765")
            .contains("https://www.openstreetmap.org/?mlat=42.671234&mlon=23.318765#map=19/42.671234/23.318765");
    }

    @ParameterizedTest
    @EnumSource(AlertType.class)
    void namesEveryCategoryInBulgarian(AlertType type) {
        assertThat(SmtpSignalMailer.label(type)).isNotBlank().doesNotContain("_");
    }

    /** Swallowing this would turn a report the district never got into one nabat calls sent. */
    @Test
    void doesNotSwallowAFailedSend() {
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("connection refused")).when(sender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () -> mailer.send(mail()));
    }
}
