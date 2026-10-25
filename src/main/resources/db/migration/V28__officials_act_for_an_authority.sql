-- An official acts for an authority, not only for a district.
--
-- A district was the only body a report could go to, so the account, the report and the
-- comment all said "district". A report about a bus stop or a stray dog is answered by a
-- city-wide institution (V29), and calling that a district would be a lie in every place
-- that reads it. No behaviour changes here: every report's authority is still its district.

-- The role and the column on the account.
ALTER TABLE users DROP CONSTRAINT users_district_official_check;
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users RENAME COLUMN district_code TO authority_code;
UPDATE users SET role = 'OFFICIAL' WHERE role = 'DISTRICT_OFFICIAL';
ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'MODERATOR', 'ADMIN', 'OFFICIAL'));
-- Same rule as V20: an official without an authority acts nowhere, and an authority on any
-- other role is a grant waiting to be misread.
ALTER TABLE users
    ADD CONSTRAINT users_official_check
    CHECK ((role = 'OFFICIAL') = (authority_code IS NOT NULL));

-- Who answers for a report, beside where it is. Written once at creation, like district_code,
-- and like it not a foreign key: the authorities belong to jurisdiction. Until now that was
-- always the district.
ALTER TABLE alerts ADD COLUMN authority_code VARCHAR(32);
UPDATE alerts SET authority_code = district_code;
CREATE INDEX idx_alerts_authority_status ON alerts (authority_code, status);

-- Who closed it: the authority, whichever it is.
ALTER TABLE alerts DROP CONSTRAINT alerts_closed_by_check;
UPDATE alerts SET closed_by = 'AUTHORITY' WHERE closed_by = 'DISTRICT';
ALTER TABLE alerts
    ADD CONSTRAINT alerts_closed_by_check
    CHECK (closed_by IN ('AUTHORITY', 'REPORTER', 'MODERATOR'));

-- A comment by the authority that answers for the report.
ALTER TABLE alert_comments RENAME COLUMN by_district TO by_authority;

-- Events not yet delivered still say DISTRICT and byDistrict in their JSON, and would fail to
-- read back on replay, with nothing to retry them into. Completed ones are never read again.
UPDATE event_publication
SET serialized_event = replace(
        replace(serialized_event, '"closedBy":"DISTRICT"', '"closedBy":"AUTHORITY"'),
        '"byDistrict":', '"byAuthority":')
WHERE completion_date IS NULL;
