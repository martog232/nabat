package org.example.nabat.incident.application.port.in;

/**
 * One pass over the reports past their answer deadline: remind the authorities that have let
 * one pass, and tell the municipality about those still silent well after the reminder.
 */
public interface WatchDeadlinesUseCase {

    Result watch();

    /** How many reports were reminded about, and how many escalated, in this pass. */
    record Result(int reminded, int escalated) {
    }
}
