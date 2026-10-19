package org.example.nabat.jurisdiction.domain;

/**
 * Whoever a report goes to and answers for it: a district administration, or a city-wide
 * {@link Institution}. One namespace for both, because a report, an official's account and a
 * delivery each name one without caring which kind it is.
 *
 * @param mailbox where its reports are mailed
 */
public record Authority(String code, String name, String mailbox, Kind kind) {

    public enum Kind {
        DISTRICT,
        INSTITUTION
    }

    public static Authority of(District district) {
        return new Authority(district.code(), district.name(), district.signalEmail(), Kind.DISTRICT);
    }

    public static Authority of(Institution institution) {
        return new Authority(institution.code(), institution.name(), institution.signalEmail(), Kind.INSTITUTION);
    }
}
