-- Stolichna obshtina — the municipality itself — as an authority.
--
-- Two uses. A report its authority leaves unanswered past the deadline and the reminder is
-- escalated to the municipality, which the districts answer to. And a report can be forwarded
-- to it: a pothole on a boulevard, a manhole of the main network, are the municipality's, not
-- the district's. No category goes to it by routing — whether a street is a boulevard is not
-- something the category can tell (see V29).
--
-- A PLACEHOLDER address on .test, like every other authority's (V19, V29). Owned by
-- jurisdiction.
INSERT INTO institutions (code, name, signal_email) VALUES
    ('STOLICHNA_OBSHTINA', 'Столична община', 'obshtina@institution.nabat.test');
