package org.example.nabat.shared.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /**
     * The cache manager, and the defaults every cache starts from.
     *
     * <p>It configures no cache by name. `shared` depends on nothing, so it cannot name the
     * types the caches hold; each module configures its own through a
     * {@link RedisCacheManagerBuilderCustomizer}, which is the same hook Spring Boot's own
     * auto-configuration applies. `nearbyAlerts` is configured that way in `incident`.
     *
     * <p>The default below serialises values with the generic serializer, which writes each
     * value's class into the JSON so a cache can hold anything. Two things are worth knowing
     * before relying on it. It only works for values that serialise as a JSON <em>object</em>:
     * the type id is written as an {@code @class} field, and a top-level collection or array
     * has no field to put it in, so it round-trips untyped and fails on read. And an
     * allow-list validator is not optional — without one, anything able to write to Redis
     * chooses which classes this JVM constructs, which is the classic deserialization-gadget
     * primitive.
     *
     * <p>A cache whose values are a collection, or one that holds a single known type, is
     * better off binding that type instead: no class names on the wire and nothing
     * polymorphic to validate. See `NearbyAlertsCacheConfig` for the worked example and for
     * what Jackson 3 changed here.
     */
    @Bean
    CacheManager cacheManager(
        RedisConnectionFactory connectionFactory,
        ObjectProvider<RedisCacheManagerBuilderCustomizer> customizers
    ) {
        var typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("org.example.nabat.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .build();

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(GenericJacksonJsonRedisSerializer.builder()
                    .enableDefaultTyping(typeValidator)
                    .build()));

        RedisCacheManager.RedisCacheManagerBuilder builder =
            RedisCacheManager.builder(connectionFactory).cacheDefaults(defaults);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }
}
