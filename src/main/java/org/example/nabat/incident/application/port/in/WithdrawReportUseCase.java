package org.example.nabat.incident.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;

/**
 * The reporter takes their report back.
 *
 * <p>Before this, the only way a reporter could close their own report was to mark it resolved,
 * which said a problem was fixed when it was a mistake, a duplicate they noticed, or nothing at
 * all — and then asked the city whether it really was fixed.
 */
public interface WithdrawReportUseCase {

    /** As long as the column allows. */
    int MAX_REASON_LENGTH = 1000;

    /**
     * Withdraws an open report, saying why: final, and in the thread. The authority is told if
     * it had already been sent the report.
     *
     * @throws org.example.nabat.shared.domain.NotAuthorizedException for anyone but its reporter —
     *         a moderator rejects; an authority rejects or forwards
     * @throws IllegalArgumentException for no reason, or one too long
     * @throws IllegalStateException    if the report is closed already
     */
    Alert withdraw(AlertId id, User reporter, String reason);
}
