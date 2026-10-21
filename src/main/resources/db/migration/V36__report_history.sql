-- Who did what to a report, and when.
--
-- A report said where it was and, once closed, who closed it — "the authority", "the
-- reporter" — but not which person acknowledged it, started work on it, or rejected it, and
-- nothing of the steps between. With several officials per authority that is the difference
-- between an authority that answers and one where nobody knows who did.
--
-- One row per step, written in the transaction that takes it. actor_name and by_authority are
-- the actor's name and standing when they acted, kept like a comment's, not looked up. No
-- actor for what nabat itself does (the delivery). authority_code is the authority the report
-- was with at the time. Owned by incident. Reports older than this have no history; none is
-- invented for them.
CREATE TABLE report_history (
    id              UUID         PRIMARY KEY,
    alert_id        UUID         NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    step            VARCHAR(16)  NOT NULL CHECK (step IN (
                        'CREATED', 'SENT', 'ACKNOWLEDGED', 'WORK_STARTED', 'RESOLVED',
                        'REJECTED', 'FORWARDED', 'REOPENED', 'WITHDRAWN')),
    actor_id        UUID,
    actor_name      VARCHAR(100),
    by_authority    BOOLEAN      NOT NULL,
    authority_code  VARCHAR(32),
    happened_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT report_history_actor_whole CHECK ((actor_id IS NULL) = (actor_name IS NULL))
);

CREATE INDEX idx_report_history_alert ON report_history (alert_id, happened_at);
