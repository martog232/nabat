package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.RejectReportUseCase;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.CommentStore;
import org.example.nabat.incident.application.port.out.RejectionStore;
import org.example.nabat.incident.application.port.out.ResponsibleAuthorityPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.RejectionDecided;
import org.example.nabat.incident.domain.ReportProgressed;
import org.example.nabat.incident.domain.ReportRejection;
import org.example.nabat.incident.domain.ReportRejection.Decision;
import org.example.nabat.shared.UseCase;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rejecting a report, disputing the rejection, and deciding the dispute.
 *
 * <p>Each step leaves a line in the report's thread, written straight to the store: the
 * thread is locked once a report is closed, and these are the record of why it closed and
 * what came of it, not conversation. The reporter hears of the rejection and of the decision
 * after commit, through the Event Publication Registry.
 */
@UseCase
public class RejectionService implements RejectReportUseCase {

    private final AlertRepository alerts;
    private final RejectionStore rejections;
    private final CommentStore comments;
    private final ResponsibleAuthorityPort authorities;
    private final AlertNotificationPort broadcast;
    private final ApplicationEventPublisher events;

    public RejectionService(
        AlertRepository alerts,
        RejectionStore rejections,
        CommentStore comments,
        ResponsibleAuthorityPort authorities,
        AlertNotificationPort broadcast,
        ApplicationEventPublisher events
    ) {
        this.alerts = alerts;
        this.rejections = rejections;
        this.comments = comments;
        this.authorities = authorities;
        this.broadcast = broadcast;
        this.events = events;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert reject(AlertId id, User actor, String reason) {
        String why = requireReason(reason, "A rejection");
        Alert alert = find(id);

        boolean byAuthority = actor.actsFor(alert.authorityCode());
        boolean byModerator = !byAuthority && actor.role().canModerateContent();
        if (!byAuthority && !byModerator) {
            throw new NotAuthorizedException("Only the report's authority or a moderator can reject this report");
        }

        // Refuses a report that is closed already.
        Alert rejected = alerts.save(alert.reject(byAuthority ? ClosedBy.AUTHORITY : ClosedBy.MODERATOR));
        broadcast.broadcastAlertUpdate(rejected);

        Instant now = now();
        UUID actorId = actor.id().value();
        rejections.insert(ReportRejection.of(id, actorId, byModerator, alert.authorityCode(), why, now));
        comments.insert(new Comment(UUID.randomUUID(), id, actorId, actor.displayName(), byAuthority,
            alert.reportedBy().equals(actorId), "Отхвърлен: " + why, now, null, null));

        events.publishEvent(new ReportProgressed(rejected, nameOf(rejected), why));
        return rejected;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportRejection> rejections(AlertId id) {
        find(id);
        return rejections.rejectionsOf(id.value());
    }

    @Override
    @Transactional
    public ReportRejection dispute(AlertId id, User reporter, String reason) {
        String why = requireReason(reason, "A dispute");
        Alert alert = find(id);
        UUID reporterId = reporter.id().value();
        if (!alert.reportedBy().equals(reporterId)) {
            throw new NotAuthorizedException("Only the reporter can dispute a rejection");
        }
        if (alert.status() != AlertStatus.REJECTED) {
            throw new IllegalStateException("Only a rejected report has a rejection to dispute");
        }

        Instant now = now();
        ReportRejection disputed = latest(id).dispute(why, now);
        rejections.update(disputed);
        comments.insert(new Comment(UUID.randomUUID(), id, reporterId, reporter.displayName(), false, true,
            "Оспорвам отхвърлянето: " + why, now, null, null));
        return disputed;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert decide(AlertId id, User moderator, Decision decision) {
        requireModerator(moderator);
        if (decision == null) {
            throw new IllegalArgumentException("A decision is required");
        }
        Alert alert = find(id);

        Instant now = now();
        UUID moderatorId = moderator.id().value();
        // Refuses a rejection nobody disputed, and one decided already.
        rejections.update(latest(id).decide(decision, moderatorId, now));

        Alert after = alert;
        if (decision == Decision.REOPENED) {
            after = alerts.save(alert.reopen());
            broadcast.broadcastAlertUpdate(after);
        }
        comments.insert(new Comment(UUID.randomUUID(), id, moderatorId, moderator.displayName(), false,
            alert.reportedBy().equals(moderatorId),
            decision == Decision.REOPENED
                ? "Модератор отвори сигнала отново след оспорването."
                : "Модератор потвърди отхвърлянето след оспорването.",
            now, null, null));

        events.publishEvent(new RejectionDecided(after, decision, nameOf(after)));
        return after;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alert> disputed(User moderator) {
        requireModerator(moderator);
        return rejections.openDisputes(DISPUTE_QUEUE_LIMIT).stream()
            .map(alertId -> alerts.findById(AlertId.of(alertId)))
            .flatMap(Optional::stream)
            .toList();
    }

    private Alert find(AlertId id) {
        return alerts.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
    }

    private ReportRejection latest(AlertId id) {
        return rejections.holdLatest(id.value())
            .orElseThrow(() -> new IllegalStateException("No rejection of this report is on record"));
    }

    private String nameOf(Alert alert) {
        return authorities.nameOf(alert.authorityCode()).orElse(alert.authorityCode());
    }

    private static void requireModerator(User actor) {
        if (!actor.role().canModerateContent()) {
            throw new NotAuthorizedException("Only a moderator decides a disputed rejection");
        }
    }

    private static String requireReason(String reason, String what) {
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty() || why.length() > ReportRejection.MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                what + " says why, in 1 to " + ReportRejection.MAX_REASON_LENGTH + " characters");
        }
        return why;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
