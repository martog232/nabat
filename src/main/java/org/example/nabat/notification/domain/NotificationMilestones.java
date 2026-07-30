package org.example.nabat.notification.domain;

import java.util.Set;

/**
 * Confirmation thresholds at which we fire ALERT_MILESTONE notifications.
 *
 * <p>A policy of this module, so it lives in its domain rather than beside the service
 * that happens to consult it. It is public because the voting module calls
 * {@link #isMilestone(int)} directly — a dependency in the wrong direction: voting
 * should announce that a vote was cast and let this module decide whether the new
 * count is worth notifying anyone about. Once vote counting is event-driven the
 * decision moves behind {@code SendNotificationUseCase} and this type goes back to
 * being an internal detail. Until then the coupling is at least declared, rather than
 * hidden by same-package access.
 */
public final class NotificationMilestones {

    public static final Set<Integer> THRESHOLDS = Set.of(10, 25, 50, 100, 250, 500, 1000);

    public static boolean isMilestone(int count) {
        return THRESHOLDS.contains(count);
    }

    private NotificationMilestones() {}
}
