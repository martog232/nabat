package org.example.nabat.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.application.port.in.SendNotificationUseCase.MilestoneNotificationCommand;
import org.example.nabat.application.port.in.SendNotificationUseCase.VoteNotificationCommand;
import org.example.nabat.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.AlertVoteRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertVote;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class AlertVoteService implements VoteAlertUseCase {

    private final AlertVoteRepository alertVoteRepository;
    private final AlertRepository alertRepository;
    private final SendNotificationUseCase sendNotificationUseCase;

    @Override
    @Transactional
    public AlertVote vote(VoteCommand command) {
        Optional<AlertVote> existing = alertVoteRepository.findByAlertIdAndUserId(
                command.alertId(), command.userId());

        // Idempotent: same-type re-vote is a no-op (no save, no count update, no notification).
        if (existing.isPresent() && existing.get().voteType() == command.voteType()) {
            return existing.get();
        }

        AlertVote saved;
        if (existing.isPresent()) {
            // Switch type — preserve id, refresh createdAt.
            saved = alertVoteRepository.save(existing.get().withVoteType(command.voteType()));
        } else {
            saved = alertVoteRepository.save(AlertVote.create(
                    command.alertId(), command.userId(), command.voteType()));
        }

        int newConfirmations = updateAlertVoteCounts(command.alertId());

        // Fire owner notifications (skip self-vote).
        alertRepository.findById(command.alertId()).ifPresent(alert -> {
            if (alert.reportedBy().equals(command.userId().value())) {
                return; // self-vote
            }
            UserId ownerId = UserId.of(alert.reportedBy());
            sendNotificationUseCase.sendVoteNotification(new VoteNotificationCommand(
                    ownerId, command.userId(), command.alertId(),
                    alert.title(), command.voteType()));

            // Milestone notification only on CONFIRM whose new count crosses a threshold.
            if (command.voteType() == VoteType.CONFIRM
                    && NotificationMilestones.isMilestone(newConfirmations)) {
                sendNotificationUseCase.sendMilestoneNotification(new MilestoneNotificationCommand(
                        ownerId, command.alertId(), alert.title(), newConfirmations));
            }
        });

        return saved;
    }

    @Override
    @Transactional
    public void removeVote(AlertId alertId, UserId userId) {
        if (!alertVoteRepository.existsByAlertIdAndUserId(alertId, userId)) {
            throw new IllegalStateException("No existing vote to remove.");
        }
        alertVoteRepository.deleteByAlertIdAndUserId(alertId, userId);

        updateAlertVoteCounts(alertId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserVoted(AlertId alertId, UserId userId) {
        return alertVoteRepository.existsByAlertIdAndUserId(alertId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public VoteStats getVoteStats(AlertId alertId) {
        int upvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.UPVOTE);
        int downvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.DOWNVOTE);
        int confirmations = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.CONFIRM);
        return VoteStats.of(upvotes, downvotes, confirmations);
    }

    /** Recomputes denormalized vote counts on the alert; returns the new confirmation count. */
    private int updateAlertVoteCounts(AlertId alertId) {
        int upvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.UPVOTE);
        int downvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.DOWNVOTE);
        int confirmations = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.CONFIRM);

        alertRepository.updateVoteCounts(alertId, upvotes, downvotes, confirmations);
        return confirmations;
    }
}
