package org.example.nabat.notification.adapter.out.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.notification.adapter.in.rest.NotificationResponse;
import org.example.nabat.notification.application.port.out.NotificationSender;
import org.example.nabat.notification.domain.Notification;
import org.example.nabat.realtime.spi.WsBroadcaster;
import org.example.nabat.realtime.spi.WsFrame;
import org.springframework.stereotype.Component;

/**
 * Pushes notifications via WebSocket to online users.
 * Falls back to logging when the user is offline (notification is still persisted by the caller).
 *
 * <p>The frame carries {@link NotificationResponse}, the same DTO the REST endpoints
 * return. Serialising the domain record directly instead produced
 * {@code {"id":{"value":"…"},"isRead":false}} over the socket against
 * {@code {"id":"…","read":false}} over REST, for the same logical object.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationSender implements NotificationSender {

    private final WsBroadcaster broadcaster;

    @Override
    public void sendToUser(UserId userId, Notification notification) {
        WsFrame frame = WsFrame.notification(NotificationResponse.from(notification));
        boolean delivered = broadcaster.sendToUser(userId.value(), frame);
        if (!delivered) {
            log.info("User {} offline; notification {} persisted but not pushed",
                    userId.value(), notification.id().value());
        }
    }
}
