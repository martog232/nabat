package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.in.WatchDeadlinesUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.DeadlineStepStore;
import org.example.nabat.incident.application.port.out.ResponsibleAuthorityPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AnswerOverdue;
import org.example.nabat.incident.domain.AnswerOverdue.Stage;
import org.example.nabat.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Finds reports past their answer deadline and takes the next step: remind the authority, and
 * if nothing comes of that within {@code nabat.reports.escalate-after}, tell the municipality.
 *
 * <p>Each report in a transaction of its own, so one that fails does not hold back the rest,
 * and each step is taken through {@link DeadlineStepStore#take}, which only one caller wins:
 * the event that mails and notifies is published only by the run that took the step.
 * The report is re-read inside that transaction, so one answered since the list was read is
 * left alone.
 */
@UseCase
public class DeadlineWatchService implements WatchDeadlinesUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeadlineWatchService.class);

    /** A run's worth; the rest is found on the next. */
    private static final int BATCH = 200;

    private final AlertRepository alerts;
    private final DeadlineStepStore steps;
    private final ResponsibleAuthorityPort authorities;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Duration escalateAfter;

    public DeadlineWatchService(
        AlertRepository alerts,
        DeadlineStepStore steps,
        ResponsibleAuthorityPort authorities,
        ApplicationEventPublisher events,
        PlatformTransactionManager transactions,
        @Value("${nabat.reports.escalate-after:P14D}") Duration escalateAfter
    ) {
        this.alerts = alerts;
        this.steps = steps;
        this.authorities = authorities;
        this.events = events;
        this.transaction = new TransactionTemplate(transactions);
        this.escalateAfter = escalateAfter;
    }

    @Override
    public Result watch() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        int reminded = takeAll(steps.dueForReminder(now, BATCH), Stage.REMINDER, now);
        int escalated = takeAll(steps.dueForEscalation(now.minus(escalateAfter), BATCH), Stage.ESCALATION, now);
        if (reminded + escalated > 0) {
            log.info("Deadline watch: {} reminded, {} escalated", reminded, escalated);
        }
        return new Result(reminded, escalated);
    }

    private int takeAll(List<UUID> ids, Stage stage, Instant now) {
        int taken = 0;
        for (UUID id : ids) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> take(id, stage, now)))) {
                    taken++;
                }
            } catch (RuntimeException e) {
                log.warn("Deadline step {} for report {} failed; the next run tries again", stage, id, e);
            }
        }
        return taken;
    }

    private boolean take(UUID id, Stage stage, Instant now) {
        Alert alert = alerts.findById(AlertId.of(id)).orElse(null);
        // Answered, closed or forwarded since the list was read: nothing is late any more.
        if (alert == null || !alert.isOverdue(now)) {
            return false;
        }
        if (!steps.take(id, alert.answerDueAt(), stage, now)) {
            return false;
        }
        String name = authorities.nameOf(alert.authorityCode()).orElse(alert.authorityCode());
        events.publishEvent(new AnswerOverdue(alert, stage, alert.answerDueAt(), name));
        return true;
    }
}
