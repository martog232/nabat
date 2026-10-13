package org.example.nabat.incident.application;

import lombok.RequiredArgsConstructor;
import org.example.nabat.shared.UseCase;
import org.example.nabat.incident.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.incident.application.port.in.ListAlertsUseCase;
import org.example.nabat.incident.application.port.in.ListDistrictReportsUseCase;
import org.example.nabat.incident.application.port.in.ProgressReportUseCase;
import org.example.nabat.incident.application.port.in.RecordDeliveryUseCase;
import org.example.nabat.incident.application.port.in.ResolveAlertUseCase;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.AlertResolved;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.identity.domain.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

@UseCase
@RequiredArgsConstructor
public class AlertLifecycleService
        implements GetAlertByIdUseCase, ResolveAlertUseCase, ListAlertsUseCase, RecordDeliveryUseCase,
                   ProgressReportUseCase, ListDistrictReportsUseCase {

    private final AlertRepository alertRepository;
    private final AlertNotificationPort alertNotificationPort;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public Alert getById(AlertId id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> listByStatus(AlertStatus status) {
        return alertRepository.findByStatus(status);
    }

    /**
     * Resolving removes a report from the open set, so every cached nearby-alerts result is
     * now wrong. Evicting the whole cache is acceptable because entries are short-lived (15s
     * TTL) and status changes are rare compared with reads. Every transition below evicts for
     * the same reason: the status is on every cached map.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert resolve(AlertId id, User actor) {
        Alert resolved = transition(id, alert -> alert.resolve(closerOf(alert, actor, true)));
        // Through the Event Publication Registry, like AlertCreated: the reporter is asked
        // whether it is really fixed after this commits, and asked again if that fails.
        events.publishEvent(new AlertResolved(resolved));
        return resolved;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert acknowledge(AlertId id, User actor) {
        return transition(id, alert -> requireDistrict(alert, actor).acknowledge());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert startWork(AlertId id, User actor) {
        return transition(id, alert -> requireDistrict(alert, actor).startWork());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert reject(AlertId id, User actor) {
        return transition(id, alert -> alert.reject(closerOf(alert, actor, false)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> listForOwnDistrict(User actor, Set<AlertStatus> statuses) {
        if (!actor.role().canActForDistrict()) {
            throw new NotAuthorizedException("Only a district official has a district to list");
        }
        Set<AlertStatus> wanted = statuses == null || statuses.isEmpty() ? OPEN : statuses;
        return alertRepository.findInDistrict(actor.districtCode(), wanted, LIMIT);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public void recordDelivered(AlertId id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        if (alert.status() != AlertStatus.SUBMITTED) {
            return;
        }

        Alert sent = alertRepository.save(alert.markSent());
        alertNotificationPort.broadcastAlertUpdate(sent);
    }

    /**
     * Who is closing this report, or a refusal.
     *
     * <p>In order of standing: the report's own district first, because "fixed" from the body
     * responsible is the strongest statement there is; then its reporter; then a moderator.
     * An official of <em>another</em> district is none of these — the capability answers,
     * not the role, and for someone else's district it answers no.
     *
     * <p>The capability, not the role, for moderators too: closing someone else's report is
     * content moderation, and asking {@code role == ADMIN} is what once tied it to the one
     * role that can also disable accounts. The refusal is a domain exception, not Spring
     * Security's {@code AccessDeniedException} — AGENTS.md keeps framework exceptions out of
     * this layer, and the handler maps it to the same 403.
     *
     * @param reporterMay whether the reporter may make this closing: they may resolve their
     *                    own report, but not reject it as not a real problem
     */
    private static ClosedBy closerOf(Alert alert, User actor, boolean reporterMay) {
        if (actor.actsForDistrict(alert.districtCode())) {
            return ClosedBy.DISTRICT;
        }
        if (reporterMay && alert.reportedBy().equals(actor.id().value())) {
            return ClosedBy.REPORTER;
        }
        if (actor.role().canModerateContent()) {
            return ClosedBy.MODERATOR;
        }
        throw new NotAuthorizedException(reporterMay
            ? "Only the reporter, the report's district or a moderator can close this report"
            : "Only the report's district or a moderator can reject this report");
    }

    /** Only an official of the report's own district may speak for the district about it. */
    private static Alert requireDistrict(Alert alert, User actor) {
        if (!actor.actsForDistrict(alert.districtCode())) {
            throw new NotAuthorizedException("Only an official of the report's district can do this");
        }
        return alert;
    }

    /**
     * Load, change, save, announce — the shape every transition shares. The change is where
     * authorisation and the state machine both live, so a refusal of either leaves nothing
     * saved and nothing broadcast.
     */
    private Alert transition(AlertId id, UnaryOperator<Alert> change) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
        Alert changed = alertRepository.save(change.apply(alert));
        alertNotificationPort.broadcastAlertUpdate(changed);
        return changed;
    }
}
