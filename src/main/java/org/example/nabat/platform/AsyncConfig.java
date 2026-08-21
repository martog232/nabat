package org.example.nabat.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
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

    /**
     * Carries the caller's context — tracing span and MDC — onto the pool thread.
     *
     * <p>Tracing context and MDC are thread-locals, so an {@code @Async} hop loses both by
     * default. Spring Boot does not register a decorator on its own; it only applies one
     * if a {@link TaskDecorator} bean exists, and then all of
     * {@code applicationTaskExecutor}'s work inherits it.
     *
     * <p>Concretely, without this bean {@code NewAlertFanout} runs with
     * {@code tracer.currentSpan() == null} and an empty MDC, so every line it logs prints
     * an empty {@code traceId} under {@code logging.pattern.console} and its spans are
     * orphaned from the request that caused them. The fan-out is the least sequential part
     * of a request and therefore the part that most needs to be followable. Verified by
     * {@code FanoutTracePropagationIntegrationTest}, which fails without it.
     *
     * <p>Requires {@code io.micrometer:context-propagation} on the classpath — it arrives
     * transitively with {@code micrometer-tracing}.
     *
     * <p>What actually travels is an <strong>Observation</strong>. Only
     * {@code ObservationThreadLocalAccessor} is registered through the ServiceLoader;
     * {@code micrometer-tracing} ships an {@code ObservationAwareSpanThreadLocalAccessor}
     * but registers none, so a bare {@code tracer.withSpan(...)} opened outside an
     * Observation propagates nowhere even with this decorator in place. Spring MVC wraps
     * every request in an Observation, so the production path is covered — but code that
     * opens a raw span and then hands work to the executor is not.
     *
     * <p>This covers the in-process hop only. A publication replayed at startup by the
     * Event Publication Registry has no caller to inherit from and correctly starts its
     * own trace.
     */
    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
