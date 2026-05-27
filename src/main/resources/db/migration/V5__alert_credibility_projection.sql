ALTER TABLE alerts
    ADD COLUMN credibility_score INTEGER NOT NULL DEFAULT 0;

UPDATE alerts
SET credibility_score = upvote_count - downvote_count + (confirmation_count * 2);

