-- Report categories: Call Sofia's, in place of the seven nabat made up (V15).
--
-- Call Sofia files every report under one of twelve groups and a subcategory beneath it.
-- Using the same tree, with the same codes, means a nabat report and a Call Sofia report
-- of the same kind can be compared without a mapping table — and nabat's seven covered
-- under half of what people actually report there (abandoned cars, trees, pavements,
-- noise and playgrounds had nowhere to go).
--
-- Codes, names and group colours are Call Sofia's, read from its map on 2026-10-13. Two
-- of its subcategories are left out: „Информационни системи на Столична община“ is about
-- its own website, and „Предложения“ is not a report of a problem. „Организацията на
-- движението“ is written without its stray article. The hints are nabat's own: the
-- official names are administrative, and nobody standing over a pothole looks for
-- „Улично платно“.
--
-- A group has no group_code and carries the colour; a subcategory has a group and no
-- colour of its own. A report always names a subcategory.
CREATE TABLE report_categories (
    code       INTEGER      PRIMARY KEY,
    group_code INTEGER      REFERENCES report_categories (code),
    name       VARCHAR(120) NOT NULL,
    hint       VARCHAR(255),
    color      VARCHAR(7),
    sort_order INTEGER      NOT NULL,
    CONSTRAINT report_categories_group_check CHECK ((group_code IS NULL) = (color IS NOT NULL))
);

INSERT INTO report_categories (code, group_code, name, hint, color, sort_order) VALUES
    (1000, NULL, 'Чистота',                              NULL, '#16A085', 1),
    (1006, NULL, 'Пътища и шахти',                       NULL, '#1E3A8A', 2),
    (1012, NULL, 'Паркове, градини и площадки',          NULL, '#2E7D32', 3),
    (1018, NULL, 'Автомобили',                           NULL, '#06B6D4', 4),
    (1021, NULL, 'Градски транспорт',                    NULL, '#7C3AED', 5),
    (1027, NULL, 'Осветление и пътна сигнализация',      NULL, '#F59E0B', 6),
    (1032, NULL, 'Животни',                              NULL, '#14B8A6', 7),
    (1035, NULL, 'Търговска дейност',                    NULL, '#EC4899', 8),
    (1038, NULL, 'Сгради и строежи',                     NULL, '#84CC16', 9),
    (1042, NULL, 'Нарушаване на обществения ред и шум',  NULL, '#EAB308', 10),
    (1046, NULL, 'Подлези и мостове',                    NULL, '#D946EF', 11),
    (1049, NULL, 'Други',                                NULL, '#475569', 12);

