-- What was done about a report past its answer deadline: the authority reminded, then the
-- municipality told.
--
-- One row per step and deadline. Keyed by the deadline itself, so a report that gets a new
-- one — forwarded, or reopened after a dispute — is reminded about the new one as well, and
-- the row is what keeps a step from being taken twice: the watch inserts it and only acts if
-- the insert went in, so two replicas running the watch at once do it once. Owned by incident.
CREATE TABLE report_deadline_steps (
    alert_id  UUID        NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    due_at    TIMESTAMPTZ NOT NULL,
    stage     VARCHAR(16) NOT NULL CHECK (stage IN ('REMINDER', 'ESCALATION')),
    taken_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (alert_id, due_at, stage)
);
