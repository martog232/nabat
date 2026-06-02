package org.example.nabat.voting.application.service;

import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.voting.application.port.out.AlertVoteRepository;
import org.example.nabat.voting.domain.event.VoteCastEvent;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.voting.domain.model.VoteType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteCastProjectionUpdaterTest {

    @Mock
    private AlertVoteRepository alertVoteRepository;

    @Mock
    private AlertRepository alertRepository;

    @Test
    void shouldRebuildAndPersistVoteProjectionAfterVoteCast() {
        AlertId alertId = AlertId.generate();
        VoteCastEvent event = new VoteCastEvent(alertId, UserId.generate(), VoteType.CONFIRM, Instant.now());

        when(alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.UPVOTE)).thenReturn(11);
        when(alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.DOWNVOTE)).thenReturn(4);
        when(alertVoteRepository.countByAlertIdAndVoteType(alertId, VoteType.CONFIRM)).thenReturn(3);

        VoteCastProjectionUpdater updater = new VoteCastProjectionUpdater(alertVoteRepository, alertRepository);
        updater.onVoteCast(event);

        verify(alertRepository).updateVoteCounts(alertId, 11, 4, 3, 13);
    }
}

