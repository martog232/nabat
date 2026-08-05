package org.example.nabat.realtime.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.nabat.realtime.spi.LocalWsDelivery;
import org.example.nabat.realtime.spi.WsFrame;
import org.example.nabat.realtime.adapter.out.RedisWsPublisher.RedisWsMessage;
import org.example.nabat.shared.config.InstanceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Delivers frames relayed by peer instances to locally-connected sessions.
 */
@Component
public class RedisWsSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisWsSubscriber.class);
    private static final ChannelTopic TOPIC = new ChannelTopic(RedisWsPublisher.CHANNEL);

    private final RedisMessageListenerContainer listenerContainer;
    private final LocalWsDelivery localDelivery;
    private final ObjectMapper objectMapper;
    private final InstanceId instanceId;

    public RedisWsSubscriber(
        RedisMessageListenerContainer listenerContainer,
        LocalWsDelivery localDelivery,
        ObjectMapper objectMapper,
        InstanceId instanceId
    ) {
        this.listenerContainer = listenerContainer;
        this.localDelivery = localDelivery;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
    }

    @PostConstruct
    void subscribe() {
        listenerContainer.addMessageListener(this, TOPIC);
        log.info("Subscribed to Redis channel: {}", RedisWsPublisher.CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // Explicit UTF-8: the default charset differs between a Windows dev machine and
        // a Linux container, so relying on it made the wire format environment-dependent.
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            RedisWsMessage msg = objectMapper.readValue(body, RedisWsMessage.class);
            WsFrame frame = msg.frame();

            if (frame == null) {
                log.warn("Ignoring relayed message with no frame");
                return;
            }

            // Redis echoes published messages back to the publisher. Delivering our own
            // broadcast again would show every client each ALERT_UPDATED twice.
            if (instanceId.value().equals(frame.origin())) {
                return;
            }

            if (RedisWsPublisher.BROADCAST_RECIPIENT.equals(msg.recipient())) {
                localDelivery.deliverToAll(frame);
                return;
            }

            localDelivery.deliverLocally(UUID.fromString(msg.recipient()), frame);
        } catch (IllegalArgumentException e) {
            // Covers both an unparseable recipient and Jackson's own argument errors.
            log.warn("Ignoring malformed relayed WS message: {}", e.getMessage());
        } catch (Exception e) {
            // A single bad message must not kill the listener thread.
            log.warn("Failed to handle relayed WS message: {}", e.getMessage());
        }
    }
}
