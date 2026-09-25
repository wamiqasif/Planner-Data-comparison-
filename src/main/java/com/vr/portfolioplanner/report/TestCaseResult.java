package com.vr.portfolioplanner.report;

import com.vr.portfolioplanner.altfund.AlternateFundAudit;
import com.vr.portfolioplanner.compare.Mismatch;

import java.util.Collections;
import java.util.List;

/**
 * Immutable data record for one test-case execution outcome.
 * Populated by {@link com.vr.portfolioplanner.test.ApiComparisonTest}
 * and consumed by {@link ExcelResultWriter}.
 *
 * <p>All fields have safe defaults so {@link Builder#build()} never throws,
 * even when an unexpected exception interrupts the pipeline early.
 */
public final class TestCaseResult {

    // ── Identification ──────────────────────────────────────────────────────
    private final String testCaseId;
    private final String description;
    /** "PASS", "FAIL", or "ERROR" (unexpected exception). */
    private final String executionStatus;

    // ── HTTP layer ───────────────────────────────────────────────────────────
    private final int  api1HttpStatus;
    private final int  api2HttpStatus;
    private final long api1ResponseTimeMs;
    private final long api2ResponseTimeMs;
    /** Wall-clock time the API-1 request was dispatched, formatted "yyyy-MM-dd HH:mm:ss.SSS". */
    private final String api1RequestTimestamp;
    /** Wall-clock time the API-2 request was dispatched, formatted "yyyy-MM-dd HH:mm:ss.SSS". */
    private final String api2RequestTimestamp;
    /** Exact outbound API-1 JSON request body. */
    private final String api1Payload;
    /** Exact outbound API-2 form-data fields, rendered as "key=value" lines. */
    private final String api2Payload;
    /** Raw API-1 response body. */
    private final String api1ResponseBody;
    /** Raw API-2 response body. */
    private final String api2ResponseBody;

    // ── Fund counts ──────────────────────────────────────────────────────────
    /** -1 when extraction failed. */
    private final int api1FundCount;
    /** -1 when extraction failed. */
    private final int api2FundCount;
    private final int matchedFundCount;
    private final int missingFundCount;
    private final int extraFundCount;

    // ── Comparison outcome ───────────────────────────────────────────────────
    /** "PASS", "FAIL", or "N/A" (could not compare). */
    private final String comparisonResult;
    private final int    mismatchCount;
    private final int    normalizationDiffCount;
    /** Aggregated text for the Excel Mismatch_Details cell (legacy). */
    private final String mismatchDetails;
    /** Per-mismatch objects for the Excel detail sheet. */
    private final List<Mismatch> mismatches;
    /** Alternate-fund validation audit rows for the "Alternate Fund Validation" / "Data Gaps" sheets. */
    private final List<AlternateFundAudit> alternateFundAudits;

