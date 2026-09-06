package org.example.nabat.incident.config;

import org.example.nabat.incident.domain.Alert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * Serialization for the {@code nearbyAlerts} cache, owned by the module that owns the data.
 *
 * <p>It lives here rather than beside the rest of the Redis setup in {@code shared} for a
 * boundary reason and a correctness one. The boundary: {@code shared} depends on nothing, so
 * it cannot name {@link Alert}. The correctness: this cache holds exactly one type, and
 * saying so is what makes it work at all.
 *
 * <h2>Why the type is bound instead of inferred</h2>
 *
 * <p>The generic serializer writes the runtime class into the JSON and reads it back out, so
 * a cache can hold anything. That is how this was configured until Boot 4, with Jackson 2's
 * {@code DefaultTyping.EVERYTHING}. Jackson 3 removed {@code EVERYTHING}, and every remaining
 * option — {@code NON_FINAL}, {@code NON_FINAL_AND_RECORDS} — deliberately does not type
 * final JDK classes. The cached value is {@code List.of(...)}, whose implementation
 * ({@code ImmutableCollections$ListN}) is exactly that: final, and in {@code java.util}.
 *
 * <p>So the list went into Redis as a bare JSON array while its elements each carried
 * {@code @class}, and the read — which asks for {@code Object} and therefore expects a type
 * id at the root — failed with "Unexpected token (START_OBJECT), expected VALUE_STRING". The
 * property-based scheme Spring Data Redis 4 uses cannot fix that either: {@code @class} is a
 * field, and a JSON array has no fields. A top-level collection has nowhere to put its type.
 *
 * <p>Binding the type sidesteps all of it. Nothing polymorphic is written, so nothing
 * polymorphic is read: the payload carries no class names at all, which is smaller and
 * removes the deserialization-gadget surface the previous configuration needed an allow-list
 * validator to contain. The cost is that this cache may only hold {@code List<Alert>} — which
 * is all it has ever held, and now the configuration says so.
 */
@Configuration
class NearbyAlertsCacheConfig {

    static final String CACHE_NAME = "nearbyAlerts";

    /**
     * @param ttlSeconds how long a nearby result may be reused. Short on purpose: this is a
     *                   safety map, and the cache exists to absorb a burst of identical
     *                   queries rather than to keep answers around.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer nearbyAlertsCacheCustomizer(
        @Value("${nabat.cache.nearby-ttl:15}") int ttlSeconds
    ) {
        JavaType listOfAlert = JsonMapper.builder().build()
            .getTypeFactory()
            .constructCollectionType(List.class, Alert.class);

        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(ttlSeconds))
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new JacksonJsonRedisSerializer<List<Alert>>(listOfAlert)));

        return builder -> builder.withCacheConfiguration(CACHE_NAME, configuration);
    }
}
