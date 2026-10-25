package org.example.nabat.incident.domain;

import java.time.Instant;

/**
 * A report is past the deadline for its authority's answer, and something is being done about
 * it: the authority reminded, or — still nothing well after the reminder — the municipality
 * told. Published once per stage and deadline, so the authority is mailed and the reporter
 * hears.
 *
 * <p>Stored by the Event Publication Registry and replayed from JSON, like
 * {@link AlertCreated}: anything added here must survive a Jackson round-trip.
 *
 * @param alert         the report, still waiting on its authority
 * @param dueAt         the deadline it is past
 * @param authorityName the authority it is waiting on, by name, for the reporter's message
 */
public record AnswerOverdue(Alert alert, Stage stage, Instant dueAt, String authorityName) {

    public enum Stage {
        /** The deadline passed: the authority is reminded. */
        REMINDER,
        /** Still nothing well after the reminder: the municipality is told. */
        ESCALATION
    }
}
