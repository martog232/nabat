package org.example.nabat.voting.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The parts of a vote event this service consumes: which alert, and what its counts became.
 *
 * <p>Two fields out of seven. nabat-voting's {@code vote.changed} messages also carry the
 * change type, the vote, the voter and when it happened; a projection that writes absolute
 * tallies needs none of it, and every field named here is one this service would have to keep
 * in step with a schema it does not own.
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
