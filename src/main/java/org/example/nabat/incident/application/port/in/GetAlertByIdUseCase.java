package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;

public interface GetAlertByIdUseCase {

    /** Returns the alert or throws {@link org.example.nabat.incident.domain.AlertNotFoundException}. */
    Alert getById(AlertId id);
}

