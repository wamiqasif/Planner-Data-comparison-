package com.vr.portfolioplanner.altfund;

/**
 * Outcome of evaluating one of the five alternate-fund business rules in
 * {@link AlternateFundValidator} against a single API-1 alternate candidate.
 */
public enum RuleOutcome {

    /** The rule's condition was evaluated and the fund satisfies it. */
    PASS,

    /** The rule's condition was evaluated and the fund violates it. */
    FAIL,

    /** The rule does not apply to this fund (its precondition isn't met). */
    NOT_APPLICABLE,

    /**
     * The rule could not be evaluated because required reference data (a
     * prohibited-category list, an index-fund slot policy, a watchlist tag
     * value, etc.) is not yet configured, or a live data fetch failed.
     * Never treated as PASS or FAIL.
     */
    REQUIRED_DATA_NOT_AVAILABLE
}
