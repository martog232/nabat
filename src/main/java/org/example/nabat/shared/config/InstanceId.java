package org.example.nabat.shared.config;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Identity of this running instance, used to recognise our own messages coming back
 * off a Redis pub/sub channel.
 *
 * <p>Redis delivers a published message to every subscriber on the channel,
 * including the publisher. Without an origin marker, an instance that broadcast a
 * frame locally and then published it for its peers also received its own copy and
 * broadcast it a second time — so every locally-connected client saw each
 * {@code ALERT_UPDATED} twice.
 *
 * <p>Random per process rather than derived from the hostname: pod names are not
 * guaranteed stable or unique across restarts, and correctness here only needs
 * uniqueness among live instances.
 */
@Component
public class InstanceId {

    private final String value = UUID.randomUUID().toString();

    public String value() {
        return value;
    }
}
