-- Enable PostGIS and add spatial indexes for radius searches.
-- Uses generated geography columns so existing latitude/longitude mapping remains unchanged.
--
-- This migration is intentionally wrapped in a PL/pgSQL DO block so that it degrades
-- gracefully on PostgreSQL installations that do not have the PostGIS extension binaries
-- installed (e.g. a plain postgres:16-alpine or a native install without StackBuilder).
-- When PostGIS is unavailable the migration succeeds as a no-op and the application
-- automatically falls back to Haversine distance calculations at runtime.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'postgis') THEN

        CREATE EXTENSION IF NOT EXISTS postgis;

        -- alerts spatial index
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'alerts' AND column_name = 'location_geog'
        ) THEN
            ALTER TABLE alerts
                ADD COLUMN location_geog geography(Point, 4326)
                    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
        END IF;

        CREATE INDEX IF NOT EXISTS idx_alerts_location_geog
            ON alerts USING GIST (location_geog);

        -- user_subscriptions spatial index
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'user_subscriptions' AND column_name = 'center_geog'
        ) THEN
            ALTER TABLE user_subscriptions
                ADD COLUMN center_geog geography(Point, 4326)
                    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
        END IF;

        CREATE INDEX IF NOT EXISTS idx_user_subscriptions_center_geog
            ON user_subscriptions USING GIST (center_geog);

        RAISE NOTICE 'V4: PostGIS extension enabled and spatial indexes created.';

    ELSE
        RAISE NOTICE 'V4: PostGIS extension not available on this server – spatial indexes skipped. Application will use Haversine fallback queries.';
    END IF;
END;
$$;

