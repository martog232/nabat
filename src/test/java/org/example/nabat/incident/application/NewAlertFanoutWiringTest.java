package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.out.AlertAudiencePort;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.platform.AsyncConfig;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the fan-out is actually wired, not merely annotated.
 *
 * <p>{@code NewAlertFanoutTest} calls the handler directly, so it would keep passing if
 * {@code @EnableAsync} were dropped, if the listener bean were never registered, or if the
 * event were published outside a transaction — each of which makes the fan-out silently
 * never happen in production. Nothing else covers that: the only end-to-end path through
 * {@code POST /api/v1/alerts} is a Testcontainers test, skipped wherever Docker is
 * unavailable.
 *
 * <p>So this builds the smallest context that can express the contract — async enabled,
 * transaction management enabled, the real listener bean — and publishes from inside a
 * transaction. No database: a no-op transaction manager is enough, because what is being
 * verified is the event plumbing, not persistence.
 */
@SpringJUnitConfig(NewAlertFanoutWiringTest.Config.class)
class NewAlertFanoutWiringTest {

    @Autowired
    private TransactionalPublisher publisher;

    @Autowired
    private RecordingNotificationPort notificationPort;

    @BeforeEach
    void reset() {
        // The context is shared across both tests, so the recorder has to be cleared.
        notificationPort.broadcasts.clear();
    }

    @Test
    void listenerRunsAfterCommitOnAnotherThread() throws Exception {
        Alert alert = alert();

        publisher.publishWithin(new AlertCreated(alert));

        Broadcast broadcast = notificationPort.broadcasts.poll(5, TimeUnit.SECONDS);
        assertNotNull(broadcast, "the fan-out listener never ran: @ApplicationModuleListener is not wired");
        assertEquals(alert, broadcast.alert());
        assertEquals(List.of(FixedAudience.RECIPIENT), broadcast.recipients());

        // Off the publishing thread, which is what makes POST /alerts return without
        // waiting for the fan-out. Fails if @EnableAsync is missing, in which case the
        // listener would still fire — just on the caller's thread, after commit.
        assertNotEquals(
            Thread.currentThread().getName(),
            broadcast.threadName(),
            "the fan-out ran on the publishing thread: @Async is not in effect"
        );
    }

    @Test
    void nothingIsBroadcastWhenTheTransactionRollsBack() throws Exception {
        assertThrows(
            IllegalStateException.class,
            () -> publisher.publishAndRollback(new AlertCreated(alert()))
        );

        assertNull(
            notificationPort.broadcasts.poll(1, TimeUnit.SECONDS),
            "a rolled-back alert was broadcast anyway; the listener is not bound to commit"
        );
    }

    private static Alert alert() {
        return new Alert(
            AlertId.generate(),
            "Wiring check",
            "desc",
            AlertType.HAZARD,
            AlertSeverity.HIGH,
            Location.of(42.0, 23.0),
            Instant.now(),
            AlertStatus.ACTIVE,
            UUID.randomUUID(),
            0, 0, 0, 0,
            null,
            null
        );
    }

    @Configuration
    @EnableTransactionManagement
    @Import(AsyncConfig.class)
    static class Config {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }

        @Bean
        AlertAudiencePort audiencePort() {
            return new FixedAudience();
        }

        @Bean
        RecordingNotificationPort notificationPort() {
            return new RecordingNotificationPort();
        }

        @Bean
        NewAlertFanout fanout(AlertAudiencePort audiencePort, AlertNotificationPort notificationPort) {
            return new NewAlertFanout(audiencePort, notificationPort);
        }

        @Bean
        TransactionalPublisher publisher(ApplicationEventPublisher events) {
            return new TransactionalPublisher(events);
        }
    }

    /** Publishes inside a transaction, which is what an after-commit listener requires. */
    static class TransactionalPublisher {

        private final ApplicationEventPublisher events;

        TransactionalPublisher(ApplicationEventPublisher events) {
            this.events = events;
        }

        @Transactional
        public void publishWithin(AlertCreated event) {
            events.publishEvent(event);
        }

        @Transactional
        public void publishAndRollback(AlertCreated event) {
            events.publishEvent(event);
            throw new IllegalStateException("forced rollback");
        }
    }

    static class FixedAudience implements AlertAudiencePort {

        static final UUID RECIPIENT = UUID.randomUUID();

        @Override
        public List<UUID> recipientsFor(AlertType type, Location location, double broadcastRadiusKm) {
            return List.of(RECIPIENT);
        }
    }

    record Broadcast(Alert alert, List<UUID> recipients, String threadName) {}

    /** A queue rather than a mock, because the assertion has to wait for another thread. */
    static class RecordingNotificationPort implements AlertNotificationPort {

        final BlockingQueue<Broadcast> broadcasts = new LinkedBlockingQueue<>();

        @Override
        public void broadcastAlert(Alert alert, List<UUID> userIds) {
            broadcasts.add(new Broadcast(alert, List.copyOf(userIds), Thread.currentThread().getName()));
        }

        @Override
        public void broadcastAlertUpdate(Alert alert) {
            throw new UnsupportedOperationException("not part of the creation fan-out");
        }
    }

    /**
     * Enough of a transaction manager to open a scope, commit it and fire the
     * synchronizations an after-commit listener hangs off. Holds no resources.
     */
    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // nothing to begin
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // nothing to commit; AbstractPlatformTransactionManager still triggers the
            // after-commit synchronizations, which is the point.
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // nothing to roll back
        }
    }
}
