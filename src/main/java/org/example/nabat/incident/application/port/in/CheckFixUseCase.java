package org.example.nabat.incident.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.FixCheckTally;
import org.example.nabat.incident.domain.FixVerdict;

/**
 * "Is it really fixed?" — asked of people about a report closed as resolved.
 */
public interface CheckFixUseCase {

    /**
     * Records the actor's answer, replacing an earlier one of theirs.
     *
     * @throws IllegalStateException if the report is not resolved — there is nothing to check
     * @throws org.example.nabat.shared.domain.NotAuthorizedException if the actor speaks for the
     *         report's authority, whose work this checks
     */
    FixCheckView check(AlertId id, User actor, FixVerdict verdict);

    /** Takes the actor's answer back. Not an error if there was none. */
    FixCheckView withdraw(AlertId id, User actor);

    /** The tally, and what the actor has said, if anything. */
    FixCheckView view(AlertId id, User actor);

    /** @param mine the actor's own answer; null if they have not given one */
    record FixCheckView(FixCheckTally tally, FixVerdict mine) {
    }
}
