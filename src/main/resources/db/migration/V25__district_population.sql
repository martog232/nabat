-- How many people live in each district, so districts of very different sizes can be
-- compared per resident: Lyulin has ten times Bankya's population, and a raw count of
-- reports says more about that than about either administration.
--
-- Source: the population_estimate column of call.sofia.dump.regions.csv in Boyan
-- Yurukov's Call Sofia archive (https://opendata.yurukov.net/callsofia/data/, CC BY-SA 2.5
-- BG), published 2022-03-31. An estimate of that time, not a census: good for a ratio,
-- not for quoting as the number of residents. Replace in a new migration if a better
-- source is used.
ALTER TABLE districts ADD COLUMN population INTEGER;

UPDATE districts d SET population = p.population
FROM (VALUES
    ('SREDETS',         30175),
    ('KRASNO_SELO',     85792),
    ('VAZRAZHDANE',     38634),
    ('OBORISHTE',       31076),
    ('SERDIKA',         43497),
    ('PODUYANE',        77705),
    ('SLATINA',         69055),
    ('IZGREV',          31473),
    ('LOZENETS',        53110),
    ('TRIADITSA',       68936),
    ('KRASNA_POLYANA',  54985),
    ('ILINDEN',         37754),
    ('NADEZHDA',        71707),
    ('ISKAR',           64814),
    ('MLADOST',        110025),
    ('STUDENTSKI',     104004),
    ('VITOSHA',         66563),
    ('OVCHA_KUPEL',     63871),
    ('LYULIN',         116036),
    ('VRABNITSA',       52667),
    ('NOVI_ISKAR',      26688),
    ('KREMIKOVTSI',     23772),
    ('PANCHAREVO',      26925),
    ('BANKYA',          11548)
) AS p (code, population)
WHERE d.code = p.code;

ALTER TABLE districts ALTER COLUMN population SET NOT NULL;
