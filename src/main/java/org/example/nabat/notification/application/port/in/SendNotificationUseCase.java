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

    record MilestoneNotificationCommand(
            UserId alertOwnerId,           // owner of the alert
            AlertId alertId,               // alert that reached the milestone
            String milestoneTitle,         // title of the alert
            int confirmationCount          // number of confirmations at milestone
    ) {
    }
}
