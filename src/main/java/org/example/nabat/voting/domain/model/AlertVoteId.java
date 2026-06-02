package org.example.nabat.voting.domain.model;

import java.util.UUID;

public record AlertVoteId(UUID value) {
    public static AlertVoteId generate() {
        return new AlertVoteId(UUID.randomUUID());
    }

    public static AlertVoteId of(UUID value) {
        return new AlertVoteId(value);
    }
}
