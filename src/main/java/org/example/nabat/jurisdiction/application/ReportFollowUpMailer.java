package org.example.nabat.jurisdiction.application;

import org.example.nabat.identity.application.port.in.SignInLinkUseCase;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.incident.application.port.in.ListReportCategoriesUseCase;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AnswerOverdue;
import org.example.nabat.incident.domain.ReportWithdrawn;
import org.example.nabat.jurisdiction.application.port.in.ListAuthoritiesUseCase;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail.Kind;
import org.example.nabat.jurisdiction.domain.Authority;
import org.example.nabat.jurisdiction.domain.District;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * The mails about a report after its first: to an authority that has left it past the answer
 * deadline, as a reminder, and — still nothing well after that — to the municipality, which the
 * districts and the city's institutions answer to ({@code nabat.reports.escalate-to}, V34); and
 * to the authority of a report its reporter has withdrawn, so it stops working on it.
 *
 * <p>Each overdue step is published once per deadline (see {@code DeadlineStepStore.take}), so this
 * keeps no record of its own: a replay after a failed send mails again, which is the outbox's
 * usual bargain. The report is re-read first, so one answered since the step was taken is not
 * chased.
 */
@Component
public class ReportFollowUpMailer {

    private static final Logger log = LoggerFactory.getLogger(ReportFollowUpMailer.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter
        .ofPattern("dd.MM.yyyy", Locale.forLanguageTag("bg"))
        .withZone(ZoneId.of("Europe/Sofia"));

    private final DistrictRepository districts;
    private final ListAuthoritiesUseCase authorities;
    private final SignalMailer mailer;
    private final UserRepository users;
    private final ListReportCategoriesUseCase categories;
    private final GetAlertByIdUseCase reports;
    private final SignInLinkUseCase signInLinks;
    private final String escalateTo;

    public ReportFollowUpMailer(
        DistrictRepository districts,
        ListAuthoritiesUseCase authorities,
        SignalMailer mailer,
        UserRepository users,
        ListReportCategoriesUseCase categories,
        GetAlertByIdUseCase reports,
        SignInLinkUseCase signInLinks,
        @Value("${nabat.reports.escalate-to:STOLICHNA_OBSHTINA}") String escalateTo
    ) {
        this.districts = districts;
        this.authorities = authorities;
        this.mailer = mailer;
        this.users = users;
        this.categories = categories;
        this.reports = reports;
        this.signInLinks = signInLinks;
        this.escalateTo = escalateTo;
    }

    @ApplicationModuleListener
    public void on(AnswerOverdue event) {
        Alert alert = reports.getById(event.alert().id());
        if (!event.dueAt().equals(alert.answerDueAt()) || !alert.isOverdue(Instant.now())) {
            log.info("Report {} was answered or moved on before its {} left; not sent", alert.id().value(), event.stage());
            return;
        }

        Authority answering = authority(alert.authorityCode());
        String due = DATE.format(event.dueAt());
        switch (event.stage()) {
            case REMINDER -> send(alert, answering, Kind.REMINDER,
                "Напомняне: този сигнал още чака отговор от вас. Срокът за отговор изтече на " + due + ".");
            case ESCALATION -> {
                if (escalateTo.equals(answering.code())) {
                    // The municipality is the one silent; there is nobody above it to tell here.
                    log.info("Report {} is overdue with {} itself; nothing to escalate to", alert.id().value(), escalateTo);
                    return;
                }
                send(alert, authority(escalateTo), Kind.ESCALATION,
                    answering.name() + " не е отговорил на този сигнал. Срокът за отговор изтече на " + due
                        + ", а напомнянето след това остана без отговор.");
            }
        }
    }

    /**
     * Withdrawn by its reporter: the authority that has it — and only one that has it — is told
     * it can stop. A report withdrawn before its mail left was never anyone's to stop.
     */
    @ApplicationModuleListener
    public void on(ReportWithdrawn event) {
        if (!event.delivered()) {
            return;
        }
        Alert alert = event.alert();
        send(alert, authority(alert.authorityCode()), Kind.WITHDRAWN,
            "Подателят оттегли този сигнал: „" + event.reason() + "“. Не е нужно да отговаряте повече.");
    }

    private void send(Alert alert, Authority to, Kind kind, String note) {
        District district = districts.findByCode(alert.districtCode())
            .orElseThrow(() -> new IllegalStateException("Report " + alert.id().value() + " has no known district"));
        User reporter = users.findById(UserId.of(alert.reportedBy()))
            .orElseThrow(() -> new IllegalStateException("Report " + alert.id().value() + " has no reporter account"));

        mailer.send(new SignalMail(
            alert.id().value(),
            to.mailbox(),
            to.name(),
            district.name(),
            categories.findSubcategory(alert.categoryCode())
                .map(ListReportCategoriesUseCase.CategoryGroup.Placed::label)
                .orElse("Категория " + alert.categoryCode()),
            alert.title(),
            alert.description(),
            alert.location().latitude(),
            alert.location().longitude(),
            alert.createdAt(),
            reporter.displayName(),
            reporter.email(),
            note,
            signInLinks.issueFor(to.code()).orElse(null),
            kind
        ));
        log.info("Sent {} for report {} to {}", kind, alert.id().value(), to.code());
    }

    private Authority authority(String code) {
        Optional<Authority> found = authorities.findAuthority(code);
        return found.orElseThrow(() -> new IllegalStateException("Unknown authority " + code));
    }
}
