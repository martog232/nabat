package org.example.nabat.incident.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;

/**
 * What the district responsible for a report says about it.
 *
 * <p>Acknowledging and starting work are the district's alone — they are statements by the
 * body that will do the fixing, and nobody else can make them. Rejecting is the district's or
 * a moderator's: "not a real problem" is a judgement about content as much as about the city.
 *
 * <p>Each method throws {@code AlertNotFoundException} for an unknown report,
 * {@code NotAuthorizedException} when {@code actor} may not make this statement about this
 * report — in particular an official of another district — and {@code IllegalStateException}
 * when the report cannot move that way, being already past it or closed.
 */
public interface ProgressReportUseCase {

    Alert acknowledge(AlertId id, User actor);

    Alert startWork(AlertId id, User actor);

    Alert reject(AlertId id, User actor);
}
