package org.example.nabat.adapter.out.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.websocket.AlertResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RedisWsPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisWsPublisher.class);
    static final String CHANNEL = "ws:alerts";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisWsPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(UUID userId, String type, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(
                new RedisWsMessage(userId.toString(), type, payload)
            );
            redisTemplate.convertAndSend(CHANNEL, message);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize Redis WS message for user {}: {}", userId, e.getMessage());
        }
    }

    public record RedisWsMessage(String userId, String type, Object alert) {}
}
