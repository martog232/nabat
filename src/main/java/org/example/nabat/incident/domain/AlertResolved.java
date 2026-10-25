package org.example.nabat.incident.domain;

/**
 * A report was closed as resolved. Published so its reporter can be asked whether it is
 * really fixed; the report carries {@link Alert#closedBy()}, so a listener can tell a
 * reporter closing their own report from the authority closing it for them.
 *
 * <p>Stored by the Event Publication Registry and replayed from JSON, like
 * {@link AlertCreated}: anything added here must survive a Jackson round-trip.
 */
public record AlertResolved(Alert alert) {
}
