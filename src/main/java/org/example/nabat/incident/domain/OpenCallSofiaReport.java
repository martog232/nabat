package org.example.nabat.incident.domain;

import org.example.nabat.shared.domain.Location;

import java.time.Instant;

/**
 * A report open in Call Sofia, as nabat knows it from Call Sofia's map — offered when
 * someone may be about to report the same problem again.
 *
 * @param id           Call Sofia's own id; its page is {@code call.sofia.bg/signals/{id}}
 * @param categoryCode the same code space as nabat's categories, which are Call Sofia's
 */
public record OpenCallSofiaReport(long id, String registryNumber, String title, int categoryCode,
                                  Instant submittedAt, Location location) {
}