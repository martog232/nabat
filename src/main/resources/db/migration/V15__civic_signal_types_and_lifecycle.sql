-- nabat stops being a safety-alert feed and becomes a channel for reporting problems in the
-- city to whoever is responsible for fixing them. Two columns change meaning.
--
-- alerts.type / user_subscriptions.alert_type: incident categories (CRIME, FIRE, ...) are
-- replaced by things a citizen sees on the street (POTHOLE, MANHOLE, ...). No old category
-- maps honestly onto a new one — a HAZARD is not necessarily a pothole — so every existing
-- row becomes OTHER rather than being guessed into a category it may not belong to.
--
-- alerts.status: ACTIVE / RESOLVED becomes a lifecycle (see AlertStatus). An ACTIVE alert
-- has been accepted and delivered to nobody, which is SUBMITTED. RESOLVED keeps its name.
--
-- Both columns also get a CHECK. Like users.role in V12, they have accepted any string since
-- V1, and the enum mapping only notices on read — a row nobody can load, rather than a write
-- that is refused. The enums in incident/domain are the authority for these lists; adding a
-- constant there without adding it here fails the first insert that uses it.

UPDATE alerts SET type = 'OTHER';
UPDATE alerts SET status = 'SUBMITTED' WHERE status = 'ACTIVE';

-- Collapsing every category into one can make two subscriptions of the same user at the same
-- point identical, which the unique constraint from V1 forbids. Keep one of each: they now
-- ask for exactly the same thing. The widest radius wins, so nobody hears about less than
-- they did before.
DELETE FROM user_subscriptions s
USING user_subscriptions keep
WHERE s.user_id = keep.user_id
  AND s.latitude = keep.latitude
  AND s.longitude = keep.longitude
  AND (s.radius_km < keep.radius_km OR (s.radius_km = keep.radius_km AND s.id > keep.id));

UPDATE user_subscriptions SET alert_type = 'OTHER';

ALTER TABLE alerts
    ADD CONSTRAINT alerts_type_check
    CHECK (type IN ('POTHOLE', 'MANHOLE', 'WASTE', 'POLLUTION', 'STREET_LIGHT', 'ILLEGAL_PARKING', 'OTHER'));

ALTER TABLE alerts
    ADD CONSTRAINT alerts_status_check
    CHECK (status IN ('SUBMITTED', 'SENT', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'));

ALTER TABLE user_subscriptions
    ADD CONSTRAINT user_subscriptions_alert_type_check
    CHECK (alert_type IN ('POTHOLE', 'MANHOLE', 'WASTE', 'POLLUTION', 'STREET_LIGHT', 'ILLEGAL_PARKING', 'OTHER'));
