package org.example.nabat.adapter.in.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope for everything pushed over the alerts WebSocket.
 *
 * <p>Replaces {@code AlertResponseWrapper}, whose payload field was named
 * {@code alert} even when it carried a notification — so a notification frame
 * arrived as {@code {"type":"NOTIFICATION","alert":{…}}} while the frontend's
 * {@code WsNotificationFrame} type declared a {@code notification} field.
 *
 * <p>Both fields are emitted so existing clients keep working during a rollout:
 * {@code alert} for the two alert frame types, {@code notification} for the
 * notification frame, whichever is relevant. Nulls are omitted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsFrame(
    String type,
    Object alert,
    Object notification,
    /**
     * Which instance emitted this. Set only on frames that travel through Redis, so a
     * subscriber can ignore the copy of its own broadcast that pub/sub echoes back —
     * without this, every locally-connected client received each broadcast twice.
     */
    String origin
) {
    public static final String NEW_ALERT = "NEW_ALERT";
    public static final String ALERT_UPDATED = "ALERT_UPDATED";
    public static final String NOTIFICATION = "NOTIFICATION";

    public static WsFrame alert(String type, Object payload) {
        return new WsFrame(type, payload, null, null);
    }

    public static WsFrame notification(Object payload) {
        return new WsFrame(NOTIFICATION, null, payload, null);
    }

    /** A copy stamped with the emitting instance, for relaying over Redis. */
    public WsFrame withOrigin(String instanceId) {
        return new WsFrame(type, alert, notification, instanceId);
    }

    /** The frame as delivered to clients — origin is an internal routing concern. */
    public WsFrame forClient() {
        return origin == null ? this : new WsFrame(type, alert, notification, null);
    }

    /** The payload for this frame, whichever field carries it. */
    public Object payload() {
        return alert != null ? alert : notification;
    }
}
