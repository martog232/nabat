package org.example.nabat.domain.exception;

/**
 * Credentials were missing, malformed, or did not match.
 *
 * <p>Maps to {@code 401 Unauthorized}.
 *
 * <p>Replaces the application layer's use of Spring Security's
 * {@code BadCredentialsException}, which {@code AGENTS.md} explicitly forbids
 * outside the security adapter — {@code AuthenticationService} and
 * {@code WebSocketTicketService} both imported it, coupling use cases to the
 * framework and making them awkward to unit test in isolation.
 *
 * <p>Deliberately carries no detail about <em>which</em> part failed: the handler
 * returns one message for every cause so that login cannot be used to enumerate
 * accounts.
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
