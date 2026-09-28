package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ReportRanking;

import java.util.List;
import java.util.Set;

/**
 * The public list of reports: every district, or one, in the order a reader asks for.
 *
 * <p>Open to anyone, signed in or not. The point of the page is that what a district has
 * been told, and what it has done about it, is visible to the people who told it — and to
 * anyone else who wants to look.
 */
public interface ListRankedReportsUseCase {

    RankedPage listRanked(RankedQuery query);

    /**
     * @param districtCode null for the whole city
     * @param statuses     empty means the open ones — what the page is mostly for
     * @param page         zero-based
     */
    record RankedQuery(String districtCode, Set<AlertStatus> statuses, ReportRanking ranking, int page, int size) {

        public static final int MAX_SIZE = 100;

        public static final Set<AlertStatus> OPEN = Set.of(
            AlertStatus.SUBMITTED, AlertStatus.SENT, AlertStatus.ACKNOWLEDGED, AlertStatus.IN_PROGRESS);

        public RankedQuery {
            if (page < 0) {
                throw new IllegalArgumentException("Page must not be negative");
            }
            if (size < 1 || size > MAX_SIZE) {
                throw new IllegalArgumentException("Size must be between 1 and " + MAX_SIZE);
            }
            statuses = statuses == null || statuses.isEmpty() ? OPEN : Set.copyOf(statuses);
            ranking = ranking == null ? ReportRanking.HOT : ranking;
        }
    }

    /** One page, and how many there are in all, so a client can tell there is another. */
    record RankedPage(List<Alert> alerts, long total) {
    }
}
