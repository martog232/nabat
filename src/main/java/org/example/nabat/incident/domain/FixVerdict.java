package org.example.nabat.incident.domain;

/**
 * One person's answer to "is it really fixed?" about a report closed as resolved.
 *
 * <p>Persisted by name and pinned by a {@code CHECK} in {@code V24__fix_checks.sql}.
 */
public enum FixVerdict {
    /** „Оправено е“ */
    FIXED,
    /** „Частично“ — something was done, not all of it. */
    PARTLY,
    /** „Не е оправено“ */
    NOT_FIXED
}
