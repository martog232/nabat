package org.example.nabat.voting.adapter.in.kafka;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts the vote-event listener once the broker is there, and keeps trying until it is.
 *
 * <p>The listener does not start with the application context. It used to, and that made an
 * absent broker fatal to the whole service: Kafka treats a bootstrap address that does not
 * resolve as a configuration error rather than a temporary outage, so constructing the
 * consumer threw, the listener container failed to start, and the context refresh died with
 * {@code Failed to start bean 'internalKafkaListenerEndpointRegistry'}. nabat-app then served
 * nothing at all — no alerts, no login, no WebSocket — because votes could not be counted.
 *
 * <p>That trade is the wrong way round. This service's own reason to exist is alerts; the vote
 * counts are one denormalised column on them, they arrive asynchronously by design, and they
 * are absolute values, so whatever is missed is corrected by the next message on that alert or
 * by replaying the topic. None of that is worth refusing to boot for.
 *
 * <p>So the container starts here instead, after the context is up, and a failure is a warning
 * and another attempt rather than an exit. The same loop covers the case that prompted it in
 * reverse: a broker deployed after this service, or restarted under it, is picked up without
 * anybody restarting anything.
 */
@Component
@ConditionalOnProperty(name = "nabat.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class VoteEventListenerStarter {

    private static final Logger log = LoggerFactory.getLogger(VoteEventListenerStarter.class);

    private final KafkaListenerEndpointRegistry registry;

    /**
     * @implNote {@code initialDelay} of zero, so a healthy broker means the listener is
     *     running by the time the first request arrives rather than up to a retry interval
     *     later.
     */
    @Scheduled(
        fixedDelayString = "${nabat.kafka.listener-retry-interval:PT30S}",
        initialDelayString = "PT0S"
    )
    void startListenersWhenTheBrokerAllows() {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            if (container.isRunning()) {
                continue;
            }

            try {
                container.start();
                log.info("Vote event listener started");
            } catch (RuntimeException e) {
                // Warn, not error: the counts on alerts are eventually consistent anyway, and
                // this is the state a stack whose broker is not up yet is expected to be in.
                log.warn("Vote event listener could not start ({}); retrying. Vote counts on "
                         + "alerts will not move until it does.", e.getMessage());
            }
        }
    }
}
