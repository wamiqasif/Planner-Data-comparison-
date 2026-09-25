package com.vr.portfolioplanner.compare;

import java.util.Objects;

/**
 * Immutable record of one detected difference between API-1 and API-2 values.
 *
 * <h3>Core identity fields</h3>
 * <pre>
 *   testCaseId, mismatchType, fieldPath, api1Value, api2Value, message
 * </pre>
 *
 * <h3>Fund context (optional — populated for fund-level mismatches)</h3>
 * <pre>
 *   planId        — the matched plan_id (e.g. "19701")
 *   api1FundName  — fund name from API-1 (e.g. "Parag Parikh Flexi Cap Dir-G")
 *   api2FundName  — fund name from API-2 (e.g. "Parag Parikh Flexi Cap Direct-G")
 *                   "NOT FOUND" when the fund is absent in that API
 * </pre>
 *
 * <h3>Numeric context (optional — populated for amount/percentage mismatches)</h3>
 * <pre>
 *   difference  — absolute difference as plain string (e.g. "7000")
 *   tolerance   — configured tolerance used for comparison (e.g. "0.01")
 * </pre>
 *
 * <p>All optional fields default to {@code null}; existing callers that do not
 * set them are unaffected.
 */
public final class Mismatch {

    // Core
    private final String       testCaseId;
    private final String       fieldPath;
    private final String       api1Value;
    private final String       api2Value;
    private final MismatchType mismatchType;
    private final String       message;

    // Fund context (nullable)
    private final String planId;
    private final String api1FundName;
    private final String api2FundName;

    // Numeric context (nullable)
    private final String difference;
    private final String tolerance;

    private Mismatch(Builder b) {
        this.testCaseId   = b.testCaseId;
        this.fieldPath    = b.fieldPath;
        this.api1Value    = b.api1Value;
        this.api2Value    = b.api2Value;
        this.mismatchType = b.mismatchType;
        this.message      = b.message;
        this.planId       = b.planId;
        this.api1FundName = b.api1FundName;
        this.api2FundName = b.api2FundName;
        this.difference   = b.difference;
        this.tolerance    = b.tolerance;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String       getTestCaseId()   { return testCaseId; }
    public String       getFieldPath()    { return fieldPath; }
    public String       getApi1Value()    { return api1Value; }
    public String       getApi2Value()    { return api2Value; }
    public MismatchType getMismatchType() { return mismatchType; }
    public String       getMessage()      { return message; }

    /** The matched {@code plan_id}; {@code null} for non-fund mismatches. */
    public String getPlanId()       { return planId; }
    /** API-1 fund name for this plan; {@code null} for non-fund mismatches. */
    public String getApi1FundName() { return api1FundName; }
    /** API-2 fund name; {@code "NOT FOUND"} when the plan is absent in API-2. */
    public String getApi2FundName() { return api2FundName; }
    /** Absolute numeric difference as plain string; {@code null} for non-numeric mismatches. */
    public String getDifference()   { return difference; }
    /** Configured comparison tolerance; {@code null} for non-numeric mismatches. */
    public String getTolerance()    { return tolerance; }

    /** Delegates to {@link MismatchType#isFailing()}. */
    public boolean isFailing() { return mismatchType.isFailing(); }

    /** Whether fund context ({@link #planId}, {@link #api1FundName}) is populated. */
    public boolean hasFundContext() { return planId != null; }

    // -------------------------------------------------------------------------
    // Copy with fund context (non-destructive enrichment)
    // -------------------------------------------------------------------------

    /**
     * Returns a new {@link Mismatch} identical to this one but with the fund
     * context fields ({@code planId}, {@code api1FundName}, {@code api2FundName})
     * populated.  All other fields are preserved, including any existing
     * {@code difference} and {@code tolerance} values.
     */
    public Mismatch withFundContext(String planId, String api1FundName, String api2FundName) {
        return builder(this.testCaseId, this.mismatchType)
            .fieldPath(this.fieldPath)
            .api1Value(this.api1Value)
            .api2Value(this.api2Value)
            .message(this.message)
            .planId(planId)
            .api1FundName(api1FundName)
            .api2FundName(api2FundName)
            .difference(this.difference)
            .tolerance(this.tolerance)
            .build();
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s | field='%s' | api1='%s' api2='%s'",
            mismatchType, testCaseId, fieldPath, api1Value, api2Value));
        if (planId       != null) sb.append(" | planId=").append(planId);
        if (difference   != null) sb.append(" | diff=").append(difference);
        if (message      != null) sb.append(" | ").append(message);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mismatch other)) return false;
        return Objects.equals(testCaseId, other.testCaseId)
            && Objects.equals(fieldPath,  other.fieldPath)
            && mismatchType == other.mismatchType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(testCaseId, fieldPath, mismatchType);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder(String testCaseId, MismatchType type) {
        return new Builder(testCaseId, type);
    }

    public static final class Builder {
        private final String       testCaseId;
        private final MismatchType mismatchType;
        private String             fieldPath;
        private String             api1Value;
        private String             api2Value;
        private String             message;
        private String             planId;
        private String             api1FundName;
        private String             api2FundName;
        private String             difference;
        private String             tolerance;

        private Builder(String testCaseId, MismatchType mismatchType) {
            this.testCaseId   = Objects.requireNonNull(testCaseId,   "testCaseId");
            this.mismatchType = Objects.requireNonNull(mismatchType, "mismatchType");
        }

        public Builder fieldPath(String v)    { this.fieldPath    = v; return this; }
        public Builder api1Value(String v)    { this.api1Value    = v; return this; }
        public Builder api2Value(String v)    { this.api2Value    = v; return this; }
        public Builder message(String v)      { this.message      = v; return this; }
        public Builder planId(String v)       { this.planId       = v; return this; }
        public Builder api1FundName(String v) { this.api1FundName = v; return this; }
        public Builder api2FundName(String v) { this.api2FundName = v; return this; }
        public Builder difference(String v)   { this.difference   = v; return this; }
        public Builder tolerance(String v)    { this.tolerance    = v; return this; }

        public Mismatch build() { return new Mismatch(this); }
    }
}
