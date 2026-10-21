-- A report its reporter takes back.
--
-- The only way a reporter could close their own report was to mark it resolved — saying a
-- problem was fixed when it was a mistake, a duplicate they noticed, or nothing at all. That
-- put a false fix in the data and asked the city "is it really fixed?" about something that
-- was never broken. WITHDRAWN is final, like RESOLVED and REJECTED, closed by the reporter, and
-- counts for or against no authority.
ALTER TABLE alerts DROP CONSTRAINT alerts_status_check;
ALTER TABLE alerts
    ADD CONSTRAINT alerts_status_check
    CHECK (status IN ('SUBMITTED', 'SENT', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'WITHDRAWN'));
