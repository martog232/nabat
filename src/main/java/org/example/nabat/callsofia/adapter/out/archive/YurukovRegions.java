package org.example.nabat.callsofia.adapter.out.archive;

import java.util.Map;

/**
 * The archive's district numbers, as codes from {@code districts}.
 *
 * <p>By number, not by name. The archive's names are not ours and not always right —
 * „Панчерево“ and „Върбица“ are typos for Панчарево and Връбница, „Нови искър“ differs in
 * case — so matching on them would quietly lose whole districts. The numbers are stable
 * and there are 24 of them. 0 is the archive's „Неизвестно“.
 */
final class YurukovRegions {

    private static final Map<Integer, String> DISTRICT_CODES = Map.ofEntries(
        Map.entry(1, "SREDETS"),
        Map.entry(2, "KRASNO_SELO"),
        Map.entry(3, "VAZRAZHDANE"),
        Map.entry(4, "OBORISHTE"),
        Map.entry(5, "SERDIKA"),
        Map.entry(6, "PODUYANE"),
        Map.entry(7, "SLATINA"),
        Map.entry(8, "IZGREV"),
        Map.entry(9, "LOZENETS"),
        Map.entry(10, "TRIADITSA"),
        Map.entry(11, "KRASNA_POLYANA"),
        Map.entry(12, "ILINDEN"),
        Map.entry(13, "NADEZHDA"),
        Map.entry(14, "ISKAR"),
        Map.entry(15, "MLADOST"),
        Map.entry(16, "STUDENTSKI"),
        Map.entry(17, "VITOSHA"),
        Map.entry(18, "OVCHA_KUPEL"),
        Map.entry(19, "LYULIN"),
        Map.entry(20, "VRABNITSA"),
        Map.entry(21, "NOVI_ISKAR"),
        Map.entry(22, "KREMIKOVTSI"),
        Map.entry(23, "PANCHAREVO"),
        Map.entry(24, "BANKYA")
    );

    private YurukovRegions() {
    }

    /** The district for an archive number, or null for 0 and anything unknown. */
    static String districtCode(int region) {
        return DISTRICT_CODES.get(region);
    }

    static Map<Integer, String> all() {
        return DISTRICT_CODES;
    }
}
