package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The nearby-alerts result, with enough context to know whether it is complete.
 *
 * <p>This endpoint used to return a bare JSON array with no upper bound: a 100 km radius
 * over a dense city returned every matching alert in one response. Capping it fixes that,
 * but a cap without a signal is worse than either — the client would draw a partial map
 * and have no way to tell. So the envelope exists to carry {@code truncated}.
 *
 * <p>Deliberately not Spring Data's {@code Page}. There are no pages here: nothing pages
 * through a map, and its {@code pageable}/{@code sort} internals would become part of a
 * public HTTP contract that then cannot change without breaking clients. A client that
 * sees {@code truncated} should narrow the radius or apply a filter, which is the
 * meaningful response on a map — not fetch page 2.
 */
@Schema(description = "Active alerts near a point, capped at `limit`")
public record NearbyAlertsResponse(

    @Schema(description = "Matching alerts, newest first")
    List<AlertResponse> alerts,

    @Schema(description = "Number of alerts in this response", example = "42")
    int count,

    @Schema(description = "The cap that was applied", example = "100")
    int limit,

    @Schema(
        description = "True when the cap was reached and older matches were dropped. "
                      + "Narrow the radius or filter by type/severity to see them.",
        example = "false"
    )
    boolean truncated
) {

    public static NearbyAlertsResponse of(List<AlertResponse> alerts, int limit) {
        // The query asks for exactly `limit` rows, so a full response is indistinguishable
        // from one that had more to give. Reporting "possibly truncated" on equality is the
        // honest reading; the alternative — asking for limit + 1 to know for certain —
        // costs an extra row on every request to remove an ambiguity the client resolves
        // the same way either way.
        return new NearbyAlertsResponse(alerts, alerts.size(), limit, alerts.size() >= limit);
    }
}
