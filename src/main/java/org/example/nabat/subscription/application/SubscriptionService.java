package org.example.nabat.subscription.application;

import lombok.RequiredArgsConstructor;
import org.example.nabat.shared.UseCase;
import org.example.nabat.subscription.application.port.in.SubscribeToAlertsUseCase;
import org.example.nabat.subscription.application.port.out.UserSubscriptionRepository;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.subscription.domain.UserSubscription;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
public class SubscriptionService implements SubscribeToAlertsUseCase {

    private final UserSubscriptionRepository repository;

    @Override
    @Transactional
    public UserSubscription subscribe(SubscribeCommand cmd) {
        if (cmd.radiusKm() <= 0) {
            throw new IllegalArgumentException("radiusKm must be positive");
        }
        Location center = Location.of(cmd.latitude(), cmd.longitude());
        return repository.save(UserSubscription.create(
                cmd.userId(), cmd.alertType(), center, cmd.radiusKm()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSubscription> listMine(UserId userId) {
        return repository.findByUserId(userId);
    }

    @Override
    @Transactional
    public void unsubscribe(UUID subscriptionId, UserId actor) {
        UserSubscription s = repository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        if (!s.userId().equals(actor)) {
            // Domain exception, not Spring Security's — the handler maps it to the same 403.
            throw new NotAuthorizedException("Not the owner of this subscription");
        }
        repository.deleteById(subscriptionId);
    }
}

