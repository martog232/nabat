package org.example.nabat.callsofia.domain;

/**
 * How one district handled the Call Sofia reports filed in it over a period.
 *
 * <p>"Answered" is the first time the citizen heard anything final — a reply, a closure or
 * a rejection — not that anything was fixed. Call Sofia's commonest end is a reply alone.
 *
 * @param answered            reports that got any final answer
 * @param medianDaysToAnswer  over answered reports only; null if none were
 * @param p90DaysToAnswer     the slow end: nine in ten answered reports were answered sooner
 * @param dueIn30Days         reports filed at least 30 days before the archive ends — the
 *                            only ones the 30-day deadline can be judged on
 * @param answeredIn30Days    of those, answered within 30 days
 * @param stillOpen           no final answer by the time the archive was taken
 */
public record DistrictOutcomes(
    String districtCode,
    int signals,
    int answered,
    Double medianDaysToAnswer,
    Double p90DaysToAnswer,
    int dueIn30Days,
    int answeredIn30Days,
    int closed,
    int answeredOnly,
    int rejected,
    int stillOpen
) {
}
