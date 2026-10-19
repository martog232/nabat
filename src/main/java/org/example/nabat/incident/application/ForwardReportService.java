package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.ForwardReportUseCase;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.CommentStore;
import org.example.nabat.incident.application.port.out.ForwardStore;
import org.example.nabat.incident.application.port.out.ResponsibleAuthorityPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.ReportForward;
import org.example.nabat.incident.domain.ReportForwarded;
import org.example.nabat.shared.UseCase;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Forwarding by competence: the report moves to another authority, says so in its thread, and
 * is mailed there.
 *
 * <p>All in one transaction: the report's new authority and status, the forward on record,
 * and the line in the thread. The mail to the new authority and the reporter's notification
 * follow from {@link ReportForwarded} after commit, through the Event Publication Registry —
 * the same outbox as for a new report.
 */
@UseCase
public class ForwardReportService implements ForwardReportUseCase {

    private final AlertRepository alerts;
    private final ForwardStore forwards;
    private final CommentStore comments;
    private final ResponsibleAuthorityPort authorities;
    private final AlertNotificationPort broadcast;
    private final ApplicationEventPublisher events;

    public ForwardReportService(
        AlertRepository alerts,
        ForwardStore forwards,
        CommentStore comments,
        ResponsibleAuthorityPort authorities,
        AlertNotificationPort broadcast,
        ApplicationEventPublisher events
    ) {
        this.alerts = alerts;
        this.forwards = forwards;
        this.comments = comments;
        this.authorities = authorities;
        this.broadcast = broadcast;
        this.events = events;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert forward(AlertId id, User actor, String toAuthority, String reason) {
        String why = reason == null ? "" : reason.strip();
        if (why.isEmpty() || why.length() > ReportForward.MAX_REASON_LENGTH) {
            throw new IllegalArgumentException(
                "A forward says why, in 1 to " + ReportForward.MAX_REASON_LENGTH + " characters");
        }

        Alert alert = alerts.findById(id).orElseThrow(() -> new AlertNotFoundException(id));

        boolean byAuthority = actor.actsFor(alert.authorityCode());
        boolean byModerator = !byAuthority && actor.role().canModerateContent();
        if (!byAuthority && !byModerator) {
            throw new NotAuthorizedException("Only the report's authority or a moderator can forward it");
        }

        List<ReportForward> before = forwards.forwardsOf(id.value());
        if (byAuthority && before.size() >= FORWARDS_BEFORE_A_MODERATOR_DECIDES) {
            throw new NotAuthorizedException("This report has been forwarded " + before.size()
                + " times; a moderator decides where it goes now");
        }

        String toName = authorities.nameOf(toAuthority)
            .orElseThrow(() -> new IllegalArgumentException("No such authority: " + toAuthority));

        // Refuses a closed report and the authority it already has.
        Alert moved = alerts.save(alert.forwardTo(toAuthority));
        broadcast.broadcastAlertUpdate(moved);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID actorId = actor.id().value();
        ReportForward forward = new ReportForward(UUID.randomUUID(), id, before.size() + 1,
            alert.authorityCode(), toAuthority, actorId, byModerator, why, now);
        forwards.insert(forward);

        // In the thread, in the forwarding authority's name, so whoever follows the report
        // reads where it went and why without a second place to look.
        comments.insert(new Comment(UUID.randomUUID(), id, actorId, actor.displayName(), byAuthority,
            alert.reportedBy().equals(actorId), "Препратен към " + toName + ": " + why, now, null, null));

        events.publishEvent(new ReportForwarded(moved, forward, toName));
        return moved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportForward> forwards(AlertId id) {
        if (alerts.findById(id).isEmpty()) {
            throw new AlertNotFoundException(id);
        }
        return forwards.forwardsOf(id.value());
    }
}
