package org.example.nabat.incident.application;

import org.example.nabat.shared.UseCase;
import org.example.nabat.incident.application.port.in.CreateAlertUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.shared.domain.Location;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateAlertService implements CreateAlertUseCase {

    private final AlertRepository alertRepository;
    private final ApplicationEventPublisher events;

    public CreateAlertService(AlertRepository alertRepository, ApplicationEventPublisher events) {
        this.alertRepository = alertRepository;
        this.events = events;
    }

    /**
     * Persists a new alert and announces it.
     *
     * <p>{@code @Transactional} — every other use case in this module is transactional
     * and this one was not, so the save and the reads that followed it could observe
     * inconsistent state.
     *
     * <p>{@code @CacheEvict} — a new alert invalidates every cached nearby-alerts
     * result. Without it, a report could sit invisible for the full cache TTL, which
     * for a safety-alert product is the wrong tradeoff even at 15 seconds. Eviction
     * stays here, synchronous: a stale read is a user-visible bug, unlike a late push.
     *
     * <h2>Why the fan-out is an event</h2>
     * This method used to resolve the audience and push over WebSocket before returning,
     * all inside the transaction. Two problems. It held a pooled database connection
     * open across socket writes; and because the push happened before commit, a
     * subsequent rollback left clients showing an alert that does not exist. Publishing
     * instead means {@link NewAlertFanout} runs after commit, so it can only ever
     * announce alerts that are really there.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert createAlert(CreateAlertCommand command) {
        Location location = Location.of(command.latitude(), command.longitude());

        Alert alert = Alert.create(
            command.title(),
            command.description(),
            command.type(),
            command.severity(),
            location,
            command.reportedBy(),
            command.photoUrl()
        );

        Alert savedAlert = alertRepository.save(alert);

        events.publishEvent(new AlertCreated(savedAlert));

        return savedAlert;
    }
}
