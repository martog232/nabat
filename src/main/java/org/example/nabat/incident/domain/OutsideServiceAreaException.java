package org.example.nabat.incident.domain;

import org.example.nabat.shared.domain.Location;

/**
 * A report placed where nabat has nobody to send it to.
 *
 * <p>Not an invalid request: the coordinates are fine, and so is everything else about it.
 * nabat covers Sofia, and this is somewhere else — which is why it maps to 422 rather than
 * 400, and carries its own error code so a client can say so instead of "invalid request".
 */
public class OutsideServiceAreaException extends RuntimeException {

    public OutsideServiceAreaException(Location location) {
        super("No district covers " + location.latitude() + ", " + location.longitude());
    }
}
