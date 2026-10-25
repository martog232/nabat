package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.CommentRemoval;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comments against the real V27 schema, and the lock that keeps a comment from slipping in
 * as a report closes.
 *
 * <p>Not transactional, unlike the other {@code @DataJpaTest}s: the lock is only visible
 * between two transactions, so each test runs its own.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcCommentStore.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcCommentStoreIntegrationTest extends PostgresTestSupport {

    @Autowired private JdbcCommentStore store;
    @Autowired private AlertJpaRepository alertJpa;
    @Autowired private UserJpaRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    private TransactionTemplate tx;
    private AlertId alertId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactions);
        alertJpa.deleteAllInBatch();
        User reporter = User.create("comments-" + UUID.randomUUID() + "@example.com", "hash", "Подател");
        users.saveAndFlush(UserJpaEntity.from(reporter));
        alertId = AlertId.generate();
        alertJpa.saveAndFlush(AlertJpaEntity.from(new Alert(alertId, "Дупка", "Дълбока", 1007,
            Location.of(42.67, 23.32), Instant.now(), AlertStatus.SENT, reporter.id().value(),
            0, 0, 0, 0, null, null, "LOZENETS", null)));
    }

    @Test
    void keepsAThreadInOrderAndHidesWhatWasTakenDown() {
        Comment first = comment(UUID.randomUUID(), "Първи", Instant.parse("2026-10-14T10:00:00Z"));
        Comment second = comment(UUID.randomUUID(), "Втори", Instant.parse("2026-10-14T11:00:00Z"));
        tx.executeWithoutResult(s -> {
            store.insert(second);
            store.insert(first);
            store.markRemoved(first.id(), Instant.now(), CommentRemoval.MODERATOR);
        });

        var thread = store.thread(alertId.value());

        assertThat(thread).extracting(Comment::id).containsExactly(first.id(), second.id());
        assertThat(thread.getFirst().body()).isNull();
        assertThat(thread.getFirst().removedBy()).isEqualTo(CommentRemoval.MODERATOR);
        assertThat(thread.get(1)).isEqualTo(second);
    }

    /** Someone whose only comment was taken down is no longer in the conversation. */
    @Test
    void theThreadsAuthorsAreThoseWithACommentStanding() {
        UUID stays = UUID.randomUUID();
        UUID leaves = UUID.randomUUID();
        Comment gone = comment(leaves, "Ще го махна", Instant.now());
        tx.executeWithoutResult(s -> {
            store.insert(comment(stays, "Тук съм", Instant.now()));
            store.insert(gone);
            store.markRemoved(gone.id(), Instant.now(), CommentRemoval.AUTHOR);
        });

        assertThat(store.authorsOn(alertId.value())).containsExactly(stays);
    }

    @Test
    void aCommentIsOnlyFoundOnItsOwnReport() {
        Comment c = comment(UUID.randomUUID(), "Тук", Instant.now());
        tx.executeWithoutResult(s -> store.insert(c));

        assertThat(store.find(alertId.value(), c.id())).contains(c);
        assertThat(store.find(UUID.randomUUID(), c.id())).isEmpty();
    }

    /**
     * The race this store exists to close. A resolve has updated the report and not yet
     * committed; a comment checks the status meanwhile. A plain read would see the old, open
     * status and let the comment in after the report closed. Held {@code FOR SHARE}, the read
     * waits for the resolve and sees it.
     */
    @Test
    void aCommentWaitsForAClosingInFlightAndThenSeesItClosed() throws Exception {
        CountDownLatch rowTaken = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> resolving = pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.update("UPDATE alerts SET status = 'RESOLVED' WHERE id = ?", alertId.value());
                rowTaken.countDown();
                sleep(500);
            }));
            assertThat(rowTaken.await(10, TimeUnit.SECONDS)).isTrue();

            AlertStatus seen = tx.execute(s -> store.holdReport(alertId.value()));

            resolving.get(10, TimeUnit.SECONDS);
            assertThat(seen).isEqualTo(AlertStatus.RESOLVED);
        } finally {
            pool.shutdownNow();
        }
    }

    /** And the other way: a comment holding the report makes a closing wait for it. */
    @Test
    void aClosingWaitsForACommentInFlight() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Comment c = comment(UUID.randomUUID(), "Преди затварянето", Instant.now());
            Future<?> commenting = pool.submit(() -> tx.executeWithoutResult(s -> {
                assertThat(store.holdReport(alertId.value())).isEqualTo(AlertStatus.SENT);
                held.countDown();
                sleep(500);
                store.insert(c);
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();

            tx.executeWithoutResult(s -> {
                jdbc.update("UPDATE alerts SET status = 'RESOLVED' WHERE id = ?", alertId.value());
                // The update could only proceed once the comment's transaction had ended.
                assertThat(jdbc.queryForObject("SELECT count(*) FROM alert_comments WHERE id = ?", Integer.class, c.id()))
                    .isEqualTo(1);
            });
            commenting.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private Comment comment(UUID author, String body, Instant at) {
        return new Comment(UUID.randomUUID(), alertId, author, "Някой", false, false, body,
            at.truncatedTo(ChronoUnit.MICROS), null, null);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
