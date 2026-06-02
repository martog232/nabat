package org.example.nabat.adapter.out.notification;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.voting.application.port.out.VoteNotificationPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VoteNotificationAdapter implements VoteNotificationPort {

    private final SendNotificationUseCase sendNotificationUseCase;

    @Override
    public void sendVoteNotification(VoteNotificationPort.VoteNotificationCommand command) {
        sendNotificationUseCase.sendVoteNotification(new SendNotificationUseCase.VoteNotificationCommand(
                command.alertOwnerId(),
                command.voterId(),
                command.alertId(),
                command.alertTitle(),
                command.voteType()));
    }

    @Override
    public void sendMilestoneNotification(VoteNotificationPort.MilestoneNotificationCommand command) {
        sendNotificationUseCase.sendMilestoneNotification(new SendNotificationUseCase.MilestoneNotificationCommand(
                command.alertOwnerId(),
                command.alertId(),
                command.milestoneTitle(),
                command.confirmationCount()));
    }
}
