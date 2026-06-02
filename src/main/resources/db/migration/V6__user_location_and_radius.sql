ALTER TABLE users
    ADD COLUMN notification_radius_km INTEGER NOT NULL DEFAULT 5
        CHECK (notification_radius_km IN (1, 5, 10, 25, 50)),
    ADD COLUMN last_known_lat DOUBLE PRECISION,
    ADD COLUMN last_known_lng DOUBLE PRECISION,
    ADD COLUMN location_updated_at TIMESTAMPTZ;
