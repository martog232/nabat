package org.example.nabat.callsofia.adapter.out.incident;

import org.example.nabat.incident.application.port.out.CallSofiaReportsPort;
import org.example.nabat.incident.domain.OpenCallSofiaReport;
import org.example.nabat.shared.domain.Location;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Answers {@code incident}'s question — is this already filed in Call Sofia? — from the
 * reports this module last saw on Call Sofia's map.
 *
 * <p>Only reports still on the map: one that has left it is answered, closed or older than
 * 30 days, and sending someone to it as "already reported" would be wrong for the first two
 * and unknowable for the third. The category codes are the same on both sides — nabat took
 * Call Sofia's — so they are compared directly.
 */
@Component
public class CallSofiaReportsAdapter implements CallSofiaReportsPort {

    private static final String NEAR = """
        SELECT id, registry_number, title, category_id, submitted_at,
               ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon
        FROM call_sofia_open_signals
        WHERE left_map_at IS NULL
          AND category_id IN (:categories)
          AND location IS NOT NULL
          AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :meters)
        ORDER BY ST_Distance(location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography)
        LIMIT :limit
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public CallSofiaReportsAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OpenCallSofiaReport> findOpenNear(Location center, double radiusMeters, Collection<Integer> categoryCodes, int limit) {
        if (categoryCodes.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("categories", categoryCodes)
            .addValue("lat", center.latitude())
            .addValue("lon", center.longitude())
            .addValue("meters", radiusMeters)
            .addValue("limit", limit);
        return jdbc.query(NEAR, params, (rs, row) -> new OpenCallSofiaReport(
            rs.getLong("id"),
            rs.getString("registry_number"),
            rs.getString("title"),
            rs.getInt("category_id"),
            rs.getTimestamp("submitted_at").toInstant(),
            Location.of(rs.getDouble("lat"), rs.getDouble("lon"))));
    }
}
