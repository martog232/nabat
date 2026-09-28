package org.example.nabat.incident.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertStatus;

import java.util.List;
import java.util.Set;

/**
 * The reports in the district an official acts for — their working list.
 *
 * <p>The district comes from the account, never from the request. An official asking for
 * another district's reports is not a query with a different parameter; it is not a
 * question this use case can be asked.
 */
public interface ListDistrictReportsUseCase {

    /** Applied when the caller does not choose: everything still waiting on the district. */
    Set<AlertStatus> OPEN = Set.of(
        AlertStatus.SUBMITTED, AlertStatus.SENT, AlertStatus.ACKNOWLEDGED, AlertStatus.IN_PROGRESS);

    /** Enough for a working list; ranking and paging arrive with the public page. */
    int LIMIT = 500;

    /**
     * @param statuses which to include; empty means {@link #OPEN}
     * @throws org.example.nabat.shared.domain.NotAuthorizedException if {@code actor} is not a
     *         district official
     */
    List<Alert> listForOwnDistrict(User actor, Set<AlertStatus> statuses);
}
