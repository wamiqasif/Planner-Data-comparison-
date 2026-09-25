package com.vr.portfolioplanner.altfund;

/**
 * One full audit row for a fund slot that was not resolved by a direct
 * {@code plan_id} match — produced by {@link AlternateFundValidator} and
 * consumed by the "Alternate Fund Validation" / "Data Gaps" report sheets
 * ({@code ExcelResultWriter}) and the Extent report
 * ({@code ComparisonReportFormatter}).
 */
public final class AlternateFundAudit {

    private final String     testCaseId;
    private final String     api2PlanId;
    private final String     api2FundName;
    private final String     api1PlanId;
    private final String     api1FundName;
    private final MatchType  matchType;
    private final String     api1CategoryName;
    private final Integer    api1OpinionTypeId;
    private final Boolean    api1IsAnalystPick;
    private final Integer    api1VrRating;
    private final String     api1TagName;
    private final RuleResult rule1;
    private final RuleResult rule2;
    private final RuleResult rule3;
    private final RuleResult rule4;
    private final RuleResult rule5;
    private final String     failedCondition;
    private final String     finalResult;

    private AlternateFundAudit(Builder b) {
        this.testCaseId        = b.testCaseId;
        this.api2PlanId        = b.api2PlanId;
        this.api2FundName      = b.api2FundName;
        this.api1PlanId        = b.api1PlanId;
        this.api1FundName      = b.api1FundName;
        this.matchType         = b.matchType;
        this.api1CategoryName  = b.api1CategoryName;
        this.api1OpinionTypeId = b.api1OpinionTypeId;
        this.api1IsAnalystPick = b.api1IsAnalystPick;
        this.api1VrRating      = b.api1VrRating;
        this.api1TagName       = b.api1TagName;
        this.rule1              = b.rule1;
        this.rule2              = b.rule2;
        this.rule3              = b.rule3;
        this.rule4              = b.rule4;
        this.rule5              = b.rule5;
        this.failedCondition   = b.failedCondition;
        this.finalResult       = b.finalResult;
    }

    public String     getTestCaseId()        { return testCaseId; }
    public String     getApi2PlanId()        { return api2PlanId; }
    public String     getApi2FundName()      { return api2FundName; }
    public String     getApi1PlanId()        { return api1PlanId; }
    public String     getApi1FundName()      { return api1FundName; }
    public MatchType  getMatchType()         { return matchType; }
    public String     getApi1CategoryName()  { return api1CategoryName; }
    public Integer    getApi1OpinionTypeId() { return api1OpinionTypeId; }
    public Boolean    getApi1IsAnalystPick() { return api1IsAnalystPick; }
    public Integer    getApi1VrRating()      { return api1VrRating; }
    public String     getApi1TagName()       { return api1TagName; }
    public RuleResult getRule1()             { return rule1; }
    public RuleResult getRule2()             { return rule2; }
    public RuleResult getRule3()             { return rule3; }
    public RuleResult getRule4()             { return rule4; }
    public RuleResult getRule5()             { return rule5; }
    public String     getFailedCondition()   { return failedCondition; }
    public String     getFinalResult()       { return finalResult; }

    /** Whether this row belongs in the "Data Gaps" report. */
    public boolean isDataGap() {
        return matchType == MatchType.REQUIRED_DATA_NOT_AVAILABLE || matchType == MatchType.PAIRING_AMBIGUOUS;
    }

    @Override
    public String toString() {
        return String.format("AlternateFundAudit{testCase='%s', api2=%s, api1=%s, matchType=%s, final=%s}",
            testCaseId, api2PlanId, api1PlanId, matchType, finalResult);
    }

    public static Builder builder(String testCaseId) { return new Builder(testCaseId); }

    public static final class Builder {
        private final String testCaseId;
        private String     api2PlanId   = "";
        private String     api2FundName = "";
        private String     api1PlanId   = "";
        private String     api1FundName = "";
        private MatchType  matchType;
        private String     api1CategoryName;
        private Integer    api1OpinionTypeId;
        private Boolean    api1IsAnalystPick;
        private Integer    api1VrRating;
        private String     api1TagName;
        private RuleResult rule1;
        private RuleResult rule2;
        private RuleResult rule3;
        private RuleResult rule4;
        private RuleResult rule5;
        private String     failedCondition = "";
        private String     finalResult;

        private Builder(String testCaseId) { this.testCaseId = testCaseId; }

        public Builder api2Plan(String planId, String fundName) {
            this.api2PlanId = planId != null ? planId : ""; this.api2FundName = fundName != null ? fundName : ""; return this;
        }
        public Builder api1Plan(String planId, String fundName) {
            this.api1PlanId = planId != null ? planId : ""; this.api1FundName = fundName != null ? fundName : ""; return this;
        }
        public Builder matchType(MatchType v)        { this.matchType = v; return this; }
        public Builder api1CategoryName(String v)    { this.api1CategoryName = v; return this; }
        public Builder api1Opinion(Integer opinionTypeId, Boolean isAnalystPick, Integer vrRating, String tagName) {
            this.api1OpinionTypeId = opinionTypeId;
            this.api1IsAnalystPick = isAnalystPick;
            this.api1VrRating      = vrRating;
            this.api1TagName       = tagName;
            return this;
        }
        public Builder rules(RuleResult r1, RuleResult r2, RuleResult r3, RuleResult r4, RuleResult r5) {
            this.rule1 = r1; this.rule2 = r2; this.rule3 = r3; this.rule4 = r4; this.rule5 = r5; return this;
        }
        public Builder failedCondition(String v) { this.failedCondition = v != null ? v : ""; return this; }
        public Builder finalResult(String v)     { this.finalResult = v; return this; }

        public AlternateFundAudit build() { return new AlternateFundAudit(this); }
    }
}
