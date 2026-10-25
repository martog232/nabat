package org.example.nabat.incident.domain;

/** What the checks on a resolved report add up to. See {@link FixCheckTally#outcome()}. */
public enum FixOutcome {
    /** Nobody has said. */
    UNCHECKED,
    /** Everyone who answered says it is fixed. */
    CONFIRMED,
    /** The reporter says it is not, or more people say not than say fixed. */
    DISPUTED,
    /** Anything in between — partly fixed, or opinions split without a majority against. */
    MIXED
}
