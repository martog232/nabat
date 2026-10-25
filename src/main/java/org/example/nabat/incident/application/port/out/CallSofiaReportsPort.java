package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.OpenCallSofiaReport;
import org.example.nabat.shared.domain.Location;

import java.util.Collection;
import java.util.List;

/**
 * Reports open in Call Sofia right now, for telling someone their problem may already be
 * filed there. Declared here, implemented by {@code callsofia}, which reads Call Sofia's map.
 *
 * <p>Empty when the map is not being read ({@code nabat.callsofia.live.enabled} off): no
 * reading means nothing known to be open, not an error.
 */
public interface CallSofiaReportsPort {

    /**
     * Open Call Sofia reports within {@code radiusMeters} of {@code center} under any of
     * {@code categoryCodes}, nearest first.
     */
    List<OpenCallSofiaReport> findOpenNear(Location center, double radiusMeters, Collection<Integer> categoryCodes, int limit);
}
