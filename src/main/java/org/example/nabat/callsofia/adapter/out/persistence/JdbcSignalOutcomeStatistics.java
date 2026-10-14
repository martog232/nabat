package org.example.nabat.callsofia.adapter.out.persistence;

import org.example.nabat.callsofia.application.port.out.SignalOutcomeStatistics;
import org.example.nabat.callsofia.domain.ArchiveSpan;
import org.example.nabat.callsofia.domain.DistrictOutcomes;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * One aggregate over {@code call_sofia_signal_outcomes}, the materialised view V26 keeps.
 *
 * <p>Medians and p90 come from {@code percentile_cont}, filtered to answered reports: a
 * report still open has no time-to-answer to put in the order, and counting it as zero
 * would make the slowest districts look fastest. They are reported next to the count still
 * open, which is the part those figures leave out.
 */
@Component
public class JdbcSignalOutcomeStatistics implements SignalOutcomeStatistics {

    private static final String DAYS = "greatest(extract(epoch FROM answered_at - submitted_at), 0) / 86400.0";

    private static final String BY_DISTRICT = """
        SELECT district_code,
               count(*)                                                                 AS signals,
               count(answered_at)                                                       AS answered,
               percentile_cont(0.5) WITHIN GROUP (ORDER BY %1$s) FILTER (WHERE answered_at IS NOT NULL) AS median_days,
               percentile_cont(0.9) WITHIN GROUP (ORDER BY %1$s) FILTER (WHERE answered_at IS NOT NULL) AS p90_days,
               count(*) FILTER (WHERE submitted_at <= :dueBy)                           AS due,
               count(*) FILTER (WHERE submitted_at <= :dueBy
                                  AND answered_at <= submitted_at + interval '30 days')  AS answered_in_30,
               count(*) FILTER (WHERE last_status = 'CLOSED')                           AS closed,
               count(*) FILTER (WHERE last_status = 'ANSWERED')                         AS answered_only,
               count(*) FILTER (WHERE last_status = 'REJECTED')                         AS rejected,
               count(*) FILTER (WHERE last_status IS NULL
                                   OR last_status NOT IN ('CLOSED', 'ANSWERED', 'REJECTED')) AS still_open
        FROM call_sofia_signal_outcomes
        WHERE district_code IS NOT NULL
          AND submitted_at >= :from AND submitted_at < :to
        GROUP BY district_code
        """.formatted(DAYS);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSignalOutcomeStatistics(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ArchiveSpan> span() {
        return Optional.ofNullable(jdbc.getJdbcTemplate().queryForObject(
            "SELECT min(submitted_at) AS first, max(submitted_at) AS last FROM call_sofia_signal_outcomes",
            (rs, row) -> rs.getTimestamp("first") == null
                ? null
                : new ArchiveSpan(rs.getTimestamp("first").toInstant(), rs.getTimestamp("last").toInstant())));
    }

    @Override
    public List<DistrictOutcomes> byDistrict(Instant from, Instant to, Instant dueBy) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", utc(from))
            .addValue("to", utc(to))
            .addValue("dueBy", utc(dueBy));
        return jdbc.query(BY_DISTRICT, params, JdbcSignalOutcomeStatistics::row);
    }

    private static DistrictOutcomes row(ResultSet rs, int rowNum) throws SQLException {
        return new DistrictOutcomes(
            rs.getString("district_code"),
            rs.getInt("signals"),
            rs.getInt("answered"),
            nullableDouble(rs, "median_days"),
            nullableDouble(rs, "p90_days"),
            rs.getInt("due"),
            rs.getInt("answered_in_30"),
            rs.getInt("closed"),
            rs.getInt("answered_only"),
            rs.getInt("rejected"),
            rs.getInt("still_open"));
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
