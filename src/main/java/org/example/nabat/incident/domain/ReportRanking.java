package org.example.nabat.incident.domain;

/**
 * How the public list of reports is ordered.
 *
 * <p>Three orders, because they answer three different questions, and a single one would
 * quietly pick which question the page is for.
 */
public enum ReportRanking {

    /**
     * What matters now: support, weighed down by age.
     *
     * <pre>
     *   score = (support + 1) / (ageInDays + 2) ^ 1.5
     *   support = confirmations + upvotes
     * </pre>
     *
     * <p>Ranking by raw support would put the oldest reports on top for good — they have had
     * the longest to collect it — and a serious new problem could never climb past them. The
     * divisor gives a new report a chance; the exponent decides how fast an old one sinks.
     * At 1.5 a report confirmed by ten people a week ago still outranks a new one nobody has
     * confirmed, and falls behind one confirmed by three people yesterday. The {@code + 1}
     * orders reports without any support by age instead of tying them all at zero; the
     * {@code + 2} keeps the first hours from dividing by almost nothing.
     *
     * <p>Support is the votes nabat-voting counts, not its credibility score: this is a
     * formula a reader of the page can check, and that one belongs to another service.
     *
     * <p>Support is the people who say they see it. A report is seen, not voted on: since the
     * public API takes only a confirmation, the upvotes here are older ones, which meant much
     * the same and still count; downvotes no longer subtract — a report that is not real is
     * for its authority or a moderator to reject, with a reason, not for a tally to sink.
     */
    HOT,

    /**
     * Longest open first. The accountability view — which reports a district has left
     * waiting — and the reason the page exists at all. Age is exactly what {@link #HOT}
     * discounts, so this cannot be a tie-breaker of it; it has to be its own order.
     */
    LONGEST_WAITING,

    /** Newest first. */
    NEWEST
}
