package org.example.nabat.subscription.application;

import org.example.nabat.subscription.application.port.in.SubscribeToAlertsUseCase.SubscribeCommand;
import org.example.nabat.subscription.application.port.out.UserSubscriptionRepository;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.subscription.domain.UserSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.example.nabat.shared.domain.NotAuthorizedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private UserSubscriptionRepository repository;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(repository);
    }

    @Test
    void subscribe_savesNewSubscription() {
        UserId u = UserId.generate();
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription s = service.subscribe(new SubscribeCommand(
                u, AlertType.FIRE, 42.0, 23.0, 5.0));

        assertEquals(u, s.userId());
        assertEquals(AlertType.FIRE, s.alertType());
        assertTrue(s.active());
    }

    @Test
    void subscribe_rejectsNonPositiveRadius() {
        UserId u = UserId.generate();
        assertThrows(IllegalArgumentException.class, () -> service.subscribe(
                new SubscribeCommand(u, AlertType.FIRE, 42.0, 23.0, 0.0)));
    }

    @Test
    void unsubscribe_byOwner_deletes() {
        UserId u = UserId.generate();
        UUID id = UUID.randomUUID();
        UserSubscription s = new UserSubscription(id, u, AlertType.FIRE,
                Location.of(0, 0), 1.0, true, Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(s));

        service.unsubscribe(id, u);

        verify(repository).deleteById(id);
    }

    @Test
    void unsubscribe_byOther_throwsAccessDenied() {
        UUID id = UUID.randomUUID();
        UserSubscription s = new UserSubscription(id, UserId.generate(), AlertType.FIRE,
                Location.of(0, 0), 1.0, true, Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(s));

        assertThrows(NotAuthorizedException.class,
                () -> service.unsubscribe(id, UserId.generate()));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void unsubscribe_missing_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.unsubscribe(id, UserId.generate()));
    }
}

