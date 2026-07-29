package org.example.nabat.domain.exception;

import org.example.nabat.domain.model.AlertId;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(AlertId id) {
        super("Alert not found: " + id);
    }

    /**
     * For cases where the absence is reported by a collaborator rather than
     * discovered locally — e.g. the voting service answering 404 for an alert id.
     */
    public AlertNotFoundException(String message) {
        super(message);
    }
}
