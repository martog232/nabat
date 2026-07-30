package org.example.nabat.identity.application.port.out;

/**
 * Records login outcomes so that credential-stuffing and brute-force attempts are
 * visible in logs and metrics.
 *
 * <p>This is <strong>observability only</strong> — it does not block. Request
 * throttling is enforced at the Kong gateway layer, which owns rate limiting for
 * the platform.
 *
 * <p>Exists as a port so that {@code AuthenticationService} no longer imports
 * {@code adapter.in.security.LoginAttemptTracker} directly; an application-layer
 * class depending on an inbound adapter inverted the dependency rule the rest of
 * the codebase follows, and made the service impossible to unit test without
 * dragging in the tracker.
 */
public interface LoginAttemptPort {

    void recordFailure(String email, String clientIp);

    /** Clears the failure history for this identity after a successful login. */
    void recordSuccess(String email, String clientIp);
}
