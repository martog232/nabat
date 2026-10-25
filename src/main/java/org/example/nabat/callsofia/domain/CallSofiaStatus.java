package org.example.nabat.callsofia.domain;

/**
 * Where a report stands in Call Sofia, in Call Sofia's terms.
 *
 * <p>Deliberately not {@link org.example.nabat.incident.domain.AlertStatus}. The two do not
 * line up, and forcing them to would put a claim in the data that Call Sofia never made:
 * its usual end state is {@link #ANSWERED} — the citizen was sent a reply — and a reply is
 * not a fix. Telling those apart is the point of reading this archive at all.
 *
 * <p>Persisted by name and pinned by a {@code CHECK} in {@code V21__call_sofia_archive.sql}.
 */
public enum CallSofiaStatus {
    /** „Неизвестен“ — the archive's own placeholder. */
    UNKNOWN,
    /** „Чакащ обработка“ — filed, not yet looked at. */
    AWAITING_PROCESSING,
    /** „В процес на обработка“ — taken up and routed to whoever is responsible. */
    PROCESSING,
    /** „Очакващ отговор“ — waiting for an answer from the body it was routed to. */
    AWAITING_REPLY,
    /** „Получил уведомление“ — the citizen was sent a reply. Says nothing about a fix. */
    ANSWERED,
    /** „Приключен“ — closed. */
    CLOSED,
    /** „Отхвърлен“ — rejected. */
    REJECTED
}
