package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.CheckFixUseCase;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.FixCheckStore;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.FixCheckTally;
import org.example.nabat.incident.domain.FixVerdict;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.example.nabat.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Who may answer, and when.
 *
 * <p>Anyone signed in, about any resolved report — the people who walk past it are the
 * ones who know. Except an official of the report's own district: the check is of the
 * district's work, and an answer from the district would be the claim checking itself.
 * Officials of other districts are citizens here like anyone else.
 */
@UseCase
public class FixCheckService implements CheckFixUseCase {

    private final AlertRepository alerts;
    private final FixCheckStore store;
    private final AlertNotificationPort broadcasts;

    public FixCheckService(AlertRepository alerts, FixCheckStore store, AlertNotificationPort broadcasts) {
        this.alerts = alerts;
        this.store = store;
        this.broadcasts = broadcasts;
    }

    @Override
    @Transactional
    public FixCheckView check(AlertId id, User actor, FixVerdict verdict) {
        Alert alert = checkable(id, actor);
        boolean byReporter = alert.reportedBy().equals(actor.id().value());
        FixCheckTally tally = store.record(id.value(), actor.id().value(), verdict, byReporter, Instant.now());
        broadcasts.broadcastAlertUpdate(alert.withFixCheck(tally));
        return new FixCheckView(tally, verdict);
    }

    @Override
    @Transactional
    public FixCheckView withdraw(AlertId id, User actor) {
        Alert alert = checkable(id, actor);
        FixCheckTally tally = store.withdraw(id.value(), actor.id().value());
        broadcasts.broadcastAlertUpdate(alert.withFixCheck(tally));
        return new FixCheckView(tally, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FixCheckView view(AlertId id, User actor) {
        Alert alert = alerts.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
        FixVerdict mine = store.verdictOf(id.value(), actor.id().value()).orElse(null);
        return new FixCheckView(alert.fixCheck(), mine);
    }

    private Alert checkable(AlertId id, User actor) {
        Alert alert = alerts.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
        if (alert.status() != AlertStatus.RESOLVED) {
            throw new IllegalStateException("Only a resolved report can be checked; this one is " + alert.status());
        }
        if (actor.actsForDistrict(alert.districtCode())) {
            throw new NotAuthorizedException("The report's own district cannot check its own work");
        }
        return alert;
    }
}