INSERT INTO report_categories (code, group_code, name, hint, color, sort_order) VALUES
    (1001, 1000, 'Сметосъбиране и сметоизвозване', 'неизвозен боклук, разпилени отпадъци', NULL, 1),
    (1003, 1000, 'Съдове за отпадъци', 'препълнени, счупени или липсващи контейнери', NULL, 2),
    (1002, 1000, 'Едрогабаритни отпадъци', 'стари мебели, строителни отпадъци, уреди', NULL, 3),
    (1004, 1000, 'Метене/Чистене', 'неизметена улица, листа, кал', NULL, 4),
    (1005, 1000, 'Изгаряне на отпадъци', 'пушек, горене на гуми или боклук', NULL, 5),

    (1007, 1006, 'Улично платно', 'дупки, пропаднала настилка, шахти без капак', NULL, 1),
    (1008, 1006, 'Тротоар', 'счупени плочки и бордюри, дупки по тротоара', NULL, 2),
    (1009, 1006, 'Ограждения', 'паднали или липсващи парапети и огради', NULL, 3),
    (1010, 1006, 'Велоалеи', 'повредена или препречена велоалея', NULL, 4),
    (1011, 1006, 'Достъпност', 'липсващи рампи, препятствия за колички и хора с увреждания', NULL, 5),

    (1013, 1012, 'Дървета и растителност', 'паднал клон, сухо или опасно дърво', NULL, 1),
    (1017, 1012, 'Детски и спортни площадки', 'счупени уреди, опасна площадка', NULL, 2),
    (1014, 1012, 'Паркове и междублокови пространства', 'неподдържана градинка, счупени пейки', NULL, 3),
    (1016, 1012, 'Косене и пръскане', 'висока трева, комари, кърлежи', NULL, 4),
    (1015, 1012, 'Водоеми', 'замърсено езеро, неработещ фонтан', NULL, 5),

    (1019, 1018, 'Изоставени автомобили', 'кола без номера, на трупчета или от месеци на едно място', NULL, 1),
    (1020, 1018, 'Неправилно паркиране', 'на тротоар, на зебра, пред вход или гараж', NULL, 2),

    (1024, 1021, 'Спирки', 'счупен заслон, липсващо табло или пейка', NULL, 1),
    (1022, 1021, 'Лошо обслужване', 'неспазено разписание, отказан превоз', NULL, 2),
    (1023, 1021, 'Лошо състояние', 'повредено или мръсно превозно средство', NULL, 3),
    (1025, 1021, 'Достъпност', 'недостъпни превозни средства или спирки', NULL, 4),

    (1028, 1027, 'Улично осветление', 'не свети, мига или свети през деня', NULL, 1),
    (1029, 1027, 'Светофари', 'не работи или работи неправилно', NULL, 2),
    (1030, 1027, 'Пътни знаци', 'паднал, липсващ или закрит знак', NULL, 3),
    (1031, 1027, 'Организация на движението', 'опасно кръстовище, изтрита маркировка', NULL, 4),

    (1034, 1032, 'Безстопанствени', 'бездомни кучета и котки', NULL, 1),
    (1033, 1032, 'Домашни животни', 'животни без надзор, неприбрани нечистотии', NULL, 2),

    (1037, 1035, 'Търговска дейност на открито', 'сергии, заета улица или тротоар', NULL, 1),
    (1036, 1035, 'Рекламни елементи', 'незаконна реклама, разлепени плакати', NULL, 2),
    (1058, 1035, 'Преместваеми обекти (павилиони и други подобни)', 'незаконен или изоставен павилион', NULL, 3),

    (1039, 1038, 'Сгради', 'опасна фасада, падаща мазилка', NULL, 1),
    (1040, 1038, 'Строежи', 'строеж без ограда, кал и отпадъци по улицата', NULL, 2),

    (1057, 1042, 'Шум', 'шум нощем, ремонти извън позволените часове', NULL, 1),
    (1045, 1042, 'Нарушаване на обществения ред', 'вандализъм, графити', NULL, 2),

    (1047, 1046, 'Подлези', 'тъмен, мръсен или наводнен подлез', NULL, 1),
    (1048, 1046, 'Мостове', 'повреден мост или пасарелка', NULL, 2),

    (1059, 1049, 'Етажна собственост', 'общи части, входове, асансьори', NULL, 1),
    (1051, 1049, 'Други', 'нещо, което не намирате в списъка', NULL, 2);

-- Every existing report onto the new tree. POLLUTION has no single counterpart — Call
-- Sofia files smoke under waste and noise under public order — so the report's own words
-- decide, and what they cannot place goes to „Други“ rather than somewhere wrong.
ALTER TABLE alerts ADD COLUMN category_code INTEGER;

UPDATE alerts SET category_code = CASE type
    WHEN 'POTHOLE'         THEN 1007
    WHEN 'MANHOLE'         THEN 1007
    WHEN 'WASTE'           THEN 1001
    WHEN 'STREET_LIGHT'    THEN 1028
    WHEN 'ILLEGAL_PARKING' THEN 1020
    WHEN 'POLLUTION'       THEN CASE
        WHEN title || ' ' || description ~* '(шум|noise)' THEN 1057
        WHEN title || ' ' || description ~* '(изгаря|гори|пушек|дим|smoke|burn)' THEN 1005
        ELSE 1051
    END
    ELSE 1051
END;

ALTER TABLE alerts ALTER COLUMN category_code SET NOT NULL;

-- Same module, so a real foreign key — unlike district_code, whose table is jurisdiction's.
-- It cannot say "a subcategory, not a group"; CreateAlertService refuses group codes.
ALTER TABLE alerts ADD CONSTRAINT alerts_category_code_fkey
    FOREIGN KEY (category_code) REFERENCES report_categories (code);

CREATE INDEX idx_alerts_category_code ON alerts (category_code);

-- The CHECK V15 put on it goes with the column.
ALTER TABLE alerts DROP COLUMN type;
