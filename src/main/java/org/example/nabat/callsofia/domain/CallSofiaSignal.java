package org.example.nabat.callsofia.domain;

import org.example.nabat.shared.domain.Location;

import java.time.Instant;
import java.util.Objects;

/**
 * A report filed in Call Sofia, already translated out of the archive's format.
 *
 * @param id           Call Sofia's own id
 * @param categoryCode Call Sofia's category, see {@link CallSofiaCategory}
 * @param districtCode a code from {@code districts}, or null where Call Sofia recorded none
 * @param location     null where the reporter gave no point
 */
public record CallSofiaSignal(
    long id,
    int categoryCode,
    String districtCode,
    Instant submittedAt,
    Location location,
    String title
) {
    public CallSofiaSignal {
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(title, "title");
    }
}
