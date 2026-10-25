package org.example.nabat.incident.domain;

/**
 * Its reporter took a report back. Published so an authority that already has it can stop
 * working on it.
 *
 * <p>Stored by the Event Publication Registry and replayed from JSON, like
 * {@link AlertCreated}: anything added here must survive a Jackson round-trip.
 *
 * @param alert     the report, withdrawn
 * @param reason    why, in the reporter's words
 * @param delivered whether its authority had been sent it — if not, there is nobody to tell
 */
public record ReportWithdrawn(Alert alert, String reason, boolean delivered) {
}
