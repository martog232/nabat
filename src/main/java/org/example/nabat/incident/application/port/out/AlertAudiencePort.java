package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;

import java.util.List;
import java.util.UUID;

/**
 * Who should be told about a new alert.
 *
 * <p>This module decides <em>that</em> an audience must be reached and how wide the
 * broadcast radius is for a given severity; it does not know how the audience is
 * assembled. {@code CreateAlertService} previously queried the subscription and
 * identity repositories itself, which made incident depend on subscription while
 * subscription depends on incident's {@link AlertType} — the cycle Spring Modulith
 * rejected.
 *
 * <p>Declared here and implemented by the module that holds the data, which is the
 * ordinary hexagonal answer to "my use case needs something I do not own". When
 * subscriptions move behind an event-driven notification service, this port becomes
 * that service's subscription to {@code incident.created} and disappears from here.
 */
public interface AlertAudiencePort {

    /**
     * @param type              the alert's category, which subscribers opt into
     * @param location          where the alert was reported
     * @param broadcastRadiusKm how far to fan out, derived from the alert's severity
     * @return distinct user ids to notify, in no particular order. Never null.
     */
    List<UUID> recipientsFor(AlertType type, Location location, double broadcastRadiusKm);
}
