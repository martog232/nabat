package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.voting.domain.model.VoteType;

public interface SendNotificationUseCase {

    Notification sendVoteNotification(VoteNotificationCommand command);

    Notification sendMilestoneNotification(MilestoneNotificationCommand command);

    record VoteNotificationCommand(
            UserId alertOwnerId,      // owner of the alert
            UserId voterId,           // user who cast the vote
            AlertId alertId,          // alert that was voted on
            String alertTitle,        // title of the alert
            VoteType voteType         // type of vote cast
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
