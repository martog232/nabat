package org.example.nabat.subscription.adapter.out.incident;

import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.subscription.application.port.out.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * Covers the union-and-deduplicate behaviour that moved here out of
 * {@code CreateAlertService} when the audience lookup was inverted behind
 * {@code AlertAudiencePort}.
 */
@ExtendWith(MockitoExtension.class)
class AlertAudienceAdapterTest {

    private static final Location SOFIA = Location.of(42.6977, 23.3219);

    @Mock
    private UserSubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    private AlertAudienceAdapter adapter() {
        return new AlertAudienceAdapter(subscriptionRepository, userRepository);
    }

    @Test
    void mergesSubscribersAndNearbyUsersWithoutDuplicates() {
        UUID inBoth = UUID.randomUUID();
        UUID subscribedOnly = UUID.randomUUID();
        UUID nearbyOnly = UUID.randomUUID();

        when(subscriptionRepository.findUsersSubscribedToAlertType(any(), any(), anyDouble()))
            .thenReturn(List.of(inBoth, subscribedOnly));
        when(userRepository.findUsersNearLocation(any()))
            .thenReturn(List.of(inBoth, nearbyOnly));

        List<UUID> recipients = adapter().recipientsFor(AlertType.FIRE, SOFIA, 5.0);

        assertThat(recipients).containsExactlyInAnyOrder(inBoth, subscribedOnly, nearbyOnly);
    }

    @Test
    void returnsEmptyWhenNeitherSourceMatches() {
        when(subscriptionRepository.findUsersSubscribedToAlertType(any(), any(), anyDouble()))
            .thenReturn(List.of());
        when(userRepository.findUsersNearLocation(any())).thenReturn(List.of());

        assertThat(adapter().recipientsFor(AlertType.CRIME, SOFIA, 1.0)).isEmpty();
    }

    @Test
    void passesTheBroadcastRadiusThroughToTheSubscriptionQuery() {
        when(subscriptionRepository.findUsersSubscribedToAlertType(AlertType.ACCIDENT, SOFIA, 10.0))
            .thenReturn(List.of());
        when(userRepository.findUsersNearLocation(SOFIA)).thenReturn(List.of());

        // Strict stubbing fails this test if the adapter passes anything else.
        assertThat(adapter().recipientsFor(AlertType.ACCIDENT, SOFIA, 10.0)).isEmpty();
    }
}
