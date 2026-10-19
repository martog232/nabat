package org.example.nabat.incident.domain;

/**
 * A report went to another authority. Published so it is mailed to the new one and its
 * reporter hears where it went.
 *
 * <p>Stored by the Event Publication Registry and replayed from JSON, like
 * {@link AlertCreated}: anything added here must survive a Jackson round-trip.
 *
 * @param alert           the report as it is after the forward, with its new authority
 * @param toAuthorityName the new authority's name, carried so the reporter's notification
 *                        needs no way to look authorities up
 */
public record ReportForwarded(Alert alert, ReportForward forward, String toAuthorityName) {
}
