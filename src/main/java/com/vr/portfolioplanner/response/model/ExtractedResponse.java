package com.vr.portfolioplanner.response.model;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Holds all business data extracted from one API response.
 *
 * <p>Created by {@link com.vr.portfolioplanner.response.Api1ResponseExtractor}
 * or {@link com.vr.portfolioplanner.response.Api2ResponseExtractor}.
 *
 * <h3>Field comparability summary</h3>
 * <pre>
 *   status                  COMPARABLE   (data.status in both APIs)
 *   investorId              COMPARABLE   (data.investor.investor_id in both APIs)
 *   funds (FundEntry list)  COMPARABLE   (shared fields only — see FundEntry)
 *   breakdowns              COMPARABLE   (data.breakdown_funds_data.data[] in both)
 *
 *   message                 NOT_COMPARABLE  — API-1 only (data.message); absent in API-2
 *   legs[] per fund         NOT_COMPARABLE  — API-1 only; no structural equivalent in API-2
 *   inv_data[] per fund     NOT_COMPARABLE  — API-2 only; no structural equivalent in API-1
 *   port_builder_id         NOT_COMPARABLE  — technical/generated ID, excluded by design
 *   output_id               NOT_COMPARABLE  — technical/generated ID, excluded by design
 * </pre>
 */
public final class ExtractedResponse {

    private final String             apiLabel;
    private final String             status;
    /**
     * Present in API-1 ({@code data.message}); {@code null} in API-2.
     * Marked {@link FundEntry#NOT_COMPARABLE} — do not compare across APIs.
     */
    private final String             message;
    private final String             investorId;
    private final List<FundEntry>    funds;
    private final List<BreakdownEntry> breakdowns;
    /**
     * Fields that exist in the raw JSON but are excluded from comparison
     * because they have no reliable cross-API equivalent.
     * Informational only — not used by the comparator.
     */
    private final List<String>       notComparableFields;
    private final boolean            extractionSuccess;
    private final String             extractionError;

    private ExtractedResponse(String apiLabel, String status, String message,
                               String investorId, List<FundEntry> funds,
                               List<BreakdownEntry> breakdowns,
                               List<String> notComparableFields,
                               boolean extractionSuccess, String extractionError) {
        this.apiLabel            = apiLabel;
        this.status              = status;
        this.message             = message;
        this.investorId          = investorId;
        this.funds               = Collections.unmodifiableList(funds);
        this.breakdowns          = Collections.unmodifiableList(breakdowns);
        this.notComparableFields = Collections.unmodifiableList(notComparableFields);
        this.extractionSuccess   = extractionSuccess;
        this.extractionError     = extractionError;
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    public static ExtractedResponse success(String apiLabel, String status, String message,
                                             String investorId, List<FundEntry> funds,
                                             List<BreakdownEntry> breakdowns,
                                             List<String> notComparableFields) {
        return new ExtractedResponse(apiLabel, status, message, investorId,
            funds, breakdowns, notComparableFields, true, null);
    }

    /**
     * Returned when JSON parsing or structural navigation fails entirely.
     * The response body is unusable for comparison.
     */
    public static ExtractedResponse failed(String apiLabel, String errorMessage) {
        return new ExtractedResponse(apiLabel, null, null, null,
            Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), false, errorMessage);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String               getApiLabel()           { return apiLabel; }
    public String               getStatus()             { return status; }
    public String               getMessage()            { return message; }
    public String               getInvestorId()         { return investorId; }
    public List<FundEntry>      getFunds()              { return funds; }
    public List<BreakdownEntry> getBreakdowns()         { return breakdowns; }
    public List<String>         getNotComparableFields(){ return notComparableFields; }
    public boolean              isExtractionSuccess()   { return extractionSuccess; }
    public String               getExtractionError()    { return extractionError; }

    // Convenience lookup methods — useful in the comparator step
    public Optional<FundEntry> getFundByPlanId(String planId) {
        return funds.stream()
                    .filter(f -> planId.equals(f.getPlanId()))
                    .findFirst();
    }

    public Optional<BreakdownEntry> getBreakdownByCategoryId(String categoryId) {
        return breakdowns.stream()
                         .filter(b -> categoryId.equals(b.getCategoryId()))
                         .findFirst();
    }

    @Override
    public String toString() {
        if (!extractionSuccess) {
            return String.format("ExtractedResponse{%s FAILED: %s}", apiLabel, extractionError);
        }
        return String.format(
            "ExtractedResponse{%s status='%s' investor='%s' funds=%d breakdowns=%d notComparable=%s}",
            apiLabel, status, investorId, funds.size(), breakdowns.size(), notComparableFields);
    }
}
