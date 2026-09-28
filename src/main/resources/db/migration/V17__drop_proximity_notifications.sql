-- nabat pushed every new alert to the people near it: subscribers to its type inside a
-- circle (user_subscriptions), and users whose last known location fell inside their own
-- notification radius (the four users columns from V6). That was the point of a safety
-- feed. A report about the city is not something to push at a passer-by — the map shows
-- it to whoever is looking, and the people who will hear about it are the ones connected
-- to it, not the ones standing near it.
--
-- Dropped rather than kept for later. Subscriptions were a point and a radius; the
-- replacement that makes sense here is following a district, which is a different shape
-- and will get its own table once districts exist. Keeping this one would only mean a
-- table nothing reads and a location history of every user nobody uses.
--
-- V15 rewrote user_subscriptions.alert_type and added a CHECK to it. Both go with the table.

DROP TABLE user_subscriptions;

ALTER TABLE users
    DROP COLUMN notification_radius_km,
    DROP COLUMN last_known_lat,
    DROP COLUMN last_known_lng,
    DROP COLUMN location_updated_at;
