package com.vr.portfolioplanner.normalize;

/**
 * Declares the comparison eligibility of a field pair across API-1 and API-2.
 */
public enum ComparabilityStatus {

    /** The field exists in both APIs, can be compared directly or after normalization. */
    COMPARABLE,

    /**
     * The field exists in one or both APIs but there is no reliable structural or
     * semantic equivalent in the other.  Must not be included in automated comparison.
     */
    NOT_COMPARABLE,

    /**
     * The field exists but its value is generated (technical ID, session token,
     * display-only HTML) and must never be compared unless explicitly configured.
     */
    EXCLUDED,

    /**
     * A tentative mapping has been identified but requires domain-expert confirmation
     * before it can be asserted in automated tests.
     */
    MAPPING_REQUIRED
}
