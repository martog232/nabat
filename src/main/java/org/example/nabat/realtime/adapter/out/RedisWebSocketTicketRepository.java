package org.example.nabat.realtime.adapter.out;

import org.example.nabat.realtime.application.port.out.WebSocketTicketRepository;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.realtime.domain.WebSocketTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed store for single-use WebSocket handshake tickets.
 *
 * <p>Replaces {@code InMemoryWebSocketTicketRepository}, which could not work in the
 * deployed topology: nabat-app runs two replicas, so a ticket issued by
 * {@code POST /api/v1/ws/tickets} on one pod could not be redeemed by the WebSocket
 * upgrade if the load balancer sent it to the other — roughly half of all connection
 * attempts. That map also never evicted unredeemed tickets, so it grew without bound.
 *
 * <p>Redis gives both properties for free: shared across instances, and expiring keys
 * so an unused ticket disappears when it goes stale.
 */
@Component
public class RedisWebSocketTicketRepository implements WebSocketTicketRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisWebSocketTicketRepository.class);
    private static final String KEY_PREFIX = "ws:ticket:";

    /** Small grace period so Redis expiry never beats our own expiry check. */
    private static final Duration EXPIRY_GRACE = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;

    public RedisWebSocketTicketRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public WebSocketTicket save(WebSocketTicket ticket) {
        Duration ttl = Duration.between(Instant.now(), ticket.expiresAt()).plus(EXPIRY_GRACE);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Refusing to store an already-expired WebSocket ticket");
        }

        // Value is the owning user; the key carries the secret. Storing only the user id
        // keeps the payload minimal and means a Redis dump reveals nothing but pairings
        // that expire within minutes.
        redisTemplate.opsForValue().set(
            KEY_PREFIX + ticket.value(),
            ticket.userId().value().toString(),
            ttl
        );
        return ticket;
    }

    /**
     * Atomically reads and deletes the ticket, so a concurrent second redemption of the
     * same value cannot also succeed.
     */
    @Override
    public Optional<WebSocketTicket> consume(String ticketValue) {
        String userId = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticketValue);
        if (userId == null) {
            return Optional.empty();
        }

        try {
            // Reconstructed with an expiry just ahead of now: Redis has already enforced
            // the real TTL by deleting the key, so its presence is the freshness proof.
            return Optional.of(new WebSocketTicket(
                ticketValue,
                UserId.of(UUID.fromString(userId)),
                Instant.now().plusSeconds(1)
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Discarding WebSocket ticket with an unreadable user id");
            return Optional.empty();
        }
    }
}
