package org.example.nabat.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;

/**
 * @deprecated Extend {@link PostgresTestSupport} directly.
 *             Kept for backward compatibility; delegates everything to the canonical base.
 */
@Deprecated(since = "T-43", forRemoval = true)
abstract class PostgisIntegrationTestSupport extends PostgresTestSupport {
    // All container setup is inherited from PostgresTestSupport.
}
