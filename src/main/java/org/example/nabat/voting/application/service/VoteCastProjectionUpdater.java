package org.example.nabat.voting.application.service;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.voting.application.port.out.AlertVoteRepository;
import org.example.nabat.voting.domain.event.VoteCastEvent;
import org.example.nabat.voting.domain.model.VoteType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@UseCase
@RequiredArgsConstructor
public class VoteCastProjectionUpdater {

    private final AlertVoteRepository alertVoteRepository;
    private final AlertRepository alertRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoteCast(VoteCastEvent event) {
        int upvotes = alertVoteRepository.countByAlertIdAndVoteType(event.alertId(), VoteType.UPVOTE);
        int downvotes = alertVoteRepository.countByAlertIdAndVoteType(event.alertId(), VoteType.DOWNVOTE);
        int confirmations = alertVoteRepository.countByAlertIdAndVoteType(event.alertId(), VoteType.CONFIRM);
        int credibilityScore = upvotes - downvotes + (confirmations * 2);

        alertRepository.updateVoteCounts(event.alertId(), upvotes, downvotes, confirmations, credibilityScore);
    }
}

