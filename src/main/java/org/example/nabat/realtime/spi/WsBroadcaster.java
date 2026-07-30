package org.example.nabat.realtime.spi;

import java.util.UUID;

/**
 * The realtime module's outward-facing delivery API: hand it a {@link WsFrame} and it
 * reaches the recipient's sessions on this instance or, failing that, on a peer.
 *
 * <p>Deliberately frame-shaped rather than domain-shaped. The handler used to offer
 * {@code sendAlertToUser(UUID, Alert)} and {@code sendNotificationToUser(UUID,
 * Notification)}, converting to the matching REST DTO itself — which made realtime
 * depend on the incident and notification modules, while those modules depended on
 * realtime to do the pushing. Spring Modulith rejected the resulting cycle.
 *
 * <p>Now each module builds its own frame from its own DTO and realtime only routes
 * and serialises it. {@link WsFrame}'s payload stays {@code Object} for exactly this
 * reason: the transport does not need to know what it is carrying.
 */
public interface WsBroadcaster {

    /**
     * Delivers to {@code userId}'s sessions on this instance, relaying to peers when
     * the user has none open here.
     *
     * @return {@code true} if the frame was written to a local session
     */
    boolean sendToUser(UUID userId, WsFrame frame);

    /** Delivers to every session on this instance and relays to peers exactly once. */
    void broadcast(WsFrame frame);
}
