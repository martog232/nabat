package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.User;

public interface ResolveAlertUseCase {

    /**
     * Marks the alert as RESOLVED. Only the original reporter or an ADMIN may resolve.
     * @throws org.example.nabat.domain.exception.AlertNotFoundException if no such alert
     * @throws org.springframework.security.access.AccessDeniedException if {@code actor}
     *         is neither the reporter nor an ADMIN
     */
    Alert resolve(AlertId id, User actor);
}

