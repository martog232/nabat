-- Call Sofia's own reports, as published by Boyan Yurukov, owned by the callsofia module.
--
-- These are not nabat reports and do not go in alerts. They have no author here, were
-- never sent to anyone by nabat, and follow Call Sofia's lifecycle rather than ours;
-- in alerts they would be mailed to the districts by the dispatch listener and ranked
-- alongside reports people made in this application.
--
-- Source: https://opendata.yurukov.net/callsofia/data/ — CC BY-SA 2.5 BG. Anything
-- published from these tables must credit Yurukov and carry the same licence.

-- Call Sofia's category index. Codes below 1000 are groups; a larger code is a
-- subcategory of group code / 1000 (28068 is under 28, street lighting).
CREATE TABLE call_sofia_categories (
    code INTEGER      PRIMARY KEY,
    name VARCHAR(500) NOT NULL
);

-- One row per report filed in Call Sofia. The id is Call Sofia's, which is what makes a
-- second import of the same archive an update rather than a copy.
--
-- district_code uses the codes in districts but, as on alerts, is not a foreign key: the
-- table belongs to another module. NULL where Call Sofia recorded no district.
-- location is NULL where the reporter gave no point.
CREATE TABLE call_sofia_signals (
    id            BIGINT                 PRIMARY KEY,
    category_code INTEGER                NOT NULL REFERENCES call_sofia_categories (code),
    district_code VARCHAR(32),
    submitted_at  TIMESTAMPTZ            NOT NULL,
    location      GEOGRAPHY(Point, 4326),
    title         TEXT                   NOT NULL
);

CREATE INDEX idx_call_sofia_signals_district_submitted
    ON call_sofia_signals (district_code, submitted_at);

-- Every status a report went through, in the order Call Sofia recorded them. position is
-- the order, not changed_at: two changes often share a timestamp ("answered" and "closed"
-- in the same second), and only the archive's order says which came last.
--
-- Who made each change is in the archive — names of municipal staff — and deliberately
-- not here. Nothing nabat shows needs it.
CREATE TABLE call_sofia_status_changes (
    signal_id  BIGINT      NOT NULL REFERENCES call_sofia_signals (id),
    position   INTEGER     NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    status     VARCHAR(32) NOT NULL,
    PRIMARY KEY (signal_id, position),
    CONSTRAINT call_sofia_status_changes_status_check CHECK (status IN (
        'UNKNOWN', 'AWAITING_PROCESSING', 'PROCESSING', 'AWAITING_REPLY',
        'ANSWERED', 'CLOSED', 'REJECTED'
    ))
);
