package org.example.nabat.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.AlertVoteRepository;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertVote;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;

import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class AlertVoteService implements VoteAlertUseCase {

    private final AlertVoteRepository alertVoteRepository;
    private final AlertRepository alertRepository;

    @Override
    public AlertVote vote(VoteCommand command) {
        Optional<AlertVote> existingVote = alertVoteRepository.findByAlertIdAndUserId(command.alertId(), command.userId());

        if (existingVote.isPresent()) {
            AlertVote vote = existingVote.get();
            if (vote.voteType() == command.voteType()) {
                throw new IllegalStateException("User has already voted with the same vote type.");
            }
            alertVoteRepository.deleteByAlertIdAndUserId(command.alertId(), command.userId());
        }

        AlertVote newVote = AlertVote.create(
                command.alertId(),
                command.userId(),
                command.voteType());
        AlertVote savedVote = alertVoteRepository.save(newVote);

        updateAlertVoteCounts(command.alertId());

        return savedVote;
    }

    @Override
    public void removeVote(AlertId alertId, UserId userId) {
        if (!alertVoteRepository.existsByAlertIdAndUserId(alertId, userId)) {
            throw new IllegalStateException("No existing vote to remove.");
        }
        alertVoteRepository.deleteByAlertIdAndUserId(alertId, userId);

        updateAlertVoteCounts(alertId);
    }

    @Override
    public boolean hasUserVoted(AlertId alertId, UserId userId) {
        return alertVoteRepository.existsByAlertIdAndUserId(alertId, userId);
    }

    @Override
    public VoteStats getVoteStats(AlertId alertId) {
        int upvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.UPVOTE);
        int downvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.DOWNVOTE);
        int confirmations = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.CONFIRM);
        return VoteStats.of(upvotes, downvotes, confirmations);
    }

    private void updateAlertVoteCounts(AlertId alertId) {
        int upvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.UPVOTE);
        int downvotes = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.DOWNVOTE);
        int confirmations = alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.CONFIRM);

        alertRepository.updateVoteCounts(alertId, upvotes, downvotes, confirmations);
    }
}
