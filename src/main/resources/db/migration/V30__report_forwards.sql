-- A report handed on to another authority: "not ours, it is X's".
--
-- The routing by category (V29) will sometimes be wrong, and the authority that receives
-- someone else's report could only reject it — which is final, closes the thread, and reads
-- as a refusal when it was a wrong address. A forward moves the report's authority instead,
-- and it is mailed again, to the new one.

-- Every forward, kept. The chain is the record worth showing: a report passed between a
-- district and a utility for three weeks is exactly what "forwarded by competence" hides.
-- Owned by incident. The authority codes are not foreign keys — the authorities belong to
-- jurisdiction — and are checked when the forward is made.
CREATE TABLE report_forwards (
    id              UUID         PRIMARY KEY,
    alert_id        UUID         NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    -- 1 for the first forward, then 2, 3... The delivery for creation is 0.
    sequence        INTEGER      NOT NULL CHECK (sequence > 0),
    from_authority  VARCHAR(32)  NOT NULL,
    to_authority    VARCHAR(32)  NOT NULL,
    forwarded_by    UUID         NOT NULL,
    -- Whether a moderator made it rather than the authority that had the report.
    by_moderator    BOOLEAN      NOT NULL,
    reason          VARCHAR(1000) NOT NULL,
    forwarded_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT report_forwards_sequence_unique UNIQUE (alert_id, sequence),
    CONSTRAINT report_forwards_somewhere_else CHECK (from_authority <> to_authority)
);

-- One delivery per report became one per report and forward: the original mail is
-- sequence 0, the mail to whoever the n-th forward named is sequence n. Still exactly one
-- of each — a replay that finds its row sends nothing.
ALTER TABLE signal_dispatches ADD COLUMN sequence INTEGER NOT NULL DEFAULT 0;
ALTER TABLE signal_dispatches DROP CONSTRAINT signal_dispatches_pkey;
ALTER TABLE signal_dispatches ADD PRIMARY KEY (alert_id, sequence);
ALTER TABLE signal_dispatches ALTER COLUMN sequence DROP DEFAULT;
