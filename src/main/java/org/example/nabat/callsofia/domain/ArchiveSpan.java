package org.example.nabat.callsofia.domain;

import java.time.Instant;

/** The first and last filing time in the imported archive. */
public record ArchiveSpan(Instant firstSubmitted, Instant lastSubmitted) {
}
