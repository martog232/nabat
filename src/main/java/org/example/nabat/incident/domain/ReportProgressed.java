package org.example.nabat.incident.domain;

/**
 * The authority acknowledged a report, started work on it, or rejected it — or a moderator
 * rejected it. Published so the reporter hears: between "sent" and "resolved" a report could
 * otherwise move twice without the one person who cares being told.
 *
 * <p>Resolving has {@link AlertResolved}, which also asks whether it is really fixed, and a
 * forward has {@link ReportForwarded}; neither is published as this as well.
 *
 * <p>Stored by the Event Publication Registry and replayed from JSON, like
 * {@link AlertCreated}: anything added here must survive a Jackson round-trip.
 *
 * @param alert         the report as it is after the move; its status says which it was, and
 *                      for a rejection {@link Alert#closedBy()} says by whom
 * @param authorityName the report's authority by name, carried so the notification needs no
 *                      way to look authorities up
 * @param reason        why, for a rejection (V31); null otherwise, and on events stored before
 */
public record ReportProgressed(Alert alert, String authorityName, String reason) {

    /** Acknowledged or started: nothing to explain. */
    public ReportProgressed(Alert alert, String authorityName) {
        this(alert, authorityName, null);
    }
}
