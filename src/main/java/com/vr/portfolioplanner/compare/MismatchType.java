package com.vr.portfolioplanner.compare;

/**
 * Classification of a detected difference between API-1 and API-2 responses.
 *
 * <p>Only {@link #NORMALIZATION_DIFFERENCE} is treated as a WARNING (informational).
 * All other types are errors that contribute to a FAIL verdict.
 */
public enum MismatchType {

    /** HTTP status codes returned by the two APIs differ. */
    STATUS_MISMATCH,

    /** The number of funds or breakdown entries differs between the two APIs. */
    COUNT_MISMATCH,

    /** A fund or field is present in API-1 but absent in API-2. */
    MISSING_FIELD,

    /** A fund or field is present in API-2 but absent in API-1. */
    EXTRA_FIELD,

    /** String values differ (e.g. category name, status string). */
    VALUE_MISMATCH,

    /** Field types differ (e.g. one side returned null, the other a string). */
    TYPE_MISMATCH,

    /**
     * Numeric values differ beyond the configured tolerance.
     * Used for monetary amounts and percentages.
     */
    BUSINESS_VALUE_MISMATCH,

    /**
     * Raw values differ in format but are equal after applying the configured
     * normalization rules (e.g. "Dir-G" vs "Direct-G").
     * This is <em>informational only</em> — it does NOT cause a FAIL verdict.
     */
    NORMALIZATION_DIFFERENCE,

    /** An API call failed (non-2xx status, transport error, or empty body). */
    API_EXECUTION_ERROR,

    /**
     * An API-1 fund with a different {@code plan_id} than any API-2 fund was
     * uniquely paired with it (by {@code category_name}) and passed all
     * applicable alternate-fund business rules. Informational only — does NOT
     * cause a FAIL verdict. See {@code com.vr.portfolioplanner.altfund.AlternateFundValidator}.
     */
    ALTERNATE_FUND_MATCH,

    /**
     * An API-1 alternate fund was uniquely paired with an API-2 original but
     * failed at least one alternate-fund business rule.
     */
    ALTERNATE_FUND_REJECTED,

    /**
     * Multiple funds unmatched by {@code plan_id} shared the same
     * {@code category_name} on one or both sides — the pairing could not be
     * uniquely resolved and was not guessed.
     */
    ALTERNATE_FUND_PAIRING_AMBIGUOUS,

    /**
     * An alternate-fund pairing/rule decision could not be made because
     * required data (a live fetch, or reference data such as a prohibited
     * category list) is unavailable. Never treated as a PASS.
     */
    ALTERNATE_FUND_DATA_UNAVAILABLE;

    /**
     * Returns {@code true} when this type contributes to a FAIL verdict.
     * {@link #NORMALIZATION_DIFFERENCE} and {@link #ALTERNATE_FUND_MATCH} are
     * the only types that do not.
     */
    public boolean isFailing() {
        return this != NORMALIZATION_DIFFERENCE && this != ALTERNATE_FUND_MATCH;
    }
}
