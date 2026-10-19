-- When the authority a report is with has to have answered by.
--
-- Call Sofia gives an administration 30 days; nabat gave none, so a report could wait on an
-- authority for ever and nothing anywhere would say it was late. The deadline is set as a
-- report reaches its authority — delivered, delivered again after a forward, or reopened after
-- a disputed rejection — and cleared by the authority's first answer or by anyone closing it.
-- NULL exactly while nothing is waiting: before delivery, after an answer, once closed.
--
-- Stored as the instant rather than as "sent at": a report keeps the deadline it was given if
-- the period is later changed.
ALTER TABLE alerts ADD COLUMN answer_due_at TIMESTAMPTZ;

-- Reports waiting now get the period from when they were last delivered — the latest
-- dispatch, or creation for any older than V19 — so the ones long overdue show as such.
UPDATE alerts a
SET answer_due_at = coalesce(
        (SELECT max(d.sent_at) FROM signal_dispatches d WHERE d.alert_id = a.id),
        a.created_at AT TIME ZONE current_setting('TimeZone')
    ) + INTERVAL '30 days'
WHERE a.status = 'SENT';

-- The overdue list and the reminder job both look for these.
CREATE INDEX idx_alerts_answer_due ON alerts (answer_due_at) WHERE answer_due_at IS NOT NULL;
