package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.out.AlertAudiencePort;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pushes a newly created alert to whoever should see it.
 *
 * <h2>Why this is a listener rather than a method call</h2>
 * {@link ApplicationModuleListener} is {@code @TransactionalEventListener(AFTER_COMMIT)}
 * plus {@code @Async} plus {@code @Transactional(REQUIRES_NEW)}. Each of the three earns
 * its place here:
 *
 * <ul>
 *   <li><strong>after commit</strong> — the push can no longer describe an alert that a
 *       later rollback removed, and the writer's transaction is not held open across
 *       socket writes;</li>
 *   <li><strong>async</strong> — {@code POST /api/v1/alerts} returns as soon as the alert
 *       is durable, instead of waiting on an audience query and a fan-out whose size
 *       grows with the broadcast radius;</li>
 *   <li><strong>new transaction</strong> — the audience lookup is itself a database
 *       read, and the original transaction is already gone by the time this runs.</li>
 * </ul>
 *
 * <h2>Durability</h2>
 * The three properties above still leave a window: an async after-commit listener that
 * dies between the commit and the push loses the fan-out with no record it was owed.
 * Spring Modulith's Event Publication Registry closes it, and the same annotation is all
 * it takes — {@code spring-modulith-events-jpa} on the classpath makes publishing write a
 * row per (event, listener) into {@code event_publication} <em>inside</em> the publishing
 * transaction, stamped complete only once this method returns.
 *
 * <p>So the outbox and the alert commit or roll back together, and an incomplete row means
 * exactly "this listener still owes this event".
 * {@code spring.modulith.events.republish-outstanding-events-on-restart} replays those on
 * startup. See {@code AlertCreatedOutboxIntegrationTest}, which asserts against the table
 * rather than through an abstraction, because the table is the guarantee.
 *
 * <p>Note what this does <em>not</em> promise: delivery is retried, not guaranteed
 * exactly-once. A crash after the socket write but before completion replays the push, so
 * a client can see the same {@code NEW_ALERT} twice. That is the right trade here — the
 * frontend upserts by alert id — but it is at-least-once, not exactly-once.
 */
@Component
public class NewAlertFanout {

    private static final Logger log = LoggerFactory.getLogger(NewAlertFanout.class);

    /**
     * How far a new report reaches <em>subscribers</em>: the widest subscription circle it
     * can match within.
     *
     * <p>One value, where there used to be four chosen by severity — 10 km for a critical
     * alert down to 1 km for a minor one. That scale was about danger, and a pothole is
     * not dangerous to someone two districts away; what a report is worth to a neighbour
     * is roughly walking distance, whatever its category.
     *
     * <p>Distinct from a user's own {@code notificationRadiusKm} preference, which the
     * audience port applies separately. See {@code NotificationRadius} — this value is not
     * drawn from that allow-list and is not user-selectable.
     */
    static final double BROADCAST_RADIUS_KM = 2.0;

    private final AlertAudiencePort audiencePort;
    private final AlertNotificationPort notificationPort;

    public NewAlertFanout(AlertAudiencePort audiencePort, AlertNotificationPort notificationPort) {
        this.audiencePort = audiencePort;
        this.notificationPort = notificationPort;
    }

    @ApplicationModuleListener
    public void on(AlertCreated event) {
        Alert alert = event.alert();

        List<UUID> recipients = new ArrayList<>(audiencePort.recipientsFor(
            alert.type(),
            alert.location(),
            BROADCAST_RADIUS_KM
        ));
        // The reporter does not need to be told about their own report.
        recipients.remove(alert.reportedBy());

        if (recipients.isEmpty()) {
            return;
        }

        log.debug("Pushing new {} report to {} recipient(s)", alert.type(), recipients.size());
        notificationPort.broadcastAlert(alert, recipients);
    }
}
