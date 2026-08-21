-- Spring Modulith's Event Publication Registry — the transactional outbox behind
-- @ApplicationModuleListener.
--
-- Before this table, NewAlertFanout was an async after-commit listener with no record
-- of itself: the alert committed, the push happened on another thread, and a crash in
-- between lost the fan-out with nothing left to show it had been owed.
--
-- With the registry, publishing writes one row per (event, listener) *inside* the
-- publishing transaction, and the row is completed only once the listener returns. An
-- incomplete row is therefore exactly "this listener still owes this event", and
-- spring.modulith.events.republish-outstanding-events-on-restart replays it on startup.
--
-- The table is owned by Flyway rather than by Hibernate: ddl-auto is `validate`
-- everywhere, and Modulith's own schema initialisation would be a second writer of the
-- schema. The columns below mirror JpaEventPublication's mapping, so `validate` passes.
CREATE TABLE event_publication
(
    id               UUID PRIMARY KEY,
    listener_id      TEXT      NOT NULL,
    event_type       TEXT      NOT NULL,
    serialized_event TEXT      NOT NULL,
    publication_date TIMESTAMP NOT NULL,
    -- NULL means outstanding: published, not yet handled.
    completion_date  TIMESTAMP
);

-- Completion looks a publication up by (listener_id, serialized_event) rather than by
-- id, because the listener knows the event it was handed and not the registry row. A
-- hash index is enough and much smaller than a b-tree over a payload-sized column.
CREATE INDEX idx_event_publication_serialized_event ON event_publication USING HASH (serialized_event);

-- Startup replay and any housekeeping scan for `completion_date IS NULL`.
CREATE INDEX idx_event_publication_completion_date ON event_publication (completion_date);
