package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.incident.domain.AlertStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AlertJpaRepository extends JpaRepository<AlertJpaEntity, UUID> {

    List<AlertJpaEntity> findByStatus(AlertStatus status);

    /*
     * The public list. One query for all three orders: ORDER BY cannot take a bind
     * parameter, and three copies of the same filters would drift. Each CASE is null for the
     * orders it does not apply to, so only one of them decides; the id breaks ties so that
     * paging is stable.
     *
     * The HOT score is ReportRanking.HOT's formula, and the two must be changed together.
     * Age is LOCALTIMESTAMP - created_at: created_at is a TIMESTAMP without zone, written in
     * the JVM's zone, and pgjdbc sets the session to that same zone, so both sides are the
     * same local clock. Comparing against a bound Instant instead would mix a zoned value
     * with an unzoned one and shift every age by the UTC offset.
     */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status IN (:statuses)
        AND (CAST(:district AS text) IS NULL OR a.district_code = CAST(:district AS text))
        ORDER BY
          CASE WHEN :ranking = 'HOT' THEN
            (GREATEST(a.confirmation_count + a.upvote_count - a.downvote_count, 0) + 1)
            / power(GREATEST(EXTRACT(EPOCH FROM (LOCALTIMESTAMP - a.created_at)) / 86400.0, 0) + 2, 1.5)
          END DESC,
          CASE WHEN :ranking = 'LONGEST_WAITING' THEN a.created_at END ASC,
          CASE WHEN :ranking = 'NEWEST' THEN a.created_at END DESC,
          a.id
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<AlertJpaEntity> findRanked(
        @Param("statuses") Collection<String> statuses,
        @Param("district") String districtCode,
        @Param("ranking") String ranking,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT count(*) FROM alerts a
        WHERE a.status IN (:statuses)
        AND (CAST(:district AS text) IS NULL OR a.district_code = CAST(:district AS text))
        """, nativeQuery = true)
    long countRanked(@Param("statuses") Collection<String> statuses, @Param("district") String districtCode);

    List<AlertJpaEntity> findByDistrictCodeAndStatusInOrderByCreatedAtDesc(
        String districtCode, Collection<AlertStatus> statuses, Limit limit);

    /*
     * The four spatial queries below share a shape: circle predicate, optional category
     * filter, newest first, LIMIT.
     *
     * "Open" is spelled out as `NOT IN ('RESOLVED', 'REJECTED')`, the SQL twin of
     * AlertStatus.isOpen(). Naming the closed states rather than the open ones is
     * deliberate: a new open state then appears on the map without touching this file,
     * whereas a list of open states would hide it until someone remembered.
     *
     * On the filters — `CAST(:category AS integer) IS NULL OR ...` rather than the obvious
     * `:category IS NULL OR ...`. PostgreSQL infers parameter types from context, and in a
     * bare `NULL` comparison there is no context, so it fails the statement with
     * "could not determine data type of parameter". The cast supplies the type.
     * The category may be a group: a report is filed under a subcategory, so a group code
     * matches through report_categories.group_code rather than directly.
     *
     * On the LIMIT — it belongs here rather than in Java. Trimming the list after the
     * fact would still make the database materialise and ship every row in the radius,
     * which is exactly the cost this is meant to avoid. Combined with ORDER BY
     * created_at DESC it means a truncated response drops the *oldest* matches, which is
     * the right end to lose for a live incident map.
     */

    /**
     * Open reports close to a point, nearest first — the duplicate check before reporting.
     * PostGIS only: V18 made it a requirement, and this was written after.
     */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status NOT IN ('RESOLVED', 'REJECTED')
        AND a.category_code IN (:categories)
        AND ST_DWithin(
            a.location_geog,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
            :meters
        )
        ORDER BY ST_Distance(a.location_geog, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography)
        LIMIT :limit
        """, nativeQuery = true)
    List<AlertJpaEntity> findOpenNear(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("meters") double meters,
        @Param("categories") Collection<Integer> categories,
        @Param("limit") int limit
    );

    /** PostGIS path — used when the location_geog generated column exists. */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status NOT IN ('RESOLVED', 'REJECTED')
        AND (CAST(:category AS integer) IS NULL OR a.category_code = CAST(:category AS integer)
             OR a.category_code IN (SELECT c.code FROM report_categories c WHERE c.group_code = CAST(:category AS integer)))
        AND ST_DWithin(
            a.location_geog,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
            :radius * 1000.0
        )
        ORDER BY a.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadius(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("category") Integer category,
        @Param("limit") int limit
    );

    /**
     * PostGIS path, restricted to alerts created at or after {@code since}.
     * Backs the WebSocket reconnect catch-up, which previously passed a
     * {@code since} parameter the API silently ignored.
     */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status NOT IN ('RESOLVED', 'REJECTED')
        AND a.created_at >= :since
        AND (CAST(:category AS integer) IS NULL OR a.category_code = CAST(:category AS integer)
             OR a.category_code IN (SELECT c.code FROM report_categories c WHERE c.group_code = CAST(:category AS integer)))
        AND ST_DWithin(
            a.location_geog,
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
            :radius * 1000.0
        )
        ORDER BY a.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadiusSince(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("since") Instant since,
        @Param("category") Integer category,
        @Param("limit") int limit
    );

    /** Haversine equivalent of {@link #findActiveAlertsWithinRadiusSince}. */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status NOT IN ('RESOLVED', 'REJECTED')
        AND a.created_at >= :since
        AND (CAST(:category AS integer) IS NULL OR a.category_code = CAST(:category AS integer)
             OR a.category_code IN (SELECT c.code FROM report_categories c WHERE c.group_code = CAST(:category AS integer)))
        AND (6371 * acos(
            LEAST(1.0, cos(radians(:lat)) * cos(radians(a.latitude))
            * cos(radians(a.longitude) - radians(:lon))
            + sin(radians(:lat)) * sin(radians(a.latitude)))
        )) <= :radius
        ORDER BY a.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadiusSinceHaversine(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("since") Instant since,
        @Param("category") Integer category,
        @Param("limit") int limit
    );

    /** Haversine fallback — used when PostGIS is not installed on the server. */
    @Query(value = """
        SELECT * FROM alerts a
        WHERE a.status NOT IN ('RESOLVED', 'REJECTED')
        AND (CAST(:category AS integer) IS NULL OR a.category_code = CAST(:category AS integer)
             OR a.category_code IN (SELECT c.code FROM report_categories c WHERE c.group_code = CAST(:category AS integer)))
        AND (6371 * acos(
            LEAST(1.0, cos(radians(:lat)) * cos(radians(a.latitude))
            * cos(radians(a.longitude) - radians(:lon))
            + sin(radians(:lat)) * sin(radians(a.latitude)))
        )) <= :radius
        ORDER BY a.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<AlertJpaEntity> findActiveAlertsWithinRadiusHaversine(
        @Param("lat") double latitude,
        @Param("lon") double longitude,
        @Param("radius") double radiusKm,
        @Param("category") Integer category,
        @Param("limit") int limit
    );

    /**
     * Of the given storage names, those that are the last path segment of some alert's
     * {@code photo_url}.
     *
     * <p>Backs the orphaned-upload sweep. Alerts store a serving URL while the caller holds
     * bare filenames, so the two have to be related somehow; passing whole URLs instead
     * would make {@code incident} responsible for knowing how {@code media} builds them.
     *
     * <p>The comparison is on the final segment rather than {@code LIKE '%' || name}. A
     * plain suffix match reports {@code graph.jpg} as referenced whenever any alert points
     * at {@code photograph.jpg}, because the URL does end with those characters. Here that
     * errs safe — an orphan is kept, not a live photo deleted — but it means files quietly
     * never get reclaimed, which is the bug this job exists to fix.
     *
     * <p>Not indexable, deliberately: a few hundred names at a time, once an hour, against
     * a column holding a URL rather than a key. Reshaping the schema so a housekeeping job
     * can use an index would be the wrong trade.
     */
    @Query(value = """
        SELECT DISTINCT f.name FROM unnest(CAST(:filenames AS text[])) AS f(name)
        WHERE EXISTS (
            SELECT 1 FROM alerts a
            WHERE a.photo_url IS NOT NULL
            AND substring(a.photo_url from '[^/]+$') = f.name
        )
        """, nativeQuery = true)
    Set<String> findReferencedPhotoFilenames(@Param("filenames") String[] filenames);

    /** Convenience over the array form, so callers can pass the set they already have. */
    default Set<String> findReferencedPhotoFilenames(Set<String> filenames) {
        return findReferencedPhotoFilenames(filenames.toArray(String[]::new));
    }

    /**
     * {@code flushAutomatically} so pending changes are not lost, and
     * {@code clearAutomatically} so a subsequent {@code findById} in the same
     * transaction re-reads from the database instead of returning the stale
     * first-level-cache copy. Without the latter, whether a caller saw the new
     * counts depended purely on whether it had already loaded the entity.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE AlertJpaEntity a
        SET a.upvoteCount = :upvotes,
            a.downvoteCount = :downvotes,
            a.confirmationCount = :confirmations,
            a.credibilityScore = :credibilityScore
        WHERE a.id = :id
        """)
    int updateVoteCounts(
            @Param("id") UUID id,
            @Param("upvotes") int upvotes,
            @Param("downvotes") int downvotes,
            @Param("confirmations") int confirmations,
            @Param("credibilityScore") int credibilityScore
    );

}
