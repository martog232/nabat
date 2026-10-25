package org.example.nabat.incident.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertStatus;

import java.util.List;
import java.util.Set;

/**
 * The reports the authority an official acts for answers for — their working list.
 *
 * <p>The authority comes from the account, never from the request. An official asking for
 * another authority's reports is not a query with a different parameter; it is not a question
 * this use case can be asked.
 */
public interface ListAssignedReportsUseCase {

    /** Applied when the caller does not choose: everything still waiting on the authority. */
    Set<AlertStatus> OPEN = Set.of(
        AlertStatus.SUBMITTED, AlertStatus.SENT, AlertStatus.ACKNOWLEDGED, AlertStatus.IN_PROGRESS);

    /** Enough for a working list; ranking and paging arrive with the public page. */
    int LIMIT = 500;

    /**
     * @param statuses which to include; empty means {@link #OPEN}
     * @throws org.example.nabat.shared.domain.NotAuthorizedException if {@code actor} is not an
     *         official
     */
    List<Alert> listForOwnAuthority(User actor, Set<AlertStatus> statuses);
}
