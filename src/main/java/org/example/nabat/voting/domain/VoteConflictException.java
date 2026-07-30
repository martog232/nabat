package org.example.nabat.voting.domain;

/**
 * The requested vote conflicts with the voter's current vote — in practice, the
 * same vote type has already been cast on that alert.
 *
 * <p>Maps to {@code 409 Conflict}. Distinct from
 * {@code ExternalServiceUnavailableException} so that a real conflict and a broken
 * integration are no longer indistinguishable to the caller.
 */
public class VoteConflictException extends RuntimeException {

    public VoteConflictException(String message) {
        super(message);
    }
}
