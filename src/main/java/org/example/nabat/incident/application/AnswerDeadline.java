package org.example.nabat.incident.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * How long an authority has to answer a report it has been given: 30 days by default, as
 * Call Sofia gives an administration, set by {@code NABAT_ANSWER_WITHIN}.
 *
 * <p>A report is given its deadline as an instant when it reaches the authority, and keeps it
 * if this changes later.
 */
@Component
public class AnswerDeadline {

    private final Duration within;

    public AnswerDeadline(@Value("${nabat.reports.answer-within:P30D}") Duration within) {
        if (within.isNegative() || within.isZero()) {
            throw new IllegalArgumentException("nabat.reports.answer-within must be positive, not " + within);
        }
        this.within = within;
    }

    /** The deadline of a report reaching its authority now. */
    public Instant fromNow() {
        return Instant.now().plus(within);
    }
}
