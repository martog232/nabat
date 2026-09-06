-- Spring Modulith 2 widened the Event Publication Registry, and `ddl-auto=validate` makes
-- that a hard failure rather than a warning: the context refuses to start with
-- "missing column [completion_attempts] in table [event_publication]", which surfaces as
-- every @SpringBootTest in the suite failing to load a context.
--
-- Three columns, all on JpaEventPublication:
--   status                  — @Enumerated(STRING), one of PUBLISHED, PROCESSING, COMPLETED,
--                             FAILED, RESUBMITTED. In 1.x the state was implicit in whether
--                             completion_date was null; it is now explicit, which is what
--                             lets a failed publication be told apart from an outstanding one.
--   completion_attempts     — how many times delivery has been tried. `int`, so not null.
--   last_resubmission_date  — when it was last replayed, null until it is.
--
-- The backfill preserves the old meaning exactly: a row with no completion_date was
-- outstanding and is now PUBLISHED; a row with one had been handled and is COMPLETED.
-- Without it every pre-existing row would carry a null status and the registry would not
-- know what to do with rows it is meant to replay on startup.
ALTER TABLE event_publication
    ADD COLUMN status                 VARCHAR(255),
    ADD COLUMN completion_attempts    INTEGER   NOT NULL DEFAULT 0,
    ADD COLUMN last_resubmission_date TIMESTAMP;

UPDATE event_publication
SET status = CASE WHEN completion_date IS NULL THEN 'PUBLISHED' ELSE 'COMPLETED' END;

-- Startup replay selects the outstanding rows. It asked for `completion_date IS NULL` in
-- 1.x and asks by status now, so the index that served the old question is re-pointed at
-- the new one rather than left indexing a column nothing filters on any more.
DROP INDEX IF EXISTS idx_event_publication_completion_date;
CREATE INDEX idx_event_publication_status ON event_publication (status);
