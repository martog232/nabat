package org.example.nabat.incident.domain;

/**
 * The answers to "is it really fixed?" on one report.
 *
 * @param reporterVerdict what the person who reported it says, or null if they have not
 *                        answered. Counted in the totals as well.
 */
public record FixCheckTally(int fixed, int partly, int notFixed, FixVerdict reporterVerdict) {

    public static final FixCheckTally NONE = new FixCheckTally(0, 0, 0, null);

    public FixCheckTally {
        if (fixed < 0 || partly < 0 || notFixed < 0) {
            throw new IllegalArgumentException("Counts cannot be negative");
        }
    }

    public int total() {
        return fixed + partly + notFixed;
    }

    /**
     * The rule, in order:
     * <ol>
     *   <li>no answers — {@link FixOutcome#UNCHECKED};</li>
     *   <li>the reporter says it is not fixed, or more say not than say fixed —
     *       {@link FixOutcome#DISPUTED}. The reporter's "no" counts alone: they know what
     *       they reported, and a district closing a report its own reporter says is still
     *       there is exactly what this exists to show;</li>
     *   <li>every answer is "fixed" — {@link FixOutcome#CONFIRMED};</li>
     *   <li>otherwise {@link FixOutcome#MIXED}.</li>
     * </ol>
     * A reporter's "fixed" does not outweigh others' "not fixed" in the same way: it is the
     * sceptical answer that needs protecting from being outvoted, not the satisfied one.
     */
    public FixOutcome outcome() {
        if (total() == 0) {
            return FixOutcome.UNCHECKED;
        }
        if (reporterVerdict == FixVerdict.NOT_FIXED || notFixed > fixed) {
            return FixOutcome.DISPUTED;
        }
        if (partly == 0 && notFixed == 0) {
            return FixOutcome.CONFIRMED;
        }
        return FixOutcome.MIXED;
    }
}
