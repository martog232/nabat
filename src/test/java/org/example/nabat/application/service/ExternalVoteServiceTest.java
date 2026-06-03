package org.example.nabat.application.service;

import org.example.nabat.application.port.in.SendNotificationUseCase;
import org.example.nabat.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.ExternalVotingPort;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
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
    void vote_delegatesToExternalService_syncsProjection_andNotifiesOwner() {
        Instant createdAt = Instant.now();
        when(externalVotingPort.vote(alertId, voterId, VoteType.UPVOTE))
                .thenReturn(new ExternalVotingPort.VoteReceipt(UUID.randomUUID(), alertId, VoteType.UPVOTE, createdAt));
        when(externalVotingPort.getVoteStats(alertId))
                .thenReturn(new ExternalVotingPort.VoteStats(5, 2, 1, 5));
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(buildAlert(ownerId.value())));

        VoteAlertUseCase.VoteReceipt receipt = service.vote(new VoteAlertUseCase.VoteCommand(alertId, voterId, VoteType.UPVOTE));

        assertEquals(alertId, receipt.alertId());
        assertEquals(VoteType.UPVOTE, receipt.voteType());
        verify(alertRepository).updateVoteCounts(alertId, 5, 2, 1, 5);
        verify(sendNotificationUseCase).sendVoteNotification(any());
    }

    @Test
    void vote_doesNotNotifyOnSelfVote() {
        when(externalVotingPort.vote(alertId, voterId, VoteType.CONFIRM))
                .thenReturn(new ExternalVotingPort.VoteReceipt(UUID.randomUUID(), alertId, VoteType.CONFIRM, Instant.now()));
        when(externalVotingPort.getVoteStats(alertId))
                .thenReturn(new ExternalVotingPort.VoteStats(2, 1, 10, 21));
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(buildAlert(voterId.value())));

        service.vote(new VoteAlertUseCase.VoteCommand(alertId, voterId, VoteType.CONFIRM));

        verify(sendNotificationUseCase, never()).sendVoteNotification(any());
        verify(sendNotificationUseCase, never()).sendMilestoneNotification(any());
    }

    private Alert buildAlert(UUID reporterId) {
        return new Alert(
                alertId,
                "title",
                "desc",
                AlertType.FIRE,
                AlertSeverity.HIGH,
                Location.of(0, 0),
                Instant.now(),
                AlertStatus.ACTIVE,
                reporterId,
                0,
                0,
                0,
                null
        );
    }
}
