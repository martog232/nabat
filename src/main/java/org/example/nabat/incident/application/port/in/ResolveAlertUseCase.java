package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.User;

public interface ResolveAlertUseCase {

    /**
     * Marks the alert as RESOLVED. Only the original reporter or an ADMIN may resolve.
     * @throws org.example.nabat.incident.domain.AlertNotFoundException if no such alert
     * @throws org.example.nabat.shared.domain.NotAuthorizedException if {@code actor}
     *         is neither the reporter nor an ADMIN
     */
    Alert resolve(AlertId id, User actor);
}

