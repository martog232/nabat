package org.example.nabat.adapter.in.websocket;

import java.util.UUID;

/**
 * Relays WebSocket frames to the other application instances.
 *
 * <p>Declared here, alongside its only caller, and implemented by
 * {@code adapter.out.notification.RedisWsPublisher}. Previously
 * {@code AlertWebSocketHandler} depended on that class directly — an inbound
 * adapter reaching into an outbound one — and the outbound subscriber depended
 * back on the handler, so the two adapters were mutually coupled with no interface
 * between them.
 */
public interface WsClusterRelay {

    /** Deliver to one user, wherever they are connected. */
    void relayToUser(UUID userId, WsFrame frame);

    /** Deliver to every connected user on every other instance. */
    void relayBroadcast(WsFrame frame);
}
