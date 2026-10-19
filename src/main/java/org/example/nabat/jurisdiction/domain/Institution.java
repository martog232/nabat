package org.example.nabat.jurisdiction.domain;

/**
 * A city-wide body that answers for some categories of report wherever in Sofia they are:
 * the Centre for Urban Mobility for public transport and traffic lights, for instance (V29).
 *
 * @param code        stable identifier, never equal to a district's, e.g. {@code CGM}
 * @param name        how people call it
 * @param signalEmail where it receives reports (a placeholder on {@code .test}, like the
 *                    districts')
 */
public record Institution(String code, String name, String signalEmail) {
}
