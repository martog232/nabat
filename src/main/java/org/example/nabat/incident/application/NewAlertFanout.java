package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Puts a newly created report on every open map.
 *
 * <p>This used to push each new alert only to an audience — subscribers to its type
 * within a broadcast radius, plus users whose last known location was near it. That
 * served a feed about danger nearby. A report about a pothole is not something to push
 * at a passer-by; it is something the map should show, to whoever is looking at it. So
 * the frame goes to every connected client, the same way an update does, and nobody's
 * location is needed to decide who gets it.
 *
 * <h2>Why this is a listener rather than a method call</h2>
 * {@link ApplicationModuleListener} is {@code @TransactionalEventListener(AFTER_COMMIT)}
 * plus {@code @Async} plus {@code @Transactional(REQUIRES_NEW)}:
 *
 * <ul>
 *   <li><strong>after commit</strong> — the push can no longer describe a report that a
 *       later rollback removed, and the writer's transaction is not held open across
 *       socket writes;</li>
 *   <li><strong>async</strong> — {@code POST /api/v1/alerts} returns as soon as the report
 *       is durable, instead of waiting on a broadcast;</li>
 *   <li><strong>new transaction</strong> — kept although nothing here reads the database
 *       any more, because it is part of the annotation, and the next listener on this event
 *       (delivering the report to the responsible district) will.</li>
 * </ul>
 *
 * <h2>Durability</h2>
 * The three properties above still leave a window: an async after-commit listener that
 * dies between the commit and the push loses the push with no record it was owed.
 * Spring Modulith's Event Publication Registry closes it, and the same annotation is all
 * it takes — {@code spring-modulith-events-jpa} on the classpath makes publishing write a
 * row per (event, listener) into {@code event_publication} <em>inside</em> the publishing
 * transaction, stamped complete only once this method returns.
 *
 * <p>So the outbox and the report commit or roll back together, and an incomplete row means
 * exactly "this listener still owes this event".
 * {@code spring.modulith.events.republish-outstanding-events-on-restart} replays those on
 * startup. See {@code AlertCreatedOutboxIntegrationTest}, which asserts against the table
 * rather than through an abstraction, because the table is the guarantee.
 *
 * <p>Note what this does <em>not</em> promise: delivery is retried, not guaranteed
 * exactly-once. A crash after the socket write but before completion replays the push, so
 * a client can see the same {@code NEW_ALERT} twice. That is the right trade here — the
 * frontend upserts by alert id — but it is at-least-once, not exactly-once.
 *
 * <p>The class and method keep their names on purpose: the registry identifies a listener
 * by them, so renaming either would orphan every publication still outstanding for it.
 */
@Component
public class NewAlertFanout {

    private static final Logger log = LoggerFactory.getLogger(NewAlertFanout.class);

    private final AlertNotificationPort notificationPort;

    public NewAlertFanout(AlertNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @ApplicationModuleListener
    public void on(AlertCreated event) {
        Alert alert = event.alert();
        log.debug("Putting new report {} (category {}) on the map", alert.id().value(), alert.categoryCode());
        notificationPort.broadcastNewAlert(alert);
    }
}
