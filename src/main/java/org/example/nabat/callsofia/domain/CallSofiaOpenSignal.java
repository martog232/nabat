package org.example.nabat.callsofia.domain;

import org.example.nabat.shared.domain.Location;

import java.time.Instant;
import java.util.Objects;

/**
 * A report as Call Sofia's public map shows it: filed in the last 30 days and still in
 * processing.
 *
 * <p>Not a {@link CallSofiaSignal}. The map has no district and no history, and its
 * categories are the new site's, which do not match the archive's codes.
 *
 * @param registryNumber the municipality's registry number, as printed —
 *                       {@code СОА26-КЦ01-100828/06.09.2026}, or with a „Към“ prefix for a
 *                       follow-up to an earlier report
 * @param location       null if the map gave no usable point
 */
public record CallSofiaOpenSignal(
    long id,
    String registryNumber,
    int categoryId,
    String categoryName,
    CallSofiaStatus status,
    String title,
    Instant submittedAt,
    Location location
) {
    public CallSofiaOpenSignal {
        Objects.requireNonNull(categoryName, "categoryName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }
}
