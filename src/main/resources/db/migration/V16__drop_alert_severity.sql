-- Severity described how dangerous an incident was, and it chose how far a new alert was
-- pushed. For a report about a pothole or a dump it is a question the reporter cannot answer
-- well and the institution does not need: how much a problem matters is how many people
-- confirm it, which the voting service already measures.
--
-- Dropped rather than left nullable. A column the application no longer writes is one a
-- later reader takes for live data, and the values in it describe a product nabat no
-- longer is.
ALTER TABLE alerts DROP COLUMN severity;
