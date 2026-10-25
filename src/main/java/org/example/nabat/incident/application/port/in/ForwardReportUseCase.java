package org.example.nabat.incident.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.ReportForward;

import java.util.List;

/**
 * Handing a report to the authority it really belongs to — "forwarding by competence".
 *
 * <p>The answer to a wrong routing, which the routing by category will sometimes be. Without
 * it, an authority sent someone else's report could only reject it: final, the thread
 * closed, and in public it reads as a refusal when it was a wrong address.
 */
public interface ForwardReportUseCase {

    /**
     * After this many forwards only a moderator forwards the report again. Two authorities
     * that each think it is the other's could otherwise pass it between them for ever; past
     * this, someone outside both has to look.
     */
    int FORWARDS_BEFORE_A_MODERATOR_DECIDES = 3;

    /**
     * Moves the report to {@code toAuthority}, back to {@code SUBMITTED} until it is mailed
     * there, and says why in its thread.
     *
     * <p>Made by an official of the authority that has it, or by a moderator. Not by the
     * reporter: they chose the category, which is what chose the authority.
     *
     * @param reason why it is the other authority's, 1 to
     *               {@link org.example.nabat.incident.domain.ReportForward#MAX_REASON_LENGTH}
     *               characters — a forward without a reason is only passing the problem on
     * @throws org.example.nabat.incident.domain.AlertNotFoundException for an unknown report
     * @throws org.example.nabat.shared.domain.NotAuthorizedException   when {@code actor} may not
     *         forward it, including an official once the report has been forwarded
     *         {@link #FORWARDS_BEFORE_A_MODERATOR_DECIDES} times
     * @throws IllegalArgumentException for no reason, an unknown authority, or the same one
     * @throws IllegalStateException    when the report is closed
     */
    Alert forward(AlertId id, User actor, String toAuthority, String reason);

    /** The report's forwards, oldest first. */
    List<ReportForward> forwards(AlertId id);
}
