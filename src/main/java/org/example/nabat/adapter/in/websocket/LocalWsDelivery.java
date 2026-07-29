package org.example.nabat.adapter.in.websocket;

import java.util.UUID;

/**
 * Delivery to sessions held by <em>this</em> instance.
 *
 * <p>Implemented by {@link AlertWebSocketHandler} and consumed by the Redis
 * subscriber, so the subscriber depends on this narrow interface rather than on the
 * handler class.
 */
public interface LocalWsDelivery {

    /**
     * @return {@code true} if the user had at least one open session here and the
     *         frame was written to it
     */
    boolean deliverLocally(UUID userId, WsFrame frame);

    /** Writes to every open session on this instance. */
    void deliverToAll(WsFrame frame);
}
