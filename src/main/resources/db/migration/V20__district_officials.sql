-- Accounts that act for a district administration, and who closed a report.

-- A district official is a user who speaks for one district: acknowledges its reports,
-- starts work on them, resolves or rejects them — and only its own. The district is part of
-- the account, not of the role, because the role is the same for all 24 and the territory
-- is what differs.
--
-- Not a foreign key to districts, for the same reason as alerts.district_code: the tables
-- belong to different modules. The code is checked when the role is assigned.
ALTER TABLE users ADD COLUMN district_code VARCHAR(32);

-- The role list, now with DISTRICT_OFFICIAL. Same reasoning as V12: the enum is mapped by
-- name, so a stray value fails on read, in the authentication path, rather than on write.
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'MODERATOR', 'ADMIN', 'DISTRICT_OFFICIAL'));

-- An official without a district could act nowhere, and a district on any other role would
-- be a grant waiting for someone to misread it. The two go together or not at all.
ALTER TABLE users
    ADD CONSTRAINT users_district_official_check
    CHECK ((role = 'DISTRICT_OFFICIAL') = (district_code IS NOT NULL));

-- Who closed a report. The point is to tell "the district says it is fixed" from "the
-- person who reported it says so" — both are worth showing, and they are not worth the
-- same. NULL while the report is open, and on reports closed before this existed: which of
-- the three closed those is not recorded anywhere, and guessing would be worse than saying so.
ALTER TABLE alerts ADD COLUMN closed_by VARCHAR(20);
ALTER TABLE alerts
    ADD CONSTRAINT alerts_closed_by_check
    CHECK (closed_by IN ('DISTRICT', 'REPORTER', 'MODERATOR'));
