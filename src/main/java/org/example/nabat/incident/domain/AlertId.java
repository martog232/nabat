package org.example.nabat.incident.domain;

import java.util.UUID;

public record AlertId(UUID value) {
    public static AlertId generate() {
        return new AlertId(UUID.randomUUID());
    }

    public static AlertId of(UUID value) {
        return new AlertId(value);
    }
}
