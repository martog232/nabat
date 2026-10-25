-- "Is it really fixed?" — citizens checking a report that has been closed as resolved.
--
-- A district marking a report resolved is its own word about its own work. Call Sofia's
-- data has the same gap: its "closed" says the paperwork ended, not that anything was
-- mended, and nobody asks the person who reported it. This is where they are asked.
--
-- One row per person per report; changing one's mind overwrites the row. by_reporter is
-- kept on the row rather than looked up, because the reporter's answer is weighed
-- differently (see FixCheckTally) and the reporter of a report never changes.
-- user_id is not a foreign key: users belongs to identity.
CREATE TABLE fix_checks (
    alert_id    UUID        NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL,
    verdict     VARCHAR(16) NOT NULL,
    by_reporter BOOLEAN     NOT NULL,
    checked_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (alert_id, user_id),
    CONSTRAINT fix_checks_verdict_check CHECK (verdict IN ('FIXED', 'PARTLY', 'NOT_FIXED'))
);

-- The tally, kept on the report so every list can show it without a join. Written only by
-- JdbcFixCheckStore, which recounts from fix_checks under a lock on the report row; the
-- JPA entity maps these columns read-only, so saving a report never writes stale counts.
ALTER TABLE alerts
    ADD COLUMN fix_fixed_count     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN fix_partly_count    INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN fix_not_fixed_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reporter_fix_verdict VARCHAR(16),
    ADD CONSTRAINT alerts_reporter_fix_verdict_check
        CHECK (reporter_fix_verdict IN ('FIXED', 'PARTLY', 'NOT_FIXED'));
