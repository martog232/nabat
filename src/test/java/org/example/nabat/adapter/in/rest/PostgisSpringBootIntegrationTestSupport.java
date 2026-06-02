package org.example.nabat.adapter.in.rest;

import org.example.nabat.PostgresTestSupport;

/**
 * @deprecated Extend {@link PostgresTestSupport} directly.
 *             Kept for backward compatibility; delegates everything to the canonical base.
 */
@Deprecated(since = "T-43", forRemoval = true)
public abstract class PostgisSpringBootIntegrationTestSupport extends PostgresTestSupport {
    // All container setup is inherited from PostgresTestSupport.
}
