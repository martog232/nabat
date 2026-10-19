-- A rejection says why, can be disputed by the reporter, and a moderator decides the dispute.
--
-- Rejecting was the strongest move an authority could make and the least guarded: final, it
-- locked the thread, it needed no reason — forwarding needs one — and nobody could contest
-- it, while a "resolved" can be disputed through "is it really fixed?". The easiest way for an
-- authority to be rid of an awkward report was to reject it rather than forward it.
--
-- One row per rejection, kept: a report reopened after a dispute and rejected again has two.
-- Owned by incident. Reports rejected before this have no row, and so no reason on record and
-- nothing to dispute; guessing one would be worse than saying so.
CREATE TABLE report_rejections (
    id              UUID          PRIMARY KEY,
    alert_id        UUID          NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    rejected_by     UUID          NOT NULL,
    -- A moderator rather than the authority. Not a foreign key, like the authority code: users
    -- belong to identity, authorities to jurisdiction.
    by_moderator    BOOLEAN       NOT NULL,
    authority_code  VARCHAR(32)   NOT NULL,
    reason          VARCHAR(1000) NOT NULL,
    rejected_at     TIMESTAMPTZ   NOT NULL,
    -- The reporter's dispute, once, with why.
    dispute_reason  VARCHAR(1000),
    disputed_at     TIMESTAMPTZ,
    -- A moderator's answer to the dispute.
    decision        VARCHAR(10),
    decided_by      UUID,
    decided_at      TIMESTAMPTZ,
    CONSTRAINT report_rejections_dispute_whole CHECK ((dispute_reason IS NULL) = (disputed_at IS NULL)),
    CONSTRAINT report_rejections_decision_whole CHECK (
        (decision IS NULL) = (decided_at IS NULL) AND (decision IS NULL) = (decided_by IS NULL)),
    CONSTRAINT report_rejections_decided_after_dispute CHECK (decision IS NULL OR disputed_at IS NOT NULL),
    CONSTRAINT report_rejections_decision_check CHECK (decision IN ('UPHELD', 'REOPENED'))
);

CREATE INDEX idx_report_rejections_alert ON report_rejections (alert_id, rejected_at);

-- The moderators' queue: disputed, not yet decided.
CREATE INDEX idx_report_rejections_open_disputes ON report_rejections (disputed_at)
    WHERE disputed_at IS NOT NULL AND decision IS NULL;
