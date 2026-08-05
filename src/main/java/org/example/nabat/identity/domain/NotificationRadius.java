package org.example.nabat.identity.domain;

import java.util.Set;

/**
 * The notification radii a user may choose, in kilometres.
 *
 * <p>Single source of truth for the allow-list, which must stay in step with the
 * {@code CHECK (notification_radius_km IN (...))} constraint added by
 * {@code V6__user_location_and_radius.sql}.
 *
 * <p>Distinct from {@link AlertSeverity}'s broadcast radii, which control how far a
 * <em>new alert</em> is fanned out based on how serious it is. The two were easy to
 * confuse: the severity radii include 2 km, which is not a legal user preference,
 * and {@code CreateAlertService} used the severity radius for subscriber fan-out
 * while ignoring each user's configured preference entirely.
 */
public final class NotificationRadius {

    /** Must mirror the database CHECK constraint. */
    public static final Set<Integer> SUPPORTED_KM = Set.of(1, 5, 10, 25, 50);

    private NotificationRadius() {
    }

    public static boolean isSupported(int radiusKm) {
        return SUPPORTED_KM.contains(radiusKm);
    }

    public static void requireSupported(int radiusKm) {
        if (!isSupported(radiusKm)) {
            throw new IllegalArgumentException(
                "Unsupported notification radius: " + radiusKm + " km. Supported values: " + SUPPORTED_KM);
        }
    }
}
