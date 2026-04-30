package org.example.nabat.application.service;

import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.application.port.in.VoteAlertUseCase.VoteCommand;
import org.example.nabat.application.port.in.VoteAlertUseCase.VoteStats;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.AlertVoteRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.AlertVote;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AlertVoteServiceTest {

    @Mock
    private AlertVoteRepository alertVoteRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private SendNotificationUseCase sendNotificationUseCase;

    private AlertVoteService alertVoteService;

    private AlertId testAlertId;
    private UserId testUserId;
    private UUID alertOwnerId;

    @BeforeEach
    void setUp() {
        alertVoteService = new AlertVoteService(alertVoteRepository, alertRepository, sendNotificationUseCase);
        testAlertId = new AlertId(UUID.randomUUID());
        testUserId = new UserId(UUID.randomUUID());
        alertOwnerId = UUID.randomUUID();
    }

    private Alert buildAlert(UUID reporterId) {
        return new Alert(
                testAlertId,
                "Title", "Desc",
                AlertType.FIRE, AlertSeverity.HIGH,
                Location.of(0, 0),
                Instant.now(),
                AlertStatus.ACTIVE,
                reporterId,
                0, 0, 0,
                null
        );
    }

    /**
     * Test case for the `vote` method in the `AlertVoteService` class.
     * This test verifies the behavior of creating a new vote when no existing vote is found
     * for the given alert and user.
     */
    @Test
    void shouldCreateNewVote() {

        VoteCommand command = new VoteCommand(testAlertId, testUserId, VoteType.UPVOTE);

        when(alertVoteRepository.findByAlertIdAndUserId(testAlertId, testUserId))
                .thenReturn(Optional.empty());
        when(alertVoteRepository.save(any(AlertVote.class)))
                .then(invocation -> invocation.getArgument(0));
        when(alertVoteRepository.countByAlertIdAndVoteType(any(), any()))
                .thenReturn(0);
        when(alertRepository.findById(testAlertId)).thenReturn(Optional.of(buildAlert(alertOwnerId)));

        AlertVote result = alertVoteService.vote(command);

        assertNotNull(result);
        assertEquals(testAlertId, result.alertId());
        assertEquals(testUserId, result.userId());
        assertEquals(VoteType.UPVOTE, result.voteType());

        verify(alertVoteRepository).save(any(AlertVote.class));
        verify(alertRepository).updateVoteCounts(eq(testAlertId), anyInt(), anyInt(), anyInt());
        verify(sendNotificationUseCase).sendVoteNotification(any());
    }

    @Test
    void shouldAllowChangingVoteType() {

        VoteCommand command = new VoteCommand(testAlertId, testUserId, VoteType.DOWNVOTE);

        AlertVote existingVote = AlertVote.create(testAlertId, testUserId, VoteType.UPVOTE);
        when(alertVoteRepository.findByAlertIdAndUserId(testAlertId, testUserId)).thenReturn(Optional.of(existingVote));
        when(alertVoteRepository.save(any(AlertVote.class)))
                .then(invocation -> invocation.getArgument(0));
        when(alertVoteRepository.countByAlertIdAndVoteType(any(), any()))
                .thenReturn(0);
        when(alertRepository.findById(testAlertId)).thenReturn(Optional.of(buildAlert(alertOwnerId)));

        AlertVote result = alertVoteService.vote(command);

        assertNotNull(result);
        assertEquals(VoteType.DOWNVOTE, result.voteType());
        // Switch preserves id, no delete/insert.
        assertEquals(existingVote.id(), result.id());

        verify(alertVoteRepository, never()).deleteByAlertIdAndUserId(any(), any());
        verify(alertVoteRepository).save(any(AlertVote.class));
        verify(alertRepository).updateVoteCounts(eq(testAlertId), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shouldBeIdempotentOnSameTypeReVote() {
        VoteCommand command = new VoteCommand(testAlertId, testUserId, VoteType.UPVOTE);

        AlertVote existingVote = AlertVote.create(testAlertId, testUserId, VoteType.UPVOTE);
        when(alertVoteRepository.findByAlertIdAndUserId(testAlertId, testUserId))
                .thenReturn(Optional.of(existingVote));

        AlertVote result = alertVoteService.vote(command);

        assertEquals(existingVote, result);
        verify(alertVoteRepository, never()).save(any());
        verify(alertRepository, never()).updateVoteCounts(any(), anyInt(), anyInt(), anyInt());
        verifyNoInteractions(sendNotificationUseCase);
    }

    @Test
    void shouldNotNotifyOnSelfVote() {
        VoteCommand command = new VoteCommand(testAlertId, testUserId, VoteType.UPVOTE);

        when(alertVoteRepository.findByAlertIdAndUserId(testAlertId, testUserId))
                .thenReturn(Optional.empty());
        when(alertVoteRepository.save(any(AlertVote.class)))
                .then(invocation -> invocation.getArgument(0));
        when(alertVoteRepository.countByAlertIdAndVoteType(any(), any()))
                .thenReturn(0);
        // Owner == voter → self-vote
        when(alertRepository.findById(testAlertId))
                .thenReturn(Optional.of(buildAlert(testUserId.value())));

        alertVoteService.vote(command);

        verifyNoInteractions(sendNotificationUseCase);
    }

    @Test
    void shouldFireMilestoneOnConfirmAtThreshold() {
        VoteCommand command = new VoteCommand(testAlertId, testUserId, VoteType.CONFIRM);

        when(alertVoteRepository.findByAlertIdAndUserId(testAlertId, testUserId))
                .thenReturn(Optional.empty());
        when(alertVoteRepository.save(any(AlertVote.class)))
                .then(invocation -> invocation.getArgument(0));
        when(alertVoteRepository.countByAlertIdAndVoteType(testAlertId, VoteType.CONFIRM))
                .thenReturn(10);
        when(alertVoteRepository.countByAlertIdAndVoteType(testAlertId, VoteType.UPVOTE))
                .thenReturn(0);
        when(alertVoteRepository.countByAlertIdAndVoteType(testAlertId, VoteType.DOWNVOTE))
                .thenReturn(0);
        when(alertRepository.findById(testAlertId))
                .thenReturn(Optional.of(buildAlert(alertOwnerId)));

        alertVoteService.vote(command);

        verify(sendNotificationUseCase).sendVoteNotification(any());
        verify(sendNotificationUseCase).sendMilestoneNotification(any());
    }

    @Test
    void shouldNotFireMilestoneBelowThreshold() {
        VoteCommand command = new VoteCommand(testAlertId, testUserId, VoteType.CONFIRM);

        when(alertVoteRepository.findByAlertIdAndUserId(testAlertId, testUserId))
                .thenReturn(Optional.empty());
        when(alertVoteRepository.save(any(AlertVote.class)))
                .then(invocation -> invocation.getArgument(0));
        when(alertVoteRepository.countByAlertIdAndVoteType(testAlertId, VoteType.CONFIRM))
                .thenReturn(7);
        when(alertVoteRepository.countByAlertIdAndVoteType(testAlertId, VoteType.UPVOTE))
                .thenReturn(0);
        when(alertVoteRepository.countByAlertIdAndVoteType(testAlertId, VoteType.DOWNVOTE))
                .thenReturn(0);
        when(alertRepository.findById(testAlertId))
                .thenReturn(Optional.of(buildAlert(alertOwnerId)));

        alertVoteService.vote(command);

        verify(sendNotificationUseCase, never()).sendMilestoneNotification(any());
    }

    @Test
    void shouldRemoveVote() {
        when(alertVoteRepository.existsByAlertIdAndUserId(testAlertId, testUserId))
                .thenReturn(true);
        when(alertVoteRepository.countByAlertIdAndVoteType(any(), any()))
                .thenReturn(0);

        alertVoteService.removeVote(testAlertId, testUserId);

        verify(alertVoteRepository).deleteByAlertIdAndUserId(testAlertId, testUserId);
        verify(alertRepository).updateVoteCounts(eq(testAlertId), anyInt(), anyInt(), anyInt());
    }

    @Test
    void shouldThrowWhenRemovingNonExistentVote() {
        when(alertVoteRepository.existsByAlertIdAndUserId(testAlertId, testUserId))
                .thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> alertVoteService.removeVote(testAlertId, testUserId));

        verify(alertVoteRepository, never()).deleteByAlertIdAndUserId(any(), any());
    }

    @Test
    void shouldReturnTrueWhenUserHasVoted() {
        when(alertVoteRepository.existsByAlertIdAndUserId(testAlertId, testUserId)).thenReturn(true);

        boolean hasVoted = alertVoteService.hasUserVoted(testAlertId, testUserId);

        assertTrue(hasVoted);
    }

    @Test
    void shouldReturnVoteStats() {
        when(alertVoteRepository.countByAlertIdAndVoteType(testAlertId, VoteType.UPVOTE)).thenReturn(10);
        when(alertVoteRepository.countByAlertIdAndVoteType(testAlertId, VoteType.DOWNVOTE)).thenReturn(4);
        when(alertVoteRepository.countByAlertIdAndVoteType(testAlertId, VoteType.CONFIRM)).thenReturn(3);

        VoteStats stats = alertVoteService.getVoteStats(testAlertId);

        assertEquals(10, stats.upvotes());
        assertEquals(4, stats.downvotes());
        assertEquals(3, stats.confirmations());
        assertEquals(10 - 4 + (3 * 2), stats.credibilityScore());
    }
}
