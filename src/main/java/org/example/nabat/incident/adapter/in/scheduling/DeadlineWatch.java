package org.example.nabat.incident.adapter.in.scheduling;

import org.example.nabat.incident.application.port.in.WatchDeadlinesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clock that drives {@link WatchDeadlinesUseCase}. A driving adapter and nothing more.
 *
 * <p>Off by default, like the other jobs that mail or delete: sending reminders to authorities
 * is a decision per environment. On in compose and the Helm chart. Safe on several replicas at
 * once — each step is taken by exactly one of them (see {@code DeadlineStepStore.take}).
 */
@Component
@ConditionalOnProperty(name = "nabat.reports.deadline-watch.enabled", havingValue = "true")
public class DeadlineWatch {

    private static final Logger log = LoggerFactory.getLogger(DeadlineWatch.class);

    private final WatchDeadlinesUseCase watchDeadlines;

    public DeadlineWatch(WatchDeadlinesUseCase watchDeadlines) {
        this.watchDeadlines = watchDeadlines;
    }

    /** {@code fixedDelay}: a slow pass cannot have another queued behind it. */
    @Scheduled(
        fixedDelayString = "${nabat.reports.deadline-watch.interval:PT1H}",
        initialDelayString = "${nabat.reports.deadline-watch.initial-delay:PT2M}"
    )
    public void watch() {
        try {
            watchDeadlines.watch();
        } catch (RuntimeException e) {
            log.warn("Deadline watch failed; the next run tries again", e);
        }
    }
}
