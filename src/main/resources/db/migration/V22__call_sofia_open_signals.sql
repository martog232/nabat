-- Reports open in Call Sofia right now, as its public map shows them. Owned by callsofia.
--
-- Separate from call_sofia_signals, which is the archive and is for statistics. These are
-- observations: what the map showed and when. The map has no district, no history and a
-- category list of its own (codes 1000 and up, unrelated to the archive's), so the two
-- tables do not share columns that would mean the same thing.

-- The map shows reports in processing, filed in the last 30 days. A report leaves it for
-- one of two reasons, and which one is known at the moment it goes:
--   AGED_OUT              filed more than 30 days ago; its status is not known
--   NO_LONGER_PROCESSING  still inside the 30 days, so its status changed —
--                         answered, closed or rejected, the map does not say which
-- left_map_at is when nabat first noticed, not when it happened: up to one poll late.
CREATE TABLE call_sofia_open_signals (
    id              BIGINT                 PRIMARY KEY,
    registry_number VARCHAR(100),
    category_id     INTEGER                NOT NULL,
    category_name   VARCHAR(255)           NOT NULL,
    status          VARCHAR(32)            NOT NULL,
    title           TEXT                   NOT NULL,
    submitted_at    TIMESTAMPTZ            NOT NULL,
    location        GEOGRAPHY(Point, 4326),
    -- Computed by nabat from the point, against districts; the map does not give one.
    -- Not a foreign key: districts belongs to another module.
    district_code   VARCHAR(32),
    first_seen_at   TIMESTAMPTZ            NOT NULL,
    last_seen_at    TIMESTAMPTZ            NOT NULL,
    left_map_at     TIMESTAMPTZ,
    left_map_reason VARCHAR(32),
    CONSTRAINT call_sofia_open_signals_status_check CHECK (status IN (
        'UNKNOWN', 'AWAITING_PROCESSING', 'PROCESSING', 'AWAITING_REPLY',
        'ANSWERED', 'CLOSED', 'REJECTED'
    )),
    CONSTRAINT call_sofia_open_signals_left_map_reason_check CHECK (
        left_map_reason IN ('AGED_OUT', 'NO_LONGER_PROCESSING')
    ),
    CONSTRAINT call_sofia_open_signals_left_map_check CHECK (
        (left_map_at IS NULL) = (left_map_reason IS NULL)
    )
);

CREATE INDEX idx_call_sofia_open_signals_on_map
    ON call_sofia_open_signals (district_code, submitted_at)
    WHERE left_map_at IS NULL;
