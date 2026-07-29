package org.example.nabat.adapter.out.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.websocket.WsClusterRelay;
import org.example.nabat.adapter.in.websocket.WsFrame;
import org.example.nabat.config.InstanceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Relays WebSocket frames to peer instances over a Redis pub/sub channel.
 */
@Component
public class RedisWsPublisher implements WsClusterRelay {

    private static final Logger log = LoggerFactory.getLogger(RedisWsPublisher.class);
    static final String CHANNEL = "ws:alerts";

    /** Sentinel recipient meaning "every connected user on every instance". */
    static final String BROADCAST_RECIPIENT = "*";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final InstanceId instanceId;

    public RedisWsPublisher(
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        InstanceId instanceId
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
    }

    @Override
    public void relayToUser(UUID userId, WsFrame frame) {
        publish(userId.toString(), frame);
    }

    @Override
    public void relayBroadcast(WsFrame frame) {
        publish(BROADCAST_RECIPIENT, frame);
    }

    private void publish(String recipient, WsFrame frame) {
        try {
            String message = objectMapper.writeValueAsString(
                new RedisWsMessage(recipient, frame.withOrigin(instanceId.value()))
            );
            redisTemplate.convertAndSend(CHANNEL, message);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize {} frame for relay: {}", frame.type(), e.getMessage());
        } catch (RuntimeException e) {
            // A Redis outage must not fail the HTTP request that triggered the push —
            // local delivery has already happened and the client can refetch.
            log.error("Failed to relay {} frame over Redis: {}", frame.type(), e.getMessage());
        }
    }

    /**
     * @param recipient a user id, or {@link #BROADCAST_RECIPIENT} for everyone
     */
    public record RedisWsMessage(String recipient, WsFrame frame) {
    }
}
