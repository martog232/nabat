package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;

public interface SendNotificationUseCase {

    Notification sendVoteNotification(VoteNotificationCommand command);

    Notification sendMilestoneNotification(MilestoneNotificationCommand command);

    record VoteNotificationCommand(
            UserId alertOwnerId,      // Кой притежава alert-а
            UserId voterId,           // Кой гласува
            AlertId alertId,          // За кой alert
            String alertTitle,        // Заглавие на alert-а
            VoteType voteType         // Тип на гласа
    ) {
    }

    record MilestoneNotificationCommand(
            UserId alertOwnerId,           // Кой потребител притежава alert-а
            AlertId alertId,               // За кой alert
            String milestoneTitle,   // Заглавие на постижението
            int confirmationCount     // Брой потвърждения
    ) {
    }
}
