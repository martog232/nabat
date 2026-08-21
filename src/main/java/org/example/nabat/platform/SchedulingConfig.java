package org.example.nabat.platform;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled}.
 *
 * <p>Separate from {@link AsyncConfig} because the two answer different questions and one
 * can be wanted without the other: async is what moves the alert fan-out off the request
 * thread, scheduling is what runs periodic housekeeping.
 *
 * <p>Note that the scheduler is a <em>single</em> thread by default. That is fine while the
 * only scheduled job is the orphaned-photo sweep, but a second long-running job would
 * delay the first, so anything added here should be quick or should hand off to the task
 * executor.
 *
 * <p>Enabling scheduling is not the same as enabling any job: {@code OrphanedPhotoSweeper}
 * is behind {@code nabat.storage.orphan-sweep.enabled} and off unless a deployment asks
 * for it.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
