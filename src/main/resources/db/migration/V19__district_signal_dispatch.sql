-- Where each district receives reports, and a record of every report sent to one.

-- The address a district administration takes citizen reports at.
--
-- These are PLACEHOLDERS on the reserved .test domain, not the real addresses. The real
-- ones are published by each district administration and change; putting unverified ones
-- here would send real mail to real officials from a project that is not meant to. Every
-- environment delivers to MailHog, where these are readable as they are. Replace them with
-- a migration once the real addresses have been checked — never in place.
ALTER TABLE districts ADD COLUMN signal_email VARCHAR(254);
UPDATE districts SET signal_email = lower(replace(code, '_', '-')) || '@rayon.nabat.test';
ALTER TABLE districts ALTER COLUMN signal_email SET NOT NULL;

-- One row per report delivered to a district. Owned by jurisdiction.
--
-- The dispatch listener is replayed at least once — by the publication registry after a
-- failure or a restart — and each replay would otherwise send the district another copy.
-- The row is written in the same transaction as the move to SENT, and a replay that finds
-- it skips the mail. The primary key is the report id: one report, one delivery.
--
-- No foreign key to alerts, for the same reason alerts.district_code has none to districts:
-- the tables belong to different modules.
CREATE TABLE signal_dispatches (
    alert_id      UUID         PRIMARY KEY,
    district_code VARCHAR(32)  NOT NULL,
    recipient     VARCHAR(254) NOT NULL,
    sent_at       TIMESTAMPTZ  NOT NULL
);
