package org.example.nabat.media.application.port.out;

import java.util.Set;

/**
 * Answers which stored photos something still points at.
 *
 * <p>Declared here and implemented in {@code incident}, following the same direction as
 * {@code AlertAudiencePort}: the module that needs the answer owns the question, and the
 * module holding the data implements it. {@code media} must not query the alerts table
 * itself, and {@code incident} does not depend on {@code media} today — so the port keeps
 * the dependency pointing one way.
 *
 * <p>It exists as a port rather than a direct call for a second reason: when photos move
 * to S3 and {@code media} becomes its own service, this is the seam that turns into an API
 * call or an event subscription, and nothing else has to change.
 */
public interface PhotoReferencePort {

    /**
     * Filters {@code filenames} down to those still referenced.
     *
     * <p>Batched rather than one call per file: a sweep asks about a few hundred names and
     * the implementation answers with one query.
     *
     * <p><strong>Implementations must throw rather than return an empty set when they
     * cannot answer.</strong> The caller deletes everything not in the returned set, so a
     * failure quietly reported as "nothing is referenced" would delete every photo on the
     * volume. Failing loudly leaves the files alone.
     *
     * @param filenames storage names, e.g. {@code 3f2a....jpg} — not URLs
     * @return the subset that is still referenced; empty means genuinely none of them are
     */
    Set<String> referencedAmong(Set<String> filenames);
}
