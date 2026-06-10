package org.example.nabat.adapter.out.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.nabat.adapter.in.websocket.AlertWebSocketHandler;
import org.example.nabat.adapter.out.notification.RedisWsPublisher.RedisWsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RedisWsSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisWsSubscriber.class);
    private static final ChannelTopic TOPIC = new ChannelTopic(RedisWsPublisher.CHANNEL);

    private final RedisMessageListenerContainer listenerContainer;
    private final AlertWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    public RedisWsSubscriber(
        RedisMessageListenerContainer listenerContainer,
        StringRedisTemplate redisTemplate,
        AlertWebSocketHandler webSocketHandler,
        ObjectMapper objectMapper
    ) {
        this.listenerContainer = listenerContainer;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void subscribe() {
        listenerContainer.addMessageListener(this, TOPIC);
        log.info("Subscribed to Redis channel: {}", RedisWsPublisher.CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        try {
            RedisWsMessage msg = objectMapper.readValue(body, RedisWsMessage.class);
            UUID userId = UUID.fromString(msg.userId());
            webSocketHandler.deliverLocally(userId, msg.type(), msg.alert());
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize Redis WS message: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId in Redis WS message: {}", e.getMessage());
        }
    }
}
