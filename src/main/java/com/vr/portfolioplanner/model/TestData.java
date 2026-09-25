package com.vr.portfolioplanner.model;

/**
 * Immutable-ish POJO representing one row from the test-data Excel sheet.
 * Fields are set by {@link com.vr.portfolioplanner.excel.ExcelReader} via
 * package-private setters; callers treat instances as read-only.
 *
 * Type choices:
 *   long  – monetary amounts and all entity IDs (future-proof against large values)
 *   int   – small enumerable parameters (duration, risk profile, income range, year)
 *   String – text / enum-like fields
 */
public final class TestData {

    // ------------------------------------------------------------------
    // Identification
    // ------------------------------------------------------------------
    private String testCaseId;
    private String execute;
    private String description;

    // ------------------------------------------------------------------
    // Common request fields (shared by both APIs)
    // ------------------------------------------------------------------
    private String goalType;
    private String txnOptions;
    private String durationType;
    private int    investmentDuration;
    private long   monthlyAmount;
    private long   lumpsumAmount;
    private int    riskProfileId;
    private int    annualIncomeRange;

    // ------------------------------------------------------------------
    // Goal-specific amount fields (used instead of monthly/lumpsum amount
    // when goal_type is REGULAR-INCOME or TAX-SAVINGS — see payload builders)
    // ------------------------------------------------------------------
    private long   accumulatedAmount;
    private long   taxSavingAmount;
    private long   neededAnnualAmount; // only populated when goal_type is REGULAR-INCOME
    private long   startRegularIncome; // only populated when goal_type is REGULAR-INCOME

    // ------------------------------------------------------------------
    // API 1 – specific fields
    // ------------------------------------------------------------------
    private long   api1LabelId;
    private long   api1UserId;
    private String api1AwareType;
    private long   api1InvestorId;

    // ------------------------------------------------------------------
    // API 2 – specific fields
    // ------------------------------------------------------------------
    private long api2LabelId;
    private long api2UserId;
    private long api2InvestorId;
    private int  api2NextFinancialYear;

    // ------------------------------------------------------------------
    // Business method
    // ------------------------------------------------------------------

    /** Returns true only when the Execute column is exactly "Y" (case-insensitive). */
    public boolean isExecutable() {
        return "Y".equalsIgnoreCase(execute);
    }

    // ------------------------------------------------------------------
    // Display helpers
    // ------------------------------------------------------------------

    /**
     * Returns a log-safe representation that omits user-identity fields
     * (api*_user_id, api*_investor_id) which may map to real accounts.
     */
    public String toLoggableString() {
        return String.format(
            "[%s | execute=%s | %s] " +
            "goal=%s txnOpts=%s durationType=%s duration=%dy " +
            "monthly=%,d lumpsum=%,d accumulated=%,d taxSaving=%,d neededAnnual=%,d startRegularIncome=%,d risk=%d income=%d | " +
            "api1[label=%d awareType=%s] " +
            "api2[label=%d year=%d]",
            testCaseId, execute, description,
            goalType, txnOptions, durationType, investmentDuration,
            monthlyAmount, lumpsumAmount, accumulatedAmount, taxSavingAmount, neededAnnualAmount, startRegularIncome,
            riskProfileId, annualIncomeRange,
            api1LabelId, api1AwareType,
            api2LabelId, api2NextFinancialYear
        );
    }

    @Override
    public String toString() {
        return String.format(
            "TestData{id='%s', execute='%s', desc='%s', " +
            "goalType='%s', txnOptions='%s', durationType='%s', duration=%d, " +
            "monthly=%d, lumpsum=%d, accumulated=%d, taxSaving=%d, neededAnnual=%d, startRegularIncome=%d, risk=%d, income=%d, " +
            "api1[label=%d userId=%d aware='%s' investor=%d], " +
            "api2[label=%d userId=%d investor=%d year=%d]}",
            testCaseId, execute, description,
            goalType, txnOptions, durationType, investmentDuration,
            monthlyAmount, lumpsumAmount, accumulatedAmount, taxSavingAmount, neededAnnualAmount, startRegularIncome,
            riskProfileId, annualIncomeRange,
            api1LabelId, api1UserId, api1AwareType, api1InvestorId,
            api2LabelId, api2UserId, api2InvestorId, api2NextFinancialYear
        );
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public String getTestCaseId()          { return testCaseId; }
    public String getExecute()             { return execute; }
    public String getDescription()         { return description; }
    public String getGoalType()            { return goalType; }
    public String getTxnOptions()          { return txnOptions; }
    public String getDurationType()        { return durationType; }
    public int    getInvestmentDuration()  { return investmentDuration; }
    public long   getMonthlyAmount()       { return monthlyAmount; }
    public long   getLumpsumAmount()       { return lumpsumAmount; }
    public long   getAccumulatedAmount()   { return accumulatedAmount; }
    public long   getTaxSavingAmount()     { return taxSavingAmount; }
    public long   getNeededAnnualAmount()  { return neededAnnualAmount; }
    public long   getStartRegularIncome()  { return startRegularIncome; }
    public int    getRiskProfileId()       { return riskProfileId; }
    public int    getAnnualIncomeRange()   { return annualIncomeRange; }
    public long   getApi1LabelId()         { return api1LabelId; }
    public long   getApi1UserId()          { return api1UserId; }
    public String getApi1AwareType()       { return api1AwareType; }
    public long   getApi1InvestorId()      { return api1InvestorId; }
    public long   getApi2LabelId()         { return api2LabelId; }
    public long   getApi2UserId()          { return api2UserId; }
    public long   getApi2InvestorId()      { return api2InvestorId; }
    public int    getApi2NextFinancialYear(){ return api2NextFinancialYear; }

    // ------------------------------------------------------------------
    // Setters (public: ExcelReader lives in a different package)
    // ------------------------------------------------------------------

    public void setTestCaseId(String v)           { this.testCaseId = v; }
    public void setExecute(String v)              { this.execute = v; }
    public void setDescription(String v)          { this.description = v; }
    public void setGoalType(String v)             { this.goalType = v; }
    public void setTxnOptions(String v)           { this.txnOptions = v; }
    public void setDurationType(String v)         { this.durationType = v; }
    public void setInvestmentDuration(int v)      { this.investmentDuration = v; }
    public void setMonthlyAmount(long v)          { this.monthlyAmount = v; }
    public void setLumpsumAmount(long v)          { this.lumpsumAmount = v; }
    public void setAccumulatedAmount(long v)      { this.accumulatedAmount = v; }
    public void setTaxSavingAmount(long v)        { this.taxSavingAmount = v; }
    public void setNeededAnnualAmount(long v)     { this.neededAnnualAmount = v; }
    public void setStartRegularIncome(long v)     { this.startRegularIncome = v; }
    public void setRiskProfileId(int v)           { this.riskProfileId = v; }
    public void setAnnualIncomeRange(int v)       { this.annualIncomeRange = v; }
    public void setApi1LabelId(long v)            { this.api1LabelId = v; }
    public void setApi1UserId(long v)             { this.api1UserId = v; }
    public void setApi1AwareType(String v)        { this.api1AwareType = v; }
    public void setApi1InvestorId(long v)         { this.api1InvestorId = v; }
    public void setApi2LabelId(long v)            { this.api2LabelId = v; }
    public void setApi2UserId(long v)             { this.api2UserId = v; }
    public void setApi2InvestorId(long v)         { this.api2InvestorId = v; }
    public void setApi2NextFinancialYear(int v)   { this.api2NextFinancialYear = v; }
}
