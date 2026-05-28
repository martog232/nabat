ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS credibility_score INTEGER NOT NULL DEFAULT 0;

UPDATE alerts
SET credibility_score = upvote_count - downvote_count + (confirmation_count * 2)
WHERE credibility_score IS DISTINCT FROM (upvote_count - downvote_count + (confirmation_count * 2));

