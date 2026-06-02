package org.example.nabat.voting.application.port.out;

import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.voting.domain.model.VoteType;

public interface VoteNotificationPort {

    void sendVoteNotification(VoteNotificationCommand command);

    void sendMilestoneNotification(MilestoneNotificationCommand command);

    record VoteNotificationCommand(
            UserId alertOwnerId,
            UserId voterId,
            AlertId alertId,
            String alertTitle,
            VoteType voteType
    ) {
    }

    record MilestoneNotificationCommand(
            UserId alertOwnerId,
            AlertId alertId,
            String milestoneTitle,
            int confirmationCount
    ) {
    }
}
