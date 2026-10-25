package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.CommentOnReportUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.CommentStore;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertNotFoundException;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.CommentAdded;
import org.example.nabat.incident.domain.CommentNotFoundException;
import org.example.nabat.incident.domain.CommentRemoval;
import org.example.nabat.incident.domain.CommentsLockedException;
import org.example.nabat.shared.UseCase;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Who may write in a report's thread, and when.
 *
 * <p>Anyone signed in, while the report is open — the authority that answers for it included,
 * since a thread where the authority cannot reply is only a complaints box. Its comments are
 * marked as the authority's, so they do not get lost among the rest.
 *
 * <p>Closed with the report. Once it is resolved or rejected, both of which are final, the
 * thread is kept as it was: nothing added, and authors cannot take their words back either,
 * because a conversation that can still be edited after the fact is not a record of one.
 * Moderators can still take a comment down — a closed thread is frozen for its participants,
 * not for abuse. What remains to say about a resolved report is whether it really is fixed,
 * and "is it really fixed?" stays open for that.
 */
@UseCase
public class CommentService implements CommentOnReportUseCase {

    private final AlertRepository alerts;
    private final CommentStore store;
    private final ApplicationEventPublisher events;

    public CommentService(AlertRepository alerts, CommentStore store, ApplicationEventPublisher events) {
        this.alerts = alerts;
        this.store = store;
        this.events = events;
    }

    @Override
    @Transactional
    public Comment add(AlertId alertId, User author, String body) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > Comment.MAX_LENGTH) {
            throw new IllegalArgumentException("A comment is 1 to " + Comment.MAX_LENGTH + " characters");
        }
        Alert alert = alerts.findById(alertId).orElseThrow(() -> new AlertNotFoundException(alertId));
        requireOpen(alertId);

        UUID authorId = author.id().value();
        boolean byAuthority = author.actsFor(alert.authorityCode());
        // The reporter and everyone who has spoken here, but never the author: nobody needs
        // telling about what they just wrote.
        Set<UUID> recipients = new HashSet<>(store.authorsOn(alertId.value()));
        recipients.add(alert.reportedBy());
        recipients.remove(authorId);

        // Microseconds, as TIMESTAMPTZ keeps them, so the comment returned is the one stored.
        Comment comment = new Comment(UUID.randomUUID(), alertId, authorId, author.displayName(), byAuthority,
            alert.reportedBy().equals(authorId), text, Instant.now().truncatedTo(ChronoUnit.MICROS), null, null);
        store.insert(comment);

        if (!recipients.isEmpty()) {
            // Through the Event Publication Registry: told after this commits, and again if
            // that fails, like the reporter's question when a report is resolved.
            events.publishEvent(new CommentAdded(alertId, alert.title(), authorId, author.displayName(),
                byAuthority, Set.copyOf(recipients)));
        }
        return comment;
    }

    @Override
    @Transactional
    public Comment remove(AlertId alertId, UUID commentId, User actor) {
        Comment comment = store.find(alertId.value(), commentId)
            .orElseThrow(() -> new CommentNotFoundException(alertId, commentId));
        if (comment.isRemoved()) {
            return comment;
        }

        CommentRemoval by;
        if (actor.role().canModerateContent()) {
            // Checked before authorship, so a moderator may do it in a closed thread too. Their
            // own comment is still recorded as taken down by its author, which is what it was.
            by = comment.isBy(actor.id().value()) ? CommentRemoval.AUTHOR : CommentRemoval.MODERATOR;
        } else if (comment.isBy(actor.id().value())) {
            by = CommentRemoval.AUTHOR;
            requireOpen(alertId);
        } else {
            throw new NotAuthorizedException("Only its author or a moderator can take a comment down");
        }

        Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
        store.markRemoved(commentId, at, by);
        return new Comment(comment.id(), comment.alertId(), comment.authorId(), comment.authorName(),
            comment.byAuthority(), comment.byReporter(), null, comment.createdAt(), at, by);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comment> thread(AlertId alertId) {
        if (alerts.findById(alertId).isEmpty()) {
            throw new AlertNotFoundException(alertId);
        }
        return store.thread(alertId.value());
    }

    private void requireOpen(AlertId alertId) {
        AlertStatus status = store.holdReport(alertId.value());
        if (!status.isOpen()) {
            throw new CommentsLockedException(alertId, status);
        }
    }
}