    private TestCaseResult(Builder b) {
        this.testCaseId            = b.testCaseId;
        this.description           = b.description;
        this.executionStatus       = b.executionStatus;
        this.api1HttpStatus        = b.api1HttpStatus;
        this.api2HttpStatus        = b.api2HttpStatus;
        this.api1ResponseTimeMs    = b.api1ResponseTimeMs;
        this.api2ResponseTimeMs    = b.api2ResponseTimeMs;
        this.api1RequestTimestamp  = b.api1RequestTimestamp;
        this.api2RequestTimestamp  = b.api2RequestTimestamp;
        this.api1Payload           = b.api1Payload;
        this.api2Payload           = b.api2Payload;
        this.api1ResponseBody      = b.api1ResponseBody;
        this.api2ResponseBody      = b.api2ResponseBody;
        this.api1FundCount         = b.api1FundCount;
        this.api2FundCount         = b.api2FundCount;
        this.matchedFundCount      = b.matchedFundCount;
        this.missingFundCount      = b.missingFundCount;
        this.extraFundCount        = b.extraFundCount;
        this.comparisonResult      = b.comparisonResult;
        this.mismatchCount         = b.mismatchCount;
        this.normalizationDiffCount = b.normalizationDiffCount;
        this.mismatchDetails       = b.mismatchDetails;
        this.mismatches            = Collections.unmodifiableList(b.mismatches);
        this.alternateFundAudits   = Collections.unmodifiableList(b.alternateFundAudits);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String        getTestCaseId()            { return testCaseId; }
    public String        getDescription()           { return description; }
    public String        getExecutionStatus()       { return executionStatus; }
    public int           getApi1HttpStatus()        { return api1HttpStatus; }
    public int           getApi2HttpStatus()        { return api2HttpStatus; }
    public long          getApi1ResponseTimeMs()    { return api1ResponseTimeMs; }
    public long          getApi2ResponseTimeMs()    { return api2ResponseTimeMs; }
    public String        getApi1RequestTimestamp()  { return api1RequestTimestamp; }
    public String        getApi2RequestTimestamp()  { return api2RequestTimestamp; }
    public String        getApi1Payload()           { return api1Payload; }
    public String        getApi2Payload()           { return api2Payload; }
    public String        getApi1ResponseBody()      { return api1ResponseBody; }
    public String        getApi2ResponseBody()      { return api2ResponseBody; }
    public int           getApi1FundCount()         { return api1FundCount; }
    public int           getApi2FundCount()         { return api2FundCount; }
    public int           getMatchedFundCount()      { return matchedFundCount; }
    public int           getMissingFundCount()      { return missingFundCount; }
    public int           getExtraFundCount()        { return extraFundCount; }
    public String        getComparisonResult()      { return comparisonResult; }
    public int           getMismatchCount()         { return mismatchCount; }
    public int           getNormalizationDiffCount(){ return normalizationDiffCount; }
    public String        getMismatchDetails()       { return mismatchDetails; }
    public List<Mismatch> getMismatches()           { return mismatches; }
    public List<AlternateFundAudit> getAlternateFundAudits() { return alternateFundAudits; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String        testCaseId             = "";
        private String        description            = "";
        private String        executionStatus        = "UNKNOWN";
        private int           api1HttpStatus         = 0;
        private int           api2HttpStatus         = 0;
        private long          api1ResponseTimeMs     = 0L;
        private long          api2ResponseTimeMs     = 0L;
        private String        api1RequestTimestamp   = "";
        private String        api2RequestTimestamp   = "";
        private String        api1Payload            = "";
        private String        api2Payload            = "";
        private String        api1ResponseBody       = "";
        private String        api2ResponseBody       = "";
        private int           api1FundCount          = -1;
        private int           api2FundCount          = -1;
        private int           matchedFundCount       = 0;
        private int           missingFundCount       = 0;
        private int           extraFundCount         = 0;
        private String        comparisonResult       = "N/A";
        private int           mismatchCount          = 0;
        private int           normalizationDiffCount = 0;
        private String        mismatchDetails        = "";
        private List<Mismatch> mismatches            = Collections.emptyList();
        private List<AlternateFundAudit> alternateFundAudits = Collections.emptyList();

        public Builder testCaseId(String v)              { this.testCaseId             = v != null ? v : ""; return this; }
        public Builder description(String v)             { this.description            = v != null ? v : ""; return this; }
        public Builder executionStatus(String v)         { this.executionStatus        = v != null ? v : "UNKNOWN"; return this; }
        public Builder api1HttpStatus(int v)             { this.api1HttpStatus         = v; return this; }
        public Builder api2HttpStatus(int v)             { this.api2HttpStatus         = v; return this; }
        public Builder api1ResponseTimeMs(long v)        { this.api1ResponseTimeMs     = v; return this; }
        public Builder api2ResponseTimeMs(long v)        { this.api2ResponseTimeMs     = v; return this; }
        public Builder api1RequestTimestamp(String v)    { this.api1RequestTimestamp   = v != null ? v : ""; return this; }
        public Builder api2RequestTimestamp(String v)    { this.api2RequestTimestamp   = v != null ? v : ""; return this; }
        public Builder api1Payload(String v)             { this.api1Payload            = v != null ? v : ""; return this; }
        public Builder api2Payload(String v)             { this.api2Payload            = v != null ? v : ""; return this; }
        public Builder api1ResponseBody(String v)        { this.api1ResponseBody       = v != null ? v : ""; return this; }
        public Builder api2ResponseBody(String v)        { this.api2ResponseBody       = v != null ? v : ""; return this; }
        public Builder api1FundCount(int v)              { this.api1FundCount          = v; return this; }
        public Builder api2FundCount(int v)              { this.api2FundCount          = v; return this; }
        public Builder matchedFundCount(int v)           { this.matchedFundCount       = v; return this; }
        public Builder missingFundCount(int v)           { this.missingFundCount       = v; return this; }
        public Builder extraFundCount(int v)             { this.extraFundCount         = v; return this; }
        public Builder comparisonResult(String v)        { this.comparisonResult       = v != null ? v : "N/A"; return this; }
        public Builder mismatchCount(int v)              { this.mismatchCount          = v; return this; }
        public Builder normalizationDiffCount(int v)     { this.normalizationDiffCount = v; return this; }
        public Builder mismatchDetails(String v)         { this.mismatchDetails        = v != null ? v : ""; return this; }
        public Builder mismatches(List<Mismatch> v)      { this.mismatches             = v != null ? v : Collections.emptyList(); return this; }
        public Builder alternateFundAudits(List<AlternateFundAudit> v) { this.alternateFundAudits = v != null ? v : Collections.emptyList(); return this; }

        public TestCaseResult build() { return new TestCaseResult(this); }
    }
}
