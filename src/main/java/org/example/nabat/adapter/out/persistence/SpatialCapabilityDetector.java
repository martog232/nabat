package org.example.nabat.adapter.out.persistence;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Detects at startup whether the PostGIS extension is installed on the connected
 * PostgreSQL server. When PostGIS is absent the application falls back to
 * Haversine-based distance queries that work on plain PostgreSQL.
 *
 * <p>This allows developers to run the app against both:
 * <ul>
 *   <li>A plain {@code postgres:N} image / native PostgreSQL without PostGIS — Haversine fallback.</li>
 *   <li>A {@code postgis/postgis:N-M} image or native PostgreSQL with PostGIS installed — fast
 *       {@code ST_DWithin} spatial-index queries.</li>
 * </ul>
 * </p>
 */
@Getter
@Component
public class SpatialCapabilityDetector {

    private static final Logger log = LoggerFactory.getLogger(SpatialCapabilityDetector.class);

    private static final String CHECK_SQL =
            "SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'";

    private final boolean postgisAvailable;

    public SpatialCapabilityDetector(DataSource dataSource) {
        boolean available = false;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(CHECK_SQL);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                available = rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            log.warn("Could not determine PostGIS availability — assuming unavailable. Cause: {}", e.getMessage());
        }
        this.postgisAvailable = available;
        if (available) {
            log.info("Spatial queries: PostGIS ST_DWithin (indexed)");
        } else {
            log.warn("Spatial queries: Haversine fallback (PostGIS not installed on this server). "
                    + "For production use, install PostGIS or use the postgis/postgis Docker image.");
        }
    }

}

