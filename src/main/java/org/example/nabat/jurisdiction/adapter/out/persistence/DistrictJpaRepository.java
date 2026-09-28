package org.example.nabat.jurisdiction.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DistrictJpaRepository extends JpaRepository<DistrictJpaEntity, String> {

    List<DistrictJpaEntity> findAllByOrderByNameAsc();

    /**
     * {@code ST_Covers} on the geometry, served by the GiST index on {@code boundary}.
     * The point is built longitude first, which is the PostGIS order and the opposite of
     * how coordinates are usually written — swapping them lands every report in the
     * Indian Ocean, outside every district.
     */
    @Query(value = """
        SELECT d.* FROM districts d
        WHERE ST_Covers(d.boundary, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
        ORDER BY d.code
        LIMIT 1
        """, nativeQuery = true)
    Optional<DistrictJpaEntity> findCovering(@Param("lat") double latitude, @Param("lon") double longitude);
}
