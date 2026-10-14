-- One row per archived Call Sofia report: when it was first answered, and how it ended.
-- Owned by callsofia; the district comparison aggregates over it.
--
-- A materialised view because the answer comes from 2.9 million status changes and never
-- changes between imports. Working it out per request would cost seconds each time;
-- CallSofiaArchiveImportService refreshes it once, after an import.
--
-- answered_at is the first move to ANSWERED, CLOSED or REJECTED — the first time the
-- citizen heard anything final, which is what a deadline is about. It says nothing about
-- whether the problem was fixed; that is the point of keeping the two apart.
CREATE MATERIALIZED VIEW call_sofia_signal_outcomes AS
SELECT s.id            AS signal_id,
       s.district_code,
       s.submitted_at,
       first_answer.answered_at,
       last_step.status AS last_status
FROM call_sofia_signals s
LEFT JOIN (
    SELECT signal_id, min(changed_at) AS answered_at
    FROM call_sofia_status_changes
    WHERE status IN ('ANSWERED', 'CLOSED', 'REJECTED')
    GROUP BY signal_id
) first_answer ON first_answer.signal_id = s.id
LEFT JOIN (
    SELECT DISTINCT ON (signal_id) signal_id, status
    FROM call_sofia_status_changes
    ORDER BY signal_id, position DESC
) last_step ON last_step.signal_id = s.id;

-- Unique, so the view can later be refreshed CONCURRENTLY if reads during a refresh matter.
CREATE UNIQUE INDEX idx_call_sofia_signal_outcomes_id ON call_sofia_signal_outcomes (signal_id);
CREATE INDEX idx_call_sofia_signal_outcomes_submitted ON call_sofia_signal_outcomes (submitted_at);
