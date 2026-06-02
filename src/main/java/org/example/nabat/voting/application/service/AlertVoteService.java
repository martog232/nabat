package org.example.nabat.voting.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.voting.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.voting.application.port.out.AlertVoteRepository;
import org.example.nabat.voting.application.port.out.VoteNotificationPort;
import org.example.nabat.voting.application.port.out.VoteNotificationPort.MilestoneNotificationCommand;
import org.example.nabat.voting.application.port.out.VoteNotificationPort.VoteNotificationCommand;
import org.example.nabat.voting.domain.event.VoteCastEvent;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.voting.domain.model.AlertVote;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.voting.domain.model.VoteType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class AlertVoteService implements VoteAlertUseCase {

    private final AlertVoteRepository alertVoteRepository;
    private final AlertRepository alertRepository;
    private final VoteNotificationPort voteNotificationPort;
    private final ApplicationEventPublisher eventPublisher;

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

        eventPublisher.publishEvent(new VoteCastEvent(
                command.alertId(),
                command.userId(),
                command.voteType(),
                Instant.now()
        ));

        int newConfirmations = command.voteType() == VoteType.CONFIRM
                ? alertVoteRepository.countByAlertIdAndVoteType(command.alertId(), VoteType.CONFIRM)
                : 0;

        // Fire owner notifications (skip self-vote).
        alertRepository.findById(command.alertId()).ifPresent(alert -> {
            if (alert.reportedBy().equals(command.userId().value())) {
                return; // self-vote
            }
            UserId ownerId = UserId.of(alert.reportedBy());
            voteNotificationPort.sendVoteNotification(new VoteNotificationCommand(
                    ownerId, command.userId(), command.alertId(),
                    alert.title(), command.voteType()));

            // Milestone notification only on CONFIRM whose new count crosses a threshold.
            if (command.voteType() == VoteType.CONFIRM
                    && NotificationMilestones.isMilestone(newConfirmations)) {
                voteNotificationPort.sendMilestoneNotification(new MilestoneNotificationCommand(
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

        rebuildVoteProjection(alertId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserVoted(AlertId alertId, UserId userId) {
        return alertVoteRepository.existsByAlertIdAndUserId(alertId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public VoteStats getVoteStats(AlertId alertId) {
        return alertRepository.findVoteStats(alertId)
                .map(snapshot -> VoteStats.of(
                        snapshot.upvotes(),
                        snapshot.downvotes(),
                        snapshot.confirmations(),
                        snapshot.credibilityScore()))
                .orElse(VoteStats.of(0, 0, 0));
    }

    /** Recomputes the vote projection in the alerts table for non-evented paths (e.g., vote removal). */
    private void rebuildVoteProjection(AlertId alertId) {
        int upvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.UPVOTE);
        int downvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.DOWNVOTE);
        int confirmations = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.CONFIRM);
        int credibilityScore = upvotes - downvotes + (confirmations * 2);

        alertRepository.updateVoteCounts(alertId, upvotes, downvotes, confirmations, credibilityScore);
    }
}
