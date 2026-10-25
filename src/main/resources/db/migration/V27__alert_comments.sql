-- Comments on a report: the people who reported it, confirmed it or walk past it, and the
-- district that answers for it, talking about it in one place.
--
-- Open only while the report is. Once it is RESOLVED or REJECTED — both final — no comment
-- is added, and the thread stays as it was, readable. What is left to say about a resolved
-- report is whether it really is fixed, and fix_checks (V24) stays open for that.
--
-- author_name is the display name as it was when the comment was written, kept on the row:
-- users belongs to identity, and a thread should not need a join across modules to say who
-- spoke. A later change of name is not carried back.
--
-- by_district and by_reporter are kept on the row for the same reason as fix_checks keeps
-- by_reporter: they describe the author's standing on this report when they wrote, and that
-- is what the thread shows. An official moved to another district afterwards still spoke for
-- this one.
--
-- A removed comment keeps its row and loses its place in the conversation only as text: the
-- thread shows that something was said and taken down, and by whom — its author, or a
-- moderator — so the replies after it still read as replies.
-- author_id is not a foreign key: users belongs to identity.
CREATE TABLE alert_comments (
    id          UUID         PRIMARY KEY,
    alert_id    UUID         NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    author_id   UUID         NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    by_district BOOLEAN      NOT NULL,
    by_reporter BOOLEAN      NOT NULL,
    body        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    removed_at  TIMESTAMPTZ,
    removed_by  VARCHAR(16),
    CONSTRAINT alert_comments_body_check CHECK (char_length(body) BETWEEN 1 AND 2000),
    CONSTRAINT alert_comments_removed_by_check CHECK (removed_by IN ('AUTHOR', 'MODERATOR')),
    CONSTRAINT alert_comments_removed_check CHECK ((removed_at IS NULL) = (removed_by IS NULL))
);

CREATE INDEX idx_alert_comments_thread ON alert_comments (alert_id, created_at);
