package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.WithdrawReportUseCase;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.CommentStore;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.ReportWithdrawn;
import org.example.nabat.shared.UseCase;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A reporter taking their report back: closed as {@code WITHDRAWN}, with why in the thread,
 * and its authority told after commit if it had the report already.
 */
@UseCase
public class WithdrawalService implements WithdrawReportUseCase {

    private final AlertRepository alerts;
    private final CommentStore comments;
    private final AlertNotificationPort broadcast;
    private final ApplicationEventPublisher events;

    public WithdrawalService(
        AlertRepository alerts,
        CommentStore comments,
        AlertNotificationPort broadcast,
        ApplicationEventPublisher events
    ) {
        this.alerts = alerts;
        this.comments = comments;
        this.broadcast = broadcast;
        this.events = events;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert withdraw(AlertId id, User reporter, String reason) {
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty() || why.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("A withdrawal says why, in 1 to " + MAX_REASON_LENGTH + " characters");
        }
        Alert alert = alerts.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
        UUID reporterId = reporter.id().value();
        if (!alert.reportedBy().equals(reporterId)) {
            throw new NotAuthorizedException("Only its reporter can withdraw a report");
        }

        // Refuses a closed report.
        Alert withdrawn = alerts.save(alert.withdraw());
        broadcast.broadcastAlertUpdate(withdrawn);

        // Written to the store directly: the withdrawal closes the thread, and this is the record
        // of why, not conversation.
        comments.insert(new Comment(UUID.randomUUID(), id, reporterId, reporter.displayName(),
            reporter.actsFor(alert.authorityCode()), true, "Оттеглен от подателя: " + why,
            Instant.now().truncatedTo(ChronoUnit.MICROS), null, null));

        events.publishEvent(new ReportWithdrawn(withdrawn, why, alert.status() != AlertStatus.SUBMITTED));
        return withdrawn;
    }
}
