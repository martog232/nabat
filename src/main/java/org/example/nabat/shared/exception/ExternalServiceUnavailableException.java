package org.example.nabat.shared.exception;

/**
 * A downstream service this use case depends on could not be reached, or answered
 * in a way that indicates a problem on our side of the integration rather than a
 * problem with the user's request.
 *
 * <p>Maps to {@code 503 Service Unavailable}. This exists so that a voting-service
 * outage, an expired service credential, or a 500 from the voting service stops
 * being reported to the browser as {@code 409 Conflict} — which is what happened
 * when every failure mode was collapsed into {@code IllegalStateException}. The
 * frontend silently swallows 409s (treating them as "you already voted"), so
 * genuine outages were invisible to both the user and the operator.
 */
public class ExternalServiceUnavailableException extends RuntimeException {

    public ExternalServiceUnavailableException(String message) {
        super(message);
    }

    public ExternalServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
