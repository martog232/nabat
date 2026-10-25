package org.example.nabat.jurisdiction.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.jurisdiction.domain.District;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The boundaries V18 loads, and the one query asked of them.
 *
 * <p>Against the real migration and real PostGIS, because everything that can go wrong here
 * is a fact about the data or the database: a polygon that is invalid, two that overlap, a
 * point built latitude-first, a district that went missing on the next refresh from OSM.
 * A mock would agree with all of those.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DistrictRepositoryAdapter.class)
class DistrictBoundariesIntegrationTest extends PostgresTestSupport {

    @Autowired
    private DistrictRepositoryAdapter districts;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Places whose district is not in doubt, one per corner of the city and its middle.
     * Latitude first, as people write it — the query has to turn it round.
     */
    @ParameterizedTest(name = "{0} is in {3}")
    @CsvSource({
        "Studentski grad,  42.6515, 23.3455, STUDENTSKI",
        "Lyulin 5,         42.7140, 23.2530, LYULIN",
        "Mladost 1,        42.6503, 23.3790, MLADOST",
        "Bankya,           42.7070, 23.1450, BANKYA",
        "Pancharevo lake,  42.6000, 23.4100, PANCHAREVO"
    })
    void findsTheDistrictAPlaceIsIn(String place, double lat, double lon, String expected) {
        assertThat(districts.findCovering(Location.of(lat, lon)))
            .map(District::code)
            .contains(expected);
    }

    @ParameterizedTest(name = "{0} is outside the city")
    @CsvSource({
        "Plovdiv, 42.1354, 24.7453",
        "Pernik,  42.6052, 23.0378"
    })
    void findsNothingOutsideTheCity(String place, double lat, double lon) {
        assertThat(districts.findCovering(Location.of(lat, lon))).isEmpty();
    }

    @Test
    void listsAllTwentyFourByName() {
        List<District> all = districts.findAllOrderedByName();

        assertThat(all).hasSize(24);
        assertThat(all).extracting(District::name).isSorted();
        assertThat(all).extracting(District::code).contains("LOZENETS", "TRIADITSA", "VITOSHA");
    }

    /**
     * What was checked by hand when the data was imported, kept as a test so a refresh from
     * OSM cannot quietly break it. An invalid polygon makes ST_Covers unreliable; an overlap
     * sends one report to two districts; a gap refuses a report on a street inside the city.
     * The union's area guards the gap case: Stolichna obshtina is about 1342 km².
     */
    @Test
    void theBoundariesTileTheCityWithoutOverlapsOrGaps() {
        assertThat(jdbc.queryForObject(
            "SELECT bool_and(ST_IsValid(boundary)) FROM districts", Boolean.class))
            .as("every boundary is a valid polygon")
            .isTrue();

        assertThat(jdbc.queryForObject("""
            SELECT count(*) FROM districts a JOIN districts b
              ON a.code < b.code AND ST_Intersects(a.boundary, b.boundary)
            WHERE ST_Area(ST_Intersection(a.boundary, b.boundary)::geography) > 1
            """, Integer.class))
            .as("pairs of districts overlapping by more than a square metre")
            .isZero();

        assertThat(jdbc.queryForObject(
            "SELECT ST_NumGeometries(ST_Union(boundary)) FROM districts", Integer.class))
            .as("the city is one piece")
            .isEqualTo(1);

        assertThat(jdbc.queryForObject(
            "SELECT ST_Area(ST_Union(boundary)::geography) / 1e6 FROM districts", Double.class))
            .as("area of the city in km²")
            .isBetween(1330.0, 1355.0);
    }
}
