package org.example.nabat.jurisdiction.application;

import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.application.port.in.RecordDeliveryUseCase;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalDispatchRepository;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail;
import org.example.nabat.jurisdiction.domain.District;
import org.example.nabat.jurisdiction.domain.SignalDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Sends each new report to the district it is in, and records that it went.
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
 * A replay would send the district a second copy. So a {@link SignalDispatch} is written in
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
    private final SignalDispatchRepository dispatches;
    private final SignalMailer mailer;
    private final UserRepository users;
    private final RecordDeliveryUseCase recordDelivery;

    public SignalDispatcher(
        DistrictRepository districts,
        SignalDispatchRepository dispatches,
        SignalMailer mailer,
        UserRepository users,
        RecordDeliveryUseCase recordDelivery
    ) {
        this.districts = districts;
        this.dispatches = dispatches;
        this.mailer = mailer;
        this.users = users;
        this.recordDelivery = recordDelivery;
    }

    @ApplicationModuleListener
    public void on(AlertCreated event) {
        Alert alert = event.alert();

        if (dispatches.existsForAlert(alert.id().value())) {
            // Sent on an earlier attempt that did not get to complete. The status may still
            // be behind; recording is idempotent, so catch it up and stop.
            recordDelivery.recordDelivered(alert.id());
            return;
        }

        if (alert.districtCode() == null) {
            // Cannot happen for a report created since V18 — creation refuses one outside
            // every district. Logged rather than thrown: a retry would not give it one.
            log.warn("Report {} has no district and cannot be sent", alert.id().value());
            return;
        }

        District district = districts.findByCode(alert.districtCode())
            .orElseThrow(() -> new IllegalStateException(
                "Report " + alert.id().value() + " names unknown district " + alert.districtCode()));

        User reporter = users.findById(UserId.of(alert.reportedBy()))
            .orElseThrow(() -> new IllegalStateException(
                "Report " + alert.id().value() + " has no reporter account"));

        mailer.send(new SignalMail(
            alert.id().value(),
            district.signalEmail(),
            district.name(),
            alert.type(),
            alert.title(),
            alert.description(),
            alert.location().latitude(),
            alert.location().longitude(),
            alert.createdAt(),
            reporter.displayName(),
            reporter.email()
        ));

        dispatches.save(new SignalDispatch(
            alert.id().value(), district.code(), district.signalEmail(), Instant.now()));
        recordDelivery.recordDelivered(alert.id());

        log.info("Sent report {} to {}", alert.id().value(), district.code());
    }
}
