package org.example.nabat.identity.domain;

/**
 * An account already exists for the submitted email address.
 *
 * <p>Maps to {@code 409 Conflict}.
 *
 * <p>Note that responding differently to a taken and an untaken address is
 * inherently enumerable. Hiding it behind a fake success — which is what the
 * previous code attempted, by throwing {@code IllegalArgumentException} with the
 * message "Registration submitted. Please verify your email." — did not fix that,
 * because the status code still differed (400 vs 201). Properly closing the hole
 * requires always returning the same response and delivering the outcome by email
 * instead; that is a product decision, so for now the honest conflict is reported
 * and the tradeoff is stated here.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with that email already exists");
    }
}
