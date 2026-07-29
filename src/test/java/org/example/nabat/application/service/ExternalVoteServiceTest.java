package org.example.nabat.application.service;

import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.out.AlertNotificationPort;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.ExternalVotingPort;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;
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
    @Mock
    private AlertNotificationPort alertNotificationPort;

    private ExternalVoteService service;
    private AlertId alertId;
    private UserId voterId;
    private UserId ownerId;

    @BeforeEach
    void setUp() {
        service = new ExternalVoteService(
            externalVotingPort, alertRepository, sendNotificationUseCase, alertNotificationPort);
        alertId = AlertId.generate();
        voterId = UserId.generate();
        ownerId = UserId.generate();
    }

    @Test
    void vote_syncsProjectionFromTheStatsReturnedWithTheVote_andNotifiesOwner() {
        Instant createdAt = Instant.now();
        when(externalVotingPort.vote(alertId, voterId, VoteType.UPVOTE))
            .thenReturn(new ExternalVotingPort.VoteResult(
                UUID.randomUUID(), alertId, VoteType.UPVOTE, createdAt,
                new ExternalVotingPort.VoteStats(5, 2, 1, 5)));
        when(alertRepository.applyVoteCounts(alertId, 5, 2, 1, 5))
            .thenReturn(Optional.of(Fixtures.alert(alertId, ownerId.value())));

        VoteAlertUseCase.VoteReceipt receipt =
            service.vote(new VoteAlertUseCase.VoteCommand(alertId, voterId, VoteType.UPVOTE));

        assertEquals(alertId, receipt.alertId());
        assertEquals(VoteType.UPVOTE, receipt.voteType());
        // The tallies travel back with the receipt, so a client needs no second call.
        assertEquals(5, receipt.stats().upvotes());
        assertEquals(5, receipt.stats().credibilityScore());

        verify(alertRepository).applyVoteCounts(alertId, 5, 2, 1, 5);
        verify(sendNotificationUseCase).sendVoteNotification(any());
        verify(alertNotificationPort).broadcastAlertUpdate(any(Alert.class));
        // No follow-up stats read: that endpoint is served from an eventually consistent
        // projection and would have returned the pre-vote counts.
        verify(externalVotingPort, never()).getVoteStats(any());
    }

    @Test
    void vote_doesNotNotifyOnSelfVote() {
        when(externalVotingPort.vote(alertId, voterId, VoteType.CONFIRM))
            .thenReturn(new ExternalVotingPort.VoteResult(
                UUID.randomUUID(), alertId, VoteType.CONFIRM, Instant.now(),
                new ExternalVotingPort.VoteStats(2, 1, 10, 21)));
        when(alertRepository.applyVoteCounts(alertId, 2, 1, 10, 21))
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
        when(alertRepository.applyVoteCounts(alertId, 1, 0, 0, 1)).thenReturn(Optional.empty());

        VoteAlertUseCase.VoteReceipt receipt =
            service.vote(new VoteAlertUseCase.VoteCommand(alertId, voterId, VoteType.UPVOTE));

        assertEquals(1, receipt.stats().upvotes());
        // Nothing to notify or broadcast about when the alert is gone locally.
        verify(sendNotificationUseCase, never()).sendVoteNotification(any());
        verify(alertNotificationPort, never()).broadcastAlertUpdate(any());
    }

    @Test
    void removeVote_syncsProjectionAndBroadcasts() {
        when(externalVotingPort.removeVote(alertId, voterId))
            .thenReturn(new ExternalVotingPort.VoteStats(3, 1, 0, 2));
        when(alertRepository.applyVoteCounts(alertId, 3, 1, 0, 2))
            .thenReturn(Optional.of(Fixtures.alert(alertId, ownerId.value())));

        VoteAlertUseCase.VoteStats stats = service.removeVote(alertId, voterId);

        assertEquals(3, stats.upvotes());
        assertEquals(2, stats.credibilityScore());
        verify(alertNotificationPort).broadcastAlertUpdate(any(Alert.class));
    }

    @Test
    void findUserVote_delegatesWithoutTouchingTheDatabase() {
        when(externalVotingPort.findUserVote(alertId, voterId)).thenReturn(Optional.of(VoteType.CONFIRM));

        assertEquals(Optional.of(VoteType.CONFIRM), service.findUserVote(alertId, voterId));
        verify(alertRepository, never()).findById(any());
    }
}
