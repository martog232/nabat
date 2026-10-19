package org.example.nabat.notification.application.port.in;

import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.notification.domain.Notification;
import org.example.nabat.notification.domain.NotificationType;
import org.example.nabat.identity.domain.UserId;

public interface SendNotificationUseCase {

    Notification sendVoteNotification(VoteNotificationCommand command);

    Notification sendMilestoneNotification(MilestoneNotificationCommand command);

    /**
     * @param type which vote-derived notification to raise. Deliberately this module's
     *             own {@link NotificationType} rather than voting's {@code VoteType}:
     *             taking the caller's vocabulary made this module depend on voting,
     *             which already depends on this one, and Spring Modulith rejected the
     *             cycle. Callers translate their concept into ours.
     */
    record VoteNotificationCommand(
            UserId alertOwnerId,      // owner of the alert
            UserId voterId,           // user who cast the vote
            AlertId alertId,          // alert that was voted on
            String alertTitle,        // title of the alert
            NotificationType type
    ) {
    }

    /**
     * Tells a reporter their report was closed as resolved by someone else, and asks whether
     * it really is fixed.
     */
    Notification sendResolvedNotification(ResolvedNotificationCommand command);

    /** @param byAuthority true if the report's authority closed it, false if a moderator did */
    record ResolvedNotificationCommand(UserId reporterId, AlertId alertId, String alertTitle, boolean byAuthority) {
    }

    /** Tells one person in a report's thread that someone wrote in it. */
    Notification sendCommentNotification(CommentNotificationCommand command);

    /** @param byAuthority true if the report's authority wrote it, which the message says instead of a name */
    record CommentNotificationCommand(
            UserId recipientId,
            AlertId alertId,
            String alertTitle,
            UserId authorId,
            String authorName,
            boolean byAuthority
    ) {
    }

    /**
     * Tells a reporter what the authority did with their report: took it, started on it, or
     * turned it down. Resolving and forwarding have their own.
     */
    Notification sendProgressNotification(ProgressNotificationCommand command);

    /** The moves a reporter hears about through {@link #sendProgressNotification}. */
    enum Progress { ACKNOWLEDGED, IN_PROGRESS, REJECTED, REJECTION_UPHELD, REOPENED }

    /**
     * @param byModerator a rejection by a moderator rather than by the authority
     * @param reason      why, for a rejection; null otherwise
     */
    record ProgressNotificationCommand(
            UserId reporterId,
            AlertId alertId,
            String alertTitle,
            Progress progress,
            String authorityName,
            boolean byModerator,
            String reason
    ) {
    }

    /** Tells a reporter their report went to another authority, and why. */
    Notification sendForwardedNotification(ForwardedNotificationCommand command);

    record ForwardedNotificationCommand(UserId reporterId, AlertId alertId, String alertTitle, String toAuthorityName, String reason) {
    }

    record MilestoneNotificationCommand(
            UserId alertOwnerId,           // owner of the alert
            AlertId alertId,               // alert that reached the milestone
            String milestoneTitle,         // title of the alert
            int confirmationCount          // number of confirmations at milestone
    ) {
    }
}
