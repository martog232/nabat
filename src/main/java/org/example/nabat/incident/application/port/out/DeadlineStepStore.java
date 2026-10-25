package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.AnswerOverdue.Stage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The steps taken about reports past their answer deadline (V33). */
public interface DeadlineStepStore {

    /** Reports waiting past their deadline with no reminder about it yet, the most overdue first. */
    List<UUID> dueForReminder(Instant now, int limit);

    /**
     * Reports still waiting on the same deadline a reminder was sent about at or before
     * {@code remindedBy}, and not escalated yet, the longest reminded first.
     */
    List<UUID> dueForEscalation(Instant remindedBy, int limit);

    /**
     * Takes the step, unless it has been taken already for this deadline.
     *
     * @return whether this call took it — only then is anything to be done about it
     */
    boolean take(UUID alertId, Instant dueAt, Stage stage, Instant at);
}
