package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.User;

public interface ResolveAlertUseCase {

    /**
     * Marks the alert as RESOLVED, recording who closed it: an official of its district, its
     * reporter, or a moderator — in that order of standing, see {@code ClosedBy}.
     * @throws org.example.nabat.incident.domain.AlertNotFoundException if no such alert
     * @throws org.example.nabat.shared.domain.NotAuthorizedException if {@code actor}
     *         is none of those three
     */
    Alert resolve(AlertId id, User actor);
}

