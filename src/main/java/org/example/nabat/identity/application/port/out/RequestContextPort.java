package org.example.nabat.identity.application.port.out;

import java.util.Optional;

/**
 * Access to per-request transport facts that the application layer legitimately
 * needs but must not reach into the servlet API for.
 *
 * <p>This exists because {@code AuthenticationService} previously imported
 * {@code adapter.in.security.RequestContextHelper} and pulled the client IP out of
 * Spring's {@code RequestContextHolder} directly — an application-layer class
 * depending on an inbound adapter, and on the servlet stack, which made it
 * untestable without a mock request and inverted the dependency rule the rest of
 * the codebase follows.
 *
 * <p>Implementations must degrade gracefully when there is no active request
 * (scheduled jobs, tests, message consumers) rather than throwing.
 */
public interface RequestContextPort {

    /** Placeholder used when no request is in scope or the address cannot be determined. */
    String UNKNOWN_IP = "UNKNOWN";

    /**
     * Best-effort client IP, honouring {@code X-Forwarded-For} / {@code X-Real-IP}.
     *
     * <p>Never {@code null}; returns {@link #UNKNOWN_IP} when unavailable.
     *
     * <p>Note that these headers are client-controlled unless a trusted proxy
     * overwrites them, so treat the value as a heuristic for logging and
     * throttling, never as an authorization input.
     */
    String clientIp();

    /**
     * The raw bearer token presented by the caller on the current request, if any.
     *
     * <p>Used to propagate the end user's identity to downstream services so they
     * can authorize as that user rather than as a shared service account.
     */
    Optional<String> callerAccessToken();
}
