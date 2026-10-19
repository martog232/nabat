package org.example.nabat.incident.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;

/**
 * What the authority responsible for a report says about it.
 *
 * <p>Acknowledging and starting work are the authority's alone — they are statements by the
 * body that will do the fixing, and nobody else can make them. Rejecting is
 * {@link RejectReportUseCase}'s, which also asks why.
 *
 * <p>Each method throws {@code AlertNotFoundException} for an unknown report,
 * {@code NotAuthorizedException} when {@code actor} may not make this statement about this
 * report — in particular an official of another authority — and {@code IllegalStateException}
 * when the report cannot move that way, being already past it or closed.
 */
public interface ProgressReportUseCase {

    Alert acknowledge(AlertId id, User actor);

    Alert startWork(AlertId id, User actor);
}
