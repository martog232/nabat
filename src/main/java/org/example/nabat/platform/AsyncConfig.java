package org.example.nabat.platform;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async}, which {@code @ApplicationModuleListener} is built on.
 *
 * <p>Without this the annotation still compiles and still fires after commit, but on the
 * caller's thread — quietly losing the property the fan-out listener was moved off the
 * request path to get.
 *
 * <p>No executor is declared: Spring Boot's auto-configured {@code applicationTaskExecutor}
 * is a bounded {@code ThreadPoolTaskExecutor}, which is what we want. Omitting
 * {@code @EnableAsync} entirely would be worse than declaring one badly, but declaring one
 * here would override a sensible default with a worse guess.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
