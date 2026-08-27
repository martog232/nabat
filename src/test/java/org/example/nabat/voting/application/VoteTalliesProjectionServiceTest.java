package org.example.nabat.voting.application;

import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.testsupport.Fixtures;
import org.example.nabat.voting.application.port.in.ApplyVoteTalliesUseCase.VoteTalliesUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteTalliesProjectionServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private AlertNotificationPort alertNotificationPort;

    private VoteTalliesProjectionService service;
    private AlertId alertId;

    @BeforeEach
    void setUp() {
        service = new VoteTalliesProjectionService(alertRepository, alertNotificationPort);
        alertId = AlertId.generate();
    }

    @Test
    void writesTheCountsAndBroadcastsTheUpdatedAlert() {
        when(alertRepository.applyVoteCounts(alertId, 5, 2, 1, 5))
            .thenReturn(Optional.of(Fixtures.alert(alertId, UserId.generate().value())));

        service.applyTallies(new VoteTalliesUpdate(alertId, 5, 2, 1, 5));

        verify(alertRepository).applyVoteCounts(alertId, 5, 2, 1, 5);
        verify(alertNotificationPort).broadcastAlertUpdate(any(Alert.class));
    }

    /**
     * The property the whole design rests on: delivery is at-least-once, and applying the
     * same event twice must leave the same row. It does because the counts are absolute — the
     * second write sets the values the first one already set, rather than adding to them.
     */
    @Test
    void applyingTheSameUpdateTwiceWritesTheSameCounts() {
        when(alertRepository.applyVoteCounts(alertId, 5, 2, 1, 5))
            .thenReturn(Optional.of(Fixtures.alert(alertId, UserId.generate().value())));

        VoteTalliesUpdate update = new VoteTalliesUpdate(alertId, 5, 2, 1, 5);
        service.applyTallies(update);
        service.applyTallies(update);

        verify(alertRepository, times(2)).applyVoteCounts(alertId, 5, 2, 1, 5);
    }

    @Test
    void broadcastsNothingWhenTheAlertIsGoneLocally() {
        when(alertRepository.applyVoteCounts(alertId, 1, 0, 0, 1)).thenReturn(Optional.empty());

        service.applyTallies(new VoteTalliesUpdate(alertId, 1, 0, 0, 1));

        verify(alertNotificationPort, never()).broadcastAlertUpdate(any());
    }
}
