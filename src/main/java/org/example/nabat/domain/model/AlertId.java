package org.example.nabat.domain.model;

import java.util.UUID;

public record AlertId(UUID value) {
    public static AlertId generate() {
        return new AlertId(UUID.randomUUID());
    }

    public static AlertId of(UUID value) {
        return new AlertId(value);
    }

    public static AlertId of(String value) {
        return new AlertId(UUID.fromString(value));
    }
}
