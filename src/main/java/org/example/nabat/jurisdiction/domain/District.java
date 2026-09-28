package org.example.nabat.jurisdiction.domain;

/**
 * One of the 24 districts (rayoni) of Stolichna obshtina — the territory a district
 * administration is responsible for.
 *
 * <p>The boundary is not a component: nothing in Java reasons about the polygon, and the
 * only question asked of it, which district contains a point, is answered by the database
 * with an index behind it. Carrying ~500 KB of coordinates through every list call to
 * leave them unread would be the opposite of a domain model.
 *
 * @param code        stable identifier other data refers to, e.g. {@code LOZENETS}
 * @param name        how people call it, e.g. {@code Район Лозенец}
 * @param signalEmail where the district administration receives reports (V19 — placeholders
 *                    on {@code .test} until the real addresses are checked)
 */
public record District(String code, String name, String signalEmail) {
}
