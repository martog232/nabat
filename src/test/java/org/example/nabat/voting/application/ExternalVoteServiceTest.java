package org.example.nabat.voting.application;

import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.voting.application.port.in.VoteAlertUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.voting.application.port.out.ExternalVotingPort;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.voting.domain.VoteType;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalVoteServiceTest {

    @Mock
    private ExternalVotingPort externalVotingPort;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private SendNotificationUseCase sendNotificationUseCase;

    private ExternalVoteService service;
    private AlertId alertId;
    private UserId voterId;
    private UserId ownerId;

    @BeforeEach
    void setUp() {
        service = new ExternalVoteService(externalVotingPort, alertRepository, sendNotificationUseCase);
        alertId = AlertId.generate();
        voterId = UserId.generate();
        ownerId = UserId.generate();
    }

    @Test
    void vote_returnsTheTalliesWithTheReceipt_andNotifiesTheOwner() {
        Instant createdAt = Instant.now();
        when(externalVotingPort.vote(alertId, voterId, VoteType.UPVOTE))
            .thenReturn(new ExternalVotingPort.VoteResult(
                UUID.randomUUID(), alertId, VoteType.UPVOTE, createdAt,
                new ExternalVotingPort.VoteStats(5, 2, 1, 5)));
        when(alertRepository.findById(alertId))
            .thenReturn(Optional.of(Fixtures.alert(alertId, ownerId.value())));

        VoteAlertUseCase.VoteReceipt receipt =
            service.vote(new VoteAlertUseCase.VoteCommand(alertId, voterId, VoteType.UPVOTE));

        assertEquals(alertId, receipt.alertId());
        assertEquals(VoteType.UPVOTE, receipt.voteType());
        // The tallies travel back with the receipt, so a client needs no second call.
        assertEquals(5, receipt.stats().upvotes());
        assertEquals(5, receipt.stats().credibilityScore());

        verify(sendNotificationUseCase).sendVoteNotification(any());
        // No follow-up stats read: that endpoint is served from an eventually consistent
        // projection and would have returned the pre-vote counts.
        verify(externalVotingPort, never()).getVoteStats(any());
    }

    /**
     * The counts on the local alert are written by the vote-event consumer now. Casting a vote
     * must not also write them here, or the two would be racing to set the same row and the
     * loser would be whichever event arrived first.
     */
    @Test
    void vote_doesNotWriteTheCountsItself() {
        when(externalVotingPort.vote(alertId, voterId, VoteType.UPVOTE))
            .thenReturn(new ExternalVotingPort.VoteResult(
                UUID.randomUUID(), alertId, VoteType.UPVOTE, Instant.now(),
                new ExternalVotingPort.VoteStats(5, 2, 1, 5)));
        when(alertRepository.findById(alertId))
            .thenReturn(Optional.of(Fixtures.alert(alertId, ownerId.value())));

        service.vote(new VoteAlertUseCase.VoteCommand(alertId, voterId, VoteType.UPVOTE));

        verify(alertRepository, never()).applyVoteCounts(any(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void vote_doesNotNotifyOnSelfVote() {
        when(externalVotingPort.vote(alertId, voterId, VoteType.CONFIRM))
            .thenReturn(new ExternalVotingPort.VoteResult(
                UUID.randomUUID(), alertId, VoteType.CONFIRM, Instant.now(),
                new ExternalVotingPort.VoteStats(2, 1, 10, 21)));
        when(alertRepository.findById(alertId))
            .thenReturn(Optional.of(Fixtures.alert(alertId, voterId.value())));

        service.vote(new VoteAlertUseCase.VoteCommand(alertId, voterId, VoteType.CONFIRM));

        verify(sendNotificationUseCase, never()).sendVoteNotification(any());
        verify(sendNotificationUseCase, never()).sendMilestoneNotification(any());
    }

    @Test
    void vote_onAlertMissingLocallyStillReturnsAReceipt() {
        when(externalVotingPort.vote(alertId, voterId, VoteType.UPVOTE))
            .thenReturn(new ExternalVotingPort.VoteResult(
                UUID.randomUUID(), alertId, VoteType.UPVOTE, Instant.now(),
                new ExternalVotingPort.VoteStats(1, 0, 0, 1)));
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());

        VoteAlertUseCase.VoteReceipt receipt =
            service.vote(new VoteAlertUseCase.VoteCommand(alertId, voterId, VoteType.UPVOTE));

        assertEquals(1, receipt.stats().upvotes());
        // Nothing to notify about when the alert is gone locally.
        verify(sendNotificationUseCase, never()).sendVoteNotification(any());
    }

    @Test
    void removeVote_returnsTheTalliesFromTheVotingService() {
        when(externalVotingPort.removeVote(alertId, voterId))
            .thenReturn(new ExternalVotingPort.VoteStats(3, 1, 0, 2));

        VoteAlertUseCase.VoteStats stats = service.removeVote(alertId, voterId);

        assertEquals(3, stats.upvotes());
        assertEquals(2, stats.credibilityScore());
        verify(alertRepository, never()).applyVoteCounts(any(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void findUserVote_delegatesWithoutTouchingTheDatabase() {
        when(externalVotingPort.findUserVote(alertId, voterId)).thenReturn(Optional.of(VoteType.CONFIRM));

        assertEquals(Optional.of(VoteType.CONFIRM), service.findUserVote(alertId, voterId));
        verify(alertRepository, never()).findById(any());
    }
}
