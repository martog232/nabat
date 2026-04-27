package org.example.nabat.domain.exception;

import org.example.nabat.domain.model.AlertId;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(AlertId id) {
        super("Alert not found: " + id);
    }
}
