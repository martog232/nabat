package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.UserSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserSubscriptionJpaRepositoryHaversineIntegrationTest {

    private static final double SOFIA_LAT = 42.695;
    private static final double SOFIA_LON = 23.329;

    @Autowired
    private UserSubscriptionJpaRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findUserIdsMatchingReturnsDistinctActiveSubscribersByTypeAndCombinedRadius() {
        UUID matchingUser = UUID.randomUUID();
        UUID inactiveUser = UUID.randomUUID();
        UUID wrongTypeUser = UUID.randomUUID();
        UUID farAwayUser = UUID.randomUUID();

        repository.saveAll(List.of(
            entity(matchingUser, AlertType.FIRE, SOFIA_LAT, SOFIA_LON, 0.5, true),
            entity(matchingUser, AlertType.FIRE, SOFIA_LAT, SOFIA_LON, 1.0, true),
            entity(inactiveUser, AlertType.FIRE, SOFIA_LAT, SOFIA_LON, 5.0, false),
            entity(wrongTypeUser, AlertType.CRIME, SOFIA_LAT, SOFIA_LON, 5.0, true),
            entity(farAwayUser, AlertType.FIRE, 42.1354, 24.7453, 100.0, true)
        ));
        repository.flush();

        List<UUID> result = repository.findUserIdsMatching(AlertType.FIRE, SOFIA_LAT, SOFIA_LON, 1.0);

        assertThat(result).containsExactly(matchingUser);
    }

    @Test
    void findUserIdsMatchingUsesAlertRadiusTogetherWithSubscriptionRadius() {
        UUID subscriberNearAlertEdge = UUID.randomUUID();
        repository.saveAndFlush(entity(
            subscriberNearAlertEdge,
            AlertType.HAZARD,
            42.730,
            SOFIA_LON,
            1.0,
            true
        ));

        List<UUID> tooSmallRadius = repository.findUserIdsMatching(AlertType.HAZARD, SOFIA_LAT, SOFIA_LON, 1.0);
        List<UUID> combinedRadiusCoversSubscriber = repository.findUserIdsMatching(AlertType.HAZARD, SOFIA_LAT, SOFIA_LON, 4.0);

        assertThat(tooSmallRadius).isEmpty();
        assertThat(combinedRadiusCoversSubscriber).containsExactly(subscriberNearAlertEdge);
    }

    private static UserSubscriptionJpaEntity entity(
        UUID userId,
        AlertType type,
        double latitude,
        double longitude,
        double radiusKm,
        boolean active
    ) {
        return UserSubscriptionJpaEntity.from(new UserSubscription(
            UUID.randomUUID(),
            UserId.of(userId),
            type,
            Location.of(latitude, longitude),
            radiusKm,
            active,
            Instant.now()
        ));
    }
}


