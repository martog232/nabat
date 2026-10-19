package org.example.nabat.incident.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.ReportRejection;

import java.util.List;

/**
 * Closing a report without a fix, and the reporter's way to contest it.
 *
 * <p>A rejection is final and locks the thread, so it is guarded like nothing else: it says
 * why, the reporter may dispute it once, and a moderator — someone outside both — decides. A
 * resolved report can already be contested through "is it really fixed?"; without this, a
 * rejected one could not, and rejecting would be the easy way to be rid of a report.
 */
public interface RejectReportUseCase {

    /** How many open disputes a moderator's queue shows at once. */
    int DISPUTE_QUEUE_LIMIT = 200;

    /**
     * Closes the report without a fix: a duplicate, not a real problem. The report's authority
     * or a moderator — not the reporter, who may resolve their report but not dismiss it. The
     * reason goes in the thread and to the reporter. Something that is another authority's is
     * not a rejection; that is a forward.
     *
     * @throws org.example.nabat.shared.domain.NotAuthorizedException for anyone else
     * @throws IllegalArgumentException for no reason, or one over
     *         {@link ReportRejection#MAX_REASON_LENGTH} characters
     * @throws IllegalStateException    if the report is closed already
     */
    Alert reject(AlertId id, User actor, String reason);

    /** Every rejection of the report, oldest first; empty if it never was. */
    List<ReportRejection> rejections(AlertId id);

    /**
     * The reporter contests the rejection, once, saying why. It stands until a moderator decides.
     *
     * @throws org.example.nabat.shared.domain.NotAuthorizedException for anyone but the reporter
     * @throws IllegalStateException if the report is not rejected, its rejection has no record
     *         (before V31), or it has been disputed already
     */
    ReportRejection dispute(AlertId id, User reporter, String reason);

    /**
     * A moderator answers a dispute: the rejection stands, or the report is open again with
     * its authority, which has it already.
     *
     * @throws org.example.nabat.shared.domain.NotAuthorizedException for anyone but a moderator
     * @throws IllegalStateException if there is no undecided dispute on the report
     */
    Alert decide(AlertId id, User moderator, ReportRejection.Decision decision);

    /** Reports whose rejection is disputed and waiting for a moderator, the oldest dispute first. */
    List<Alert> disputed(User moderator);
}
