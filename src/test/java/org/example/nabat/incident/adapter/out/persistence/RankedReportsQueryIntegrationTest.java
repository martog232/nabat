package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.out.AlertRepository.RankedSearch;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.incident.domain.ReportRanking;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.shared.persistence.SpatialCapabilityDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The public list's three orders and its filters, against PostgreSQL.
 *
 * <p>The HOT order is a formula evaluated in SQL — power(), EXTRACT(EPOCH ...), an interval
 * between an unzoned timestamp and LOCALTIMESTAMP — so what it returns is a fact about the
 * database, not something a mock can agree with. Each case is built so that the formula,
 * raw support and raw age would each give a different answer.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AlertRepositoryAdapter.class, SpatialCapabilityDetector.class})
class RankedReportsQueryIntegrationTest extends PostgresTestSupport {

    private static final Set<AlertStatus> OPEN = Set.of(
        AlertStatus.SUBMITTED, AlertStatus.SENT, AlertStatus.ACKNOWLEDGED, AlertStatus.IN_PROGRESS);

    @Autowired private AlertRepositoryAdapter alerts;
    @Autowired private AlertJpaRepository jpa;
    @Autowired private UserJpaRepository users;

    private UUID reporter;

    @BeforeEach
    void setUp() {
        jpa.deleteAllInBatch();
        users.deleteAll();
        User user = User.create("ranked@example.com", "hash", "Ranked");
        users.saveAndFlush(UserJpaEntity.from(user));
        reporter = user.id().value();
    }

    /**
     * Ten confirmations a week ago beat a new report nobody has confirmed, and lose to three
     * confirmations yesterday — the example in ReportRanking.HOT's documentation. Raw support
     * would put the week-old one first; raw age would put the new one first.
     */
    @Test
    void hotWeighsSupportAgainstAge() {
        save("week old, ten confirmations", "LOZENETS", 7, 10, AlertStatus.SENT);
        save("new, unconfirmed", "LOZENETS", 0, 0, AlertStatus.SUBMITTED);
        save("yesterday, three confirmations", "LOZENETS", 1, 3, AlertStatus.SENT);

        assertThat(titles(ReportRanking.HOT, null, OPEN))
            .containsExactly("yesterday, three confirmations", "week old, ten confirmations", "new, unconfirmed");
    }

    @Test
    void longestWaitingPutsTheOldestOpenReportFirst() {
        save("two days", "LOZENETS", 2, 50, AlertStatus.SENT);
        save("a month", "LOZENETS", 30, 0, AlertStatus.ACKNOWLEDGED);
        save("today", "LOZENETS", 0, 5, AlertStatus.SUBMITTED);

        assertThat(titles(ReportRanking.LONGEST_WAITING, null, OPEN))
            .containsExactly("a month", "two days", "today");
        assertThat(titles(ReportRanking.NEWEST, null, OPEN))
            .containsExactly("today", "two days", "a month");
    }

    /**
     * Support is the people who see it: older upvotes count with confirmations, and downvotes
     * — no longer taken — do not sink a report. One that is not real is rejected, with a reason.
     */
    @Test
    void supportIsWhoSeesItAndDownvotesNoLongerSubtract() {
        Alert downvoted = save("five old downvotes, two days", "LOZENETS", 2, 0, AlertStatus.SENT)
            .withVoteCounts(0, 5, 2, 0);
        alerts.save(downvoted);
        Alert upvoted = save("two old upvotes, two days", "LOZENETS", 2, 0, AlertStatus.SENT)
            .withVoteCounts(2, 0, 1, 0);
        alerts.save(upvoted);
        save("three confirmations, two days", "LOZENETS", 2, 3, AlertStatus.SENT);

        // Support 2, 3 and 3. The downvotes would have put the first one at the bottom; they
        // are ignored, and two confirmations rank as two confirmations.
        assertThat(titles(ReportRanking.HOT, null, OPEN).getLast()).isEqualTo("five old downvotes, two days");
        assertThat(titles(ReportRanking.HOT, null, OPEN)).hasSize(3);
    }

    @Test
    void filtersByDistrictAndStatusAndCountsAcrossPages() {
        save("lozenets open", "LOZENETS", 1, 0, AlertStatus.SENT);
        save("lozenets open 2", "LOZENETS", 2, 0, AlertStatus.IN_PROGRESS);
        save("triaditsa open", "TRIADITSA", 1, 0, AlertStatus.SENT);
        Alert closed = save("lozenets closed", "LOZENETS", 3, 0, AlertStatus.SENT).resolve(ClosedBy.AUTHORITY);
        alerts.save(closed);

        assertThat(titles(ReportRanking.NEWEST, "LOZENETS", OPEN))
            .containsExactly("lozenets open", "lozenets open 2");
        assertThat(titles(ReportRanking.NEWEST, "LOZENETS", Set.of(AlertStatus.RESOLVED)))
            .containsExactly("lozenets closed");
        assertThat(titles(ReportRanking.NEWEST, null, OPEN)).hasSize(3);

        RankedSearch secondPage = new RankedSearch(null, OPEN, ReportRanking.NEWEST, 2, 2);
        assertThat(alerts.findRanked(secondPage)).extracting(Alert::title).containsExactly("lozenets open 2");
        assertThat(alerts.countRanked(secondPage)).isEqualTo(3);
    }

    /** Past its deadline and still waiting: only those, and the count agrees. */
    @Test
    void filtersToReportsPastTheirAnswerDeadline() {
        Alert late = save("late", "LOZENETS", 40, 0, AlertStatus.SUBMITTED);
        alerts.save(late.markSent(Instant.now().minus(Duration.ofDays(10))));
        Alert inTime = save("in time", "LOZENETS", 2, 0, AlertStatus.SUBMITTED);
        alerts.save(inTime.markSent(Instant.now().plus(Duration.ofDays(28))));
        // Answered: no deadline running, however old.
        save("answered", "LOZENETS", 60, 0, AlertStatus.ACKNOWLEDGED);

        RankedSearch overdue = new RankedSearch(null, OPEN, ReportRanking.LONGEST_WAITING, 0, 50, true);
        assertThat(alerts.findRanked(overdue)).extracting(Alert::title).containsExactly("late");
        assertThat(alerts.countRanked(overdue)).isEqualTo(1);
        assertThat(titles(ReportRanking.LONGEST_WAITING, null, OPEN)).hasSize(3);
    }

    private java.util.List<String> titles(ReportRanking ranking, String district, Set<AlertStatus> statuses) {
        return alerts.findRanked(new RankedSearch(district, statuses, ranking, 0, 50)).stream()
            .map(Alert::title)
            .toList();
    }

    private Alert save(String title, String district, int daysAgo, int confirmations, AlertStatus status) {
        Alert alert = new Alert(
            AlertId.generate(), title, "d", 1007, Location.of(42.67, 23.32),
            Instant.now().minus(Duration.ofDays(daysAgo)).minus(Duration.ofMinutes(1)),
            status, reporter,
            0, 0, confirmations, 0,
            null, null, district, null);
        jpa.saveAndFlush(AlertJpaEntity.from(alert));
        return alert;
    }
}
