package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;

public interface GetAlertByIdUseCase {

    /** Returns the alert or throws {@link org.example.nabat.domain.exception.AlertNotFoundException}. */
    Alert getById(AlertId id);
}

