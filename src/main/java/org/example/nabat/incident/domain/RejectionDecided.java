package org.example.nabat.incident.domain;

/**
 * A moderator decided a reporter's dispute of a rejection: upheld, or the report reopened with
 * its authority. Published so the reporter hears the answer to what they asked.
 *
 * <p>Stored by the Event Publication Registry and replayed from JSON, like
 * {@link AlertCreated}: anything added here must survive a Jackson round-trip.
 *
 * @param alert         the report as it is after the decision
 * @param authorityName its authority by name, which a reopened report is waiting on again
 */
public record RejectionDecided(Alert alert, ReportRejection.Decision decision, String authorityName) {
}
