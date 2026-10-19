package org.example.nabat.jurisdiction.application;

import org.example.nabat.identity.application.port.in.SignInLinkUseCase;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.incident.application.port.in.ListReportCategoriesUseCase;
import org.example.nabat.incident.application.port.in.RecordDeliveryUseCase;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.incident.domain.ReportForward;
import org.example.nabat.incident.domain.ReportForwarded;
import org.example.nabat.jurisdiction.application.port.in.ListAuthoritiesUseCase;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalDispatchRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail;
import org.example.nabat.jurisdiction.domain.Authority;
import org.example.nabat.jurisdiction.domain.District;
import org.example.nabat.jurisdiction.domain.SignalDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Sends each new report to the authority that answers for it — its district, or the
 * institution its category belongs to — and each forwarded one to the authority it was
 * forwarded to, and records that it went.
 *
 * <h2>Durability</h2>
 * An {@link ApplicationModuleListener}, so the Event Publication Registry writes a row for
 * this listener in the transaction that creates the report, and completes it only when this
 * method returns. A failed send throws, the row stays outstanding, and the report is sent
 * again — on the next restart, or when outstanding publications are resubmitted. This is
 * the outbox the target architecture asks for, reached without writing one: the registry
 * already is one.
 *
 * <h2>At least once, so not twice</h2>
 * A replay would send the authority a second copy. So a {@link SignalDispatch} is written in
 * the same transaction as the move to {@code SENT}, and a replay that finds one skips the
 * mail and only makes sure the status caught up. What that leaves is the one window no
 * outbox closes: the mail has left, and the transaction then fails to commit. The next
 * attempt sends it again. Rare, and a duplicate report is a much smaller failure than a
 * lost one.
 */
@Component
public class SignalDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SignalDispatcher.class);

    private final DistrictRepository districts;
    private final ListAuthoritiesUseCase authorities;
    private final SignalDispatchRepository dispatches;
    private final SignalMailer mailer;
    private final UserRepository users;
    private final RecordDeliveryUseCase recordDelivery;
    private final ListReportCategoriesUseCase categories;
    private final GetAlertByIdUseCase reports;
    private final SignInLinkUseCase signInLinks;

    public SignalDispatcher(
        DistrictRepository districts,
        ListAuthoritiesUseCase authorities,
        SignalDispatchRepository dispatches,
        SignalMailer mailer,
        UserRepository users,
        RecordDeliveryUseCase recordDelivery,
        ListReportCategoriesUseCase categories,
        GetAlertByIdUseCase reports,
        SignInLinkUseCase signInLinks
    ) {
        this.districts = districts;
        this.authorities = authorities;
        this.dispatches = dispatches;
        this.mailer = mailer;
        this.users = users;
        this.recordDelivery = recordDelivery;
        this.categories = categories;
        this.reports = reports;
        this.signInLinks = signInLinks;
    }

    @ApplicationModuleListener
    public void on(AlertCreated event) {
        deliver(event.alert(), 0, null);
    }

    /**
     * The report went to another authority: the same delivery, to the new one, with a note of
     * who sent it on and why. Its own registry rows and its own dispatch record (the forward's
     * sequence), so it is retried and deduplicated apart from the creation mail.
     */
    @ApplicationModuleListener
    public void on(ReportForwarded event) {
        ReportForward forward = event.forward();
        String from = authorities.findAuthority(forward.fromAuthority())
            .map(Authority::name)
            .orElse(forward.fromAuthority());
        deliver(event.alert(), forward.sequence(), "Препратен към вас от " + from + ": " + forward.reason());
    }

    /**
     * @param sequence    which delivery this is: 0 for the creation mail, n for the n-th forward
     * @param forwardNote said at the top of a forwarded report's mail; null otherwise
     */
    private void deliver(Alert alert, int sequence, String forwardNote) {
        UUID id = alert.id().value();

        if (dispatches.exists(id, sequence)) {
            // Sent on an earlier attempt that did not get to complete. The status may still
            // be behind; recording is idempotent, so catch it up and stop.
            recordDelivery.recordDelivered(alert.id(), alert.authorityCode());
            return;
        }

        if (alert.districtCode() == null) {
            // Cannot happen for a report created since V18 — creation refuses one outside
            // every district. Logged rather than thrown: a retry would not give it one.
            log.warn("Report {} has no district and cannot be sent", id);
            return;
        }

        // The event carries the report as it was. If it has been forwarded since, this mail
        // would go to an authority it is no longer waiting on — the forward's own delivery
        // reaches the right one.
        String current = reports.getById(alert.id()).authorityCode();
        if (!current.equals(alert.authorityCode())) {
            log.info("Report {} was forwarded to {} before its mail to {} left; not sent",
                id, current, alert.authorityCode());
            return;
        }

        District district = districts.findByCode(alert.districtCode())
            .orElseThrow(() -> new IllegalStateException(
                "Report " + id + " names unknown district " + alert.districtCode()));

        Authority authority = authorities.findAuthority(alert.authorityCode())
            .orElseThrow(() -> new IllegalStateException(
                "Report " + id + " names unknown authority " + alert.authorityCode()));

        User reporter = users.findById(UserId.of(alert.reportedBy()))
            .orElseThrow(() -> new IllegalStateException(
                "Report " + id + " has no reporter account"));

        mailer.send(new SignalMail(
            id,
            authority.mailbox(),
            authority.name(),
            district.name(),
            categoryLabel(alert.categoryCode()),
            alert.title(),
            alert.description(),
            alert.location().latitude(),
            alert.location().longitude(),
            alert.createdAt(),
            reporter.displayName(),
            reporter.email(),
            forwardNote,
            // A fresh link with every mail, so it is good for as long as the report is new.
            signInLinks.issueFor(authority.code()).orElse(null)
        ));

        dispatches.save(new SignalDispatch(
            id, sequence, district.code(), authority.code(), authority.mailbox(), Instant.now()));
        recordDelivery.recordDelivered(alert.id(), authority.code());

        log.info("Sent report {} to {} (delivery {})", id, authority.code(), sequence);
    }

    /**
     * A code that names no subcategory cannot reach here — creation refuses it and the
     * foreign key holds it — but a report must not stay unsent over a missing label, so
     * the code itself stands in.
     */
    private String categoryLabel(int code) {
        return categories.findSubcategory(code)
            .map(ListReportCategoriesUseCase.CategoryGroup.Placed::label)
            .orElse("Категория " + code);
    }
}
