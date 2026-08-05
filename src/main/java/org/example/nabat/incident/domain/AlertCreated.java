package org.example.nabat.incident.domain;

/**
 * A new alert has been reported and committed.
 *
 * <p>Published by {@code CreateAlertService} and handled after the transaction commits,
 * so that everything downstream of "an alert now exists" — fanning it out to interested
 * users, pushing it over WebSocket — happens outside the database transaction rather
 * than inside it.
 *
 * <p>Why it carries the whole {@link Alert}: the aggregate is an immutable record and
 * the handler is in this same module, so passing it costs nothing and avoids a re-read
 * that could observe a later state than the one that was created. When this becomes a
 * Kafka event crossing a service boundary it has to be flattened into an explicit
 * schema — an aggregate is not a wire contract — but that is a phase 2 concern.
 *
 * <p>Consumed today only within this module. It is nonetheless part of the module's
 * exposed API, because it is the seam a future notification service subscribes to
 * instead of this module doing its own fan-out.
 */
public record AlertCreated(Alert alert) {
}
