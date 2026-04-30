package org.example.nabat.application.service;

import java.util.Set;

/** Confirmation thresholds at which we fire ALERT_MILESTONE notifications. */
final class NotificationMilestones {
    static final Set<Integer> THRESHOLDS = Set.of(10, 25, 50, 100, 250, 500, 1000);

    static boolean isMilestone(int count) {
        return THRESHOLDS.contains(count);
    }

    private NotificationMilestones() {}
}

