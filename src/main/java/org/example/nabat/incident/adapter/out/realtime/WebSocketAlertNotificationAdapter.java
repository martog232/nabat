package org.example.nabat.incident.adapter.out.realtime;

import org.example.nabat.incident.adapter.in.rest.AlertResponse;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.realtime.spi.WsBroadcaster;
import org.example.nabat.realtime.spi.WsFrame;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Pushes alert frames over the realtime transport.
 *
 * <p>This module owns the wire payload: the frame carries {@link AlertResponse}, the
 * same DTO {@code GET /api/v1/alerts} returns, so REST and WebSocket cannot drift.
 * Building it here rather than inside the WebSocket handler is what keeps realtime
 * independent of this module.
 */
@Component
public class WebSocketAlertNotificationAdapter implements AlertNotificationPort {

    private final WsBroadcaster broadcaster;

    public WebSocketAlertNotificationAdapter(WsBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void broadcastAlert(Alert alert, List<UUID> userIds) {
        // One frame for all recipients: it is immutable and carries no per-user state.
        WsFrame frame = WsFrame.alert(WsFrame.NEW_ALERT, AlertResponse.from(alert));
        userIds.forEach(userId -> broadcaster.sendToUser(userId, frame));
    }

    @Override
    public void broadcastAlertUpdate(Alert alert) {
        broadcaster.broadcast(WsFrame.alert(WsFrame.ALERT_UPDATED, AlertResponse.from(alert)));
    }
}
