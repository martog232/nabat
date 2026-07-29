package org.example.nabat.adapter.out.token;

import org.example.nabat.application.port.out.RefreshTokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed single-use record for refresh tokens.
 *
 * <p>Redis rather than an in-memory map because nabat-app runs with more than one
 * replica: an in-memory set would let a replayed token succeed simply by landing on
 * a different pod.
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRefreshTokenStore.class);
    private static final String KEY_PREFIX = "auth:refresh:consumed:";

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean consume(String tokenId, Duration retainFor) {
        // SET key value NX EX ttl — atomic claim; succeeds only for the first caller.
        Boolean firstUse = redisTemplate.opsForValue()
            .setIfAbsent(KEY_PREFIX + tokenId, "1", retainFor);

        if (firstUse == null) {
            // No answer from Redis. Fail closed: refusing a refresh costs the user one
            // re-login, whereas allowing it would silently disable replay detection
            // exactly when the store is unavailable.
            log.error("Redis did not answer refresh-token claim for {}; refusing the exchange", tokenId);
            return false;
        }
        return firstUse;
    }
}
