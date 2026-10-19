-- City-wide institutions, and the categories that go to them instead of to the district.
--
-- A district administration does not answer for everything in its territory. Public
-- transport and the traffic lights are the Centre for Urban Mobility's, stray animals are
-- Ekoravnovesie's, street trade and advertising the Stolichen Inspektorat's. Sent to the
-- district, such a report is forwarded or left, and nabat says SENT either way.
--
-- Only categories whose institution follows from the category alone are routed. Most do
-- not: a pothole on a boulevard is the municipality's and on a side street the district's,
-- a manhole is Sofiyska Voda's or Toplofikatsia's or a telecom's, night noise is the
-- police's and a bar's noise the Inspektorat's. Those stay with the district, which knows
-- its streets. The assignments below are a best understanding, not checked against an
-- official source; a correction is a new migration.
--
-- Owned by jurisdiction. category_authorities.category_code is not a foreign key to
-- report_categories, which belongs to incident; AuthorityRoutingIntegrationTest holds
-- every routed code to a real subcategory.

-- The addresses are PLACEHOLDERS on the reserved .test domain, for the reason V19 gives
-- for the districts': this project must not mail real officials.
CREATE TABLE institutions (
    code         VARCHAR(32)  PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    signal_email VARCHAR(254) NOT NULL
);

INSERT INTO institutions (code, name, signal_email) VALUES
    ('CGM',                   'Център за градска мобилност', 'cgm@institution.nabat.test'),
    ('EKORAVNOVESIE',         'ОП „Екоравновесие“',          'ekoravnovesie@institution.nabat.test'),
    ('STOLICHEN_INSPEKTORAT', 'Столичен инспекторат',        'inspektorat@institution.nabat.test');

-- A category with no row here goes to the district the report is in.
CREATE TABLE category_authorities (
    category_code    INTEGER     PRIMARY KEY,
    institution_code VARCHAR(32) NOT NULL REFERENCES institutions (code)
);

INSERT INTO category_authorities (category_code, institution_code) VALUES
    -- Градски транспорт: спирки, лошо обслужване, лошо състояние, достъпност
    (1024, 'CGM'), (1022, 'CGM'), (1023, 'CGM'), (1025, 'CGM'),
    -- Светофари, пътни знаци, организация на движението
    (1029, 'CGM'), (1030, 'CGM'), (1031, 'CGM'),
    -- Безстопанствени животни
    (1034, 'EKORAVNOVESIE'),
    -- Търговия на открито, рекламни елементи, преместваеми обекти, изгаряне на отпадъци
    (1037, 'STOLICHEN_INSPEKTORAT'), (1036, 'STOLICHEN_INSPEKTORAT'),
    (1058, 'STOLICHEN_INSPEKTORAT'), (1005, 'STOLICHEN_INSPEKTORAT');

-- Whom a report was delivered to. Until now always its district; district_code stays where
-- the report is.
ALTER TABLE signal_dispatches ADD COLUMN authority_code VARCHAR(32);
UPDATE signal_dispatches SET authority_code = district_code;
ALTER TABLE signal_dispatches ALTER COLUMN authority_code SET NOT NULL;
