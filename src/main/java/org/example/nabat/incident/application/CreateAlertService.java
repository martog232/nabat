package org.example.nabat.incident.application;

import org.example.nabat.shared.UseCase;
import org.example.nabat.incident.application.port.in.CreateAlertUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.DistrictLookupPort;
import org.example.nabat.incident.application.port.out.ReportCategoryRepository;
import org.example.nabat.incident.application.port.out.ResponsibleAuthorityPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.incident.domain.OutsideServiceAreaException;
import org.example.nabat.incident.domain.UnknownCategoryException;
import org.example.nabat.shared.domain.Location;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateAlertService implements CreateAlertUseCase {

    private final AlertRepository alertRepository;
    private final DistrictLookupPort districts;
    private final ResponsibleAuthorityPort authorities;
    private final ReportCategoryRepository categories;
    private final ApplicationEventPublisher events;

    public CreateAlertService(
        AlertRepository alertRepository,
        DistrictLookupPort districts,
        ResponsibleAuthorityPort authorities,
        ReportCategoryRepository categories,
        ApplicationEventPublisher events
    ) {
        this.alertRepository = alertRepository;
        this.districts = districts;
        this.authorities = authorities;
        this.categories = categories;
        this.events = events;
    }

    /**
     * Persists a new report and announces it.
     *
     * <p>A report is only accepted inside a district, because only in the city is there
     * anyone to send it to. Asked here, before anything is written, rather than left to a
     * later step that would have to find out on its own that the report has nowhere to go —
     * and the reporter would have been told "submitted" about something nobody will receive.
     * Who answers for it is decided here too, once: the district, or the institution its
     * category belongs to.
     *
     * <p>{@code @Transactional} — every other use case in this module is transactional
     * and this one was not, so the save and the reads that followed it could observe
     * inconsistent state.
     *
     * <p>{@code @CacheEvict} — a new report invalidates every cached nearby-alerts
     * result. Without it, a report could sit invisible for the full cache TTL. Eviction
     * stays here, synchronous: a stale read is a user-visible bug, unlike a late push.
     *
     * <h2>Why the push is an event</h2>
     * This method used to resolve an audience and push over WebSocket before returning,
     * all inside the transaction. Two problems. It held a pooled database connection
     * open across socket writes; and because the push happened before commit, a
     * subsequent rollback left clients showing a report that does not exist.
     * Publishing instead means {@link NewAlertFanout} runs after commit, so it can only
     * ever announce reports that are really there.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = "nearbyAlerts", allEntries = true)
    public Alert createAlert(CreateAlertCommand command) {
        // A group is a heading, not a place to file a report: the foreign key would accept
        // one, so this is where it is refused.
        categories.findByCode(command.categoryCode())
            .filter(category -> !category.isGroup())
            .orElseThrow(() -> new UnknownCategoryException(command.categoryCode()));

        Location location = Location.of(command.latitude(), command.longitude());

        DistrictLookupPort.District district = districts.districtAt(location)
            .orElseThrow(() -> new OutsideServiceAreaException(location));

        Alert alert = Alert.create(
            command.title(),
            command.description(),
            command.categoryCode(),
            location,
            command.reportedBy(),
            command.photoUrl(),
            district.code(),
            authorities.authorityFor(district.code(), command.categoryCode())
        );

        Alert savedAlert = alertRepository.save(alert);

        events.publishEvent(new AlertCreated(savedAlert));

        return savedAlert;
    }
}
