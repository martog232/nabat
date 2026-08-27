package org.example.nabat.voting.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The parts of a vote event this service consumes: which alert, and what its counts became.
 *
 * <p>One type for both {@code vote.cast} and {@code vote.removed}. They are different events
 * — one names a vote and a voter, the other does not — but the fields that matter here are
 * the same two, and a projection that writes absolute tallies does not care which of them
 * produced the number.
 *
 * <p>Unknown fields are ignored deliberately rather than by default. nabat-voting owns this
 * schema and may add to it; a consumer that fails on a field it was not told about turns
 * every additive producer change into an outage here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VoteTalliesMessage(String alertId, Tallies tallies) {

    /**
     * Absolute counts, not deltas — which is what makes applying this event idempotent under
     * at-least-once delivery.
     *
     * <p>{@code credibilityScore} is carried rather than recomputed here: nabat-voting owns
     * that formula, and a second copy of it in this repository would be free to drift from
     * the first.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tallies(int upvotes, int downvotes, int confirmations, int credibilityScore) {
    }
}
