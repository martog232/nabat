package org.example.nabat.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.ExternalVotingPort;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class ExternalVoteService implements VoteAlertUseCase {

    private final ExternalVotingPort externalVotingPort;
    private final AlertRepository alertRepository;
    private final SendNotificationUseCase sendNotificationUseCase;

    @Override
    @Transactional
    public VoteReceipt vote(VoteCommand command) {
        ExternalVotingPort.VoteReceipt externalReceipt = externalVotingPort.vote(
                command.alertId(),
                command.userId(),
                command.voteType()
        );

        VoteStats stats = syncProjection(command.alertId());
        notifyAlertOwner(command, stats.confirmations());

        return new VoteReceipt(
                externalReceipt.id(),
                externalReceipt.alertId(),
                externalReceipt.voteType(),
                externalReceipt.createdAt()
        );
    }

    @Override
    @Transactional
    public void removeVote(AlertId alertId, UserId userId) {
        externalVotingPort.removeVote(alertId, userId);
        syncProjection(alertId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserVoted(AlertId alertId, UserId userId) {
        return externalVotingPort.hasUserVoted(alertId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public VoteStats getVoteStats(AlertId alertId) {
        ExternalVotingPort.VoteStats stats = externalVotingPort.getVoteStats(alertId);
        return VoteStats.of(stats.upvotes(), stats.downvotes(), stats.confirmations(), stats.credibilityScore());
    }

    private VoteStats syncProjection(AlertId alertId) {
        VoteStats stats = getVoteStats(alertId);
        alertRepository.updateVoteCounts(
                alertId,
                stats.upvotes(),
                stats.downvotes(),
                stats.confirmations(),
                stats.credibilityScore()
        );
        return stats;
    }

    private void notifyAlertOwner(VoteCommand command, int confirmations) {
        alertRepository.findById(command.alertId()).ifPresent(alert -> {
            if (alert.reportedBy().equals(command.userId().value())) {
                return;
            }

            UserId ownerId = UserId.of(alert.reportedBy());
            sendNotificationUseCase.sendVoteNotification(new SendNotificationUseCase.VoteNotificationCommand(
                    ownerId,
                    command.userId(),
                    command.alertId(),
                    alert.title(),
                    command.voteType()
            ));

            if (command.voteType() == VoteType.CONFIRM
                    && NotificationMilestones.isMilestone(confirmations)) {
                sendNotificationUseCase.sendMilestoneNotification(new SendNotificationUseCase.MilestoneNotificationCommand(
                        ownerId,
                        command.alertId(),
                        alert.title(),
                        confirmations
                ));
            }
        });
    }
}
