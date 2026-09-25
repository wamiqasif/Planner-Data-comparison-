package com.vr.portfolioplanner.compare;

import com.vr.portfolioplanner.altfund.AlternateFundAudit;
import com.vr.portfolioplanner.altfund.AlternateFundValidator;
import com.vr.portfolioplanner.client.ApiResponse;
import com.vr.portfolioplanner.config.ConfigReader;
import com.vr.portfolioplanner.response.model.BreakdownEntry;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import com.vr.portfolioplanner.response.model.FundEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates the full business-level comparison between API-1 and API-2 responses.
 *
 * <h3>Comparison order (per specification)</h3>
 * <ol>
 *   <li>HTTP status codes</li>
 *   <li>API response success/status string</li>
 *   <li>Fund count (COUNT_MISMATCH when sizes differ; comparison continues regardless)</li>
 *   <li>Fund matching by {@code plan_id}. API-2 is always the original/reference fund; funds
 *       left unmatched by {@code plan_id} are paired with the other side by {@code category_name}
 *       and the API-1 side is validated as an alternate candidate against 5 business rules (see
 *       {@link com.vr.portfolioplanner.altfund.AlternateFundValidator}) — never reported as
 *       MISSING_FIELD/EXTRA_FIELD just because {@code plan_id} differs. A category shared by more
 *       than one orphan on either side is {@code PAIRING_AMBIGUOUS} rather than guessed. Only funds
 *       that match no candidate on the other side at all fall through to MISSING_FIELD / EXTRA_FIELD.</li>
 *   <li>Category ID, category name, fund name, investment amount, transaction type &amp; amounts
 *       (delegated to {@link FundComparator})</li>
 *   <li>Breakdown count</li>
 *   <li>Breakdown matching by {@code category_id}; MISSING_FIELD / EXTRA_FIELD for unmatched</li>
 *   <li>Breakdown category name and percentage (delegated to {@link BreakdownComparator})</li>
 * </ol>
 *
 * <p>Design constraints:
 * <ul>
 *   <li>Does NOT stop at the first mismatch — ALL mismatches are collected.</li>
 *   <li>Does NOT compare fund array order — funds are matched by {@code plan_id}.</li>
 *   <li>Does NOT compare raw JSON.</li>
 *   <li>Does NOT throw assertions — returns {@link ComparisonResult} in all cases.</li>
 * </ul>
 */
public final class ResponseComparator {

    private static final Logger log = LoggerFactory.getLogger(ResponseComparator.class);

    private ResponseComparator() {}

    // -------------------------------------------------------------------------
    // Entry points
    // -------------------------------------------------------------------------

    /**
     * Full comparison including HTTP status check.
     *
     * @param testCaseId    test case identifier written into every {@link Mismatch}
     * @param api1Response  raw HTTP response from API-1 (may be null to skip HTTP check)
     * @param api2Response  raw HTTP response from API-2 (may be null to skip HTTP check)
     * @param ext1          extracted business data from API-1
     * @param ext2          extracted business data from API-2
     * @return {@link ComparisonResult} with all collected mismatches; never null
     */
    public static ComparisonResult compare(
            String testCaseId,
            ApiResponse api1Response, ApiResponse api2Response,
            ExtractedResponse ext1, ExtractedResponse ext2) {

        List<Mismatch> mismatches = new ArrayList<>();
        List<AlternateFundAudit> alternateFundAudits = new ArrayList<>();
        BigDecimal tolerance = readAmountTolerance();
        BigDecimal percentageTolerance = readPercentageTolerance();

        // Step 1 — HTTP status
        if (api1Response != null && api2Response != null) {
            compareHttpStatus(testCaseId, api1Response, api2Response, mismatches);
        }

        // Step 2 — extraction sanity (stop early only when data is completely unusable)
        if (!ext1.isExtractionSuccess()) {
            mismatches.add(Mismatch.builder(testCaseId, MismatchType.API_EXECUTION_ERROR)
                .fieldPath("api1.extraction")
                .api1Value("(extraction failed)")
                .api2Value("(ok)")
                .message("API-1 response extraction failed: " + ext1.getExtractionError())
                .build());
        }
        if (!ext2.isExtractionSuccess()) {
            mismatches.add(Mismatch.builder(testCaseId, MismatchType.API_EXECUTION_ERROR)
                .fieldPath("api2.extraction")
                .api1Value("(ok)")
                .api2Value("(extraction failed)")
                .message("API-2 response extraction failed: " + ext2.getExtractionError())
                .build());
        }
        if (!ext1.isExtractionSuccess() || !ext2.isExtractionSuccess()) {
            return new ComparisonResult(testCaseId, mismatches);
        }

        // Step 2 — API status string
        ValueComparator.compareStrings(
            testCaseId, "data.status", ext1.getStatus(), ext2.getStatus()
        ).ifPresent(mismatches::add);

        // Steps 3–10 — fund-level
        compareFunds(testCaseId, ext1, ext2, tolerance, mismatches, alternateFundAudits);

        // Steps 11–12 — breakdown-level
        compareBreakdowns(testCaseId, ext1, ext2, percentageTolerance, mismatches);

        log.info("Comparison for '{}': {} mismatch(es) — {}",
            testCaseId, mismatches.stream().filter(Mismatch::isFailing).count(),
            mismatches.stream().noneMatch(Mismatch::isFailing) ? "PASS" : "FAIL");

        return new ComparisonResult(testCaseId, mismatches, alternateFundAudits);
    }

    /**
     * Business-only comparison (no HTTP status check).
     * Convenience overload used in unit tests that work directly with
     * {@link ExtractedResponse} objects.
     */
    public static ComparisonResult compare(
            String testCaseId, ExtractedResponse ext1, ExtractedResponse ext2) {
        return compare(testCaseId, null, null, ext1, ext2);
    }

    // -------------------------------------------------------------------------
    // Step 1 — HTTP status
    // -------------------------------------------------------------------------

    private static void compareHttpStatus(
            String testCaseId, ApiResponse api1, ApiResponse api2, List<Mismatch> mismatches) {

        boolean err1 = api1.isNetworkError();
        boolean err2 = api2.isNetworkError();

        if (err1 || err2) {
            mismatches.add(Mismatch.builder(testCaseId, MismatchType.API_EXECUTION_ERROR)
                .fieldPath("http.status")
                .api1Value(err1 ? "NETWORK_ERROR" : String.valueOf(api1.getStatusCode()))
                .api2Value(err2 ? "NETWORK_ERROR" : String.valueOf(api2.getStatusCode()))
                .message("Network error: "
                    + (err1 ? "API-1: " + api1.getErrorMessage() + " " : "")
                    + (err2 ? "API-2: " + api2.getErrorMessage() : ""))
                .build());
            return;
        }

        if (api1.getStatusCode() != api2.getStatusCode()) {
            mismatches.add(Mismatch.builder(testCaseId, MismatchType.STATUS_MISMATCH)
                .fieldPath("http.status")
                .api1Value(String.valueOf(api1.getStatusCode()))
                .api2Value(String.valueOf(api2.getStatusCode()))
                .message("HTTP status codes differ")
                .build());
        }
    }

    // -------------------------------------------------------------------------
    // Steps 3–10 — fund comparison
    // -------------------------------------------------------------------------

    private static void compareFunds(
            String testCaseId, ExtractedResponse ext1, ExtractedResponse ext2,
            BigDecimal tolerance, List<Mismatch> mismatches, List<AlternateFundAudit> alternateFundAudits) {

        List<FundEntry> funds1 = ext1.getFunds();
        List<FundEntry> funds2 = ext2.getFunds();

        // Step 3 — fund count
        if (funds1.size() != funds2.size()) {
            mismatches.add(Mismatch.builder(testCaseId, MismatchType.COUNT_MISMATCH)
                .fieldPath("funds_data.data.count")
                .api1Value(String.valueOf(funds1.size()))
                .api2Value(String.valueOf(funds2.size()))
                .message("Fund count differs; comparison continues for matched plan_ids")
                .build());
        }

        // Step 4 — match by plan_id (no array-order dependency)
        Map<String, FundEntry> map1 = fundsByPlanId(funds1);
        Map<String, FundEntry> map2 = fundsByPlanId(funds2);

        Set<String> unmatched1 = new LinkedHashSet<>();
        for (String planId : map1.keySet()) if (!map2.containsKey(planId)) unmatched1.add(planId);
        Set<String> unmatched2 = new LinkedHashSet<>();
        for (String planId : map2.keySet()) if (!map1.containsKey(planId)) unmatched2.add(planId);

        // Step 4b — funds still unmatched by plan_id are paired with the other side by
        // category_name and validated as alternate candidates (API-2 = original,
        // API-1 = alternate) against 5 business rules. See AlternateFundValidator.
        if (!unmatched1.isEmpty() && !unmatched2.isEmpty()) {
            Map<String, FundEntry> orphans1 = new LinkedHashMap<>();
            for (String planId : unmatched1) orphans1.put(planId, map1.get(planId));
            Map<String, FundEntry> orphans2 = new LinkedHashMap<>();
            for (String planId : unmatched2) orphans2.put(planId, map2.get(planId));

            AlternateFundValidator.PairingOutcome pairing =
                AlternateFundValidator.pairAndValidate(testCaseId, orphans1, orphans2, tolerance);

            mismatches.addAll(pairing.getMismatches());
            alternateFundAudits.addAll(pairing.getAudits());
            unmatched1.removeAll(pairing.getConsumedApi1());
            unmatched2.removeAll(pairing.getConsumedApi2());
        }

        for (String planId : unmatched1) {
            String f1Name = map1.get(planId).getPlanName();
            mismatches.add(ValueComparator.missingInApi2(
                testCaseId,
                "funds_data.data[plan_id=" + planId + "]",
                planId,
                "Fund plan_id='" + planId + "' present in API-1 but missing in API-2"
            ).withFundContext(planId, f1Name, "NOT FOUND"));
        }

        for (String planId : unmatched2) {
            String f2Name = map2.get(planId).getPlanName();
            mismatches.add(ValueComparator.extraInApi2(
                testCaseId,
                "funds_data.data[plan_id=" + planId + "]",
                planId,
                "Fund plan_id='" + planId + "' present in API-2 but missing in API-1"
            ).withFundContext(planId, "NOT FOUND", f2Name));
        }

        // Steps 5–10 — field-level comparison for matched plans
        for (Map.Entry<String, FundEntry> e : map1.entrySet()) {
            FundEntry f2 = map2.get(e.getKey());
            if (f2 != null) {
                mismatches.addAll(FundComparator.compare(testCaseId, e.getValue(), f2, tolerance));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Steps 11–12 — breakdown comparison
    // -------------------------------------------------------------------------

    private static void compareBreakdowns(
            String testCaseId, ExtractedResponse ext1, ExtractedResponse ext2,
            BigDecimal percentageTolerance, List<Mismatch> mismatches) {

        List<BreakdownEntry> bd1 = ext1.getBreakdowns();
        List<BreakdownEntry> bd2 = ext2.getBreakdowns();

        if (bd1.size() != bd2.size()) {
            mismatches.add(Mismatch.builder(testCaseId, MismatchType.COUNT_MISMATCH)
                .fieldPath("breakdown_funds_data.data.count")
                .api1Value(String.valueOf(bd1.size()))
                .api2Value(String.valueOf(bd2.size()))
                .message("Breakdown entry count differs")
                .build());
        }

        Map<String, BreakdownEntry> bmap1 = breakdownsByCategoryId(bd1);
        Map<String, BreakdownEntry> bmap2 = breakdownsByCategoryId(bd2);

        for (String catId : bmap1.keySet()) {
            if (!bmap2.containsKey(catId)) {
                mismatches.add(ValueComparator.missingInApi2(
                    testCaseId,
                    "breakdown_funds_data.data[category_id=" + catId + "]",
                    catId,
                    "category_id='" + catId + "' present in API-1 but not in API-2"
                ));
            }
        }

        for (String catId : bmap2.keySet()) {
            if (!bmap1.containsKey(catId)) {
                mismatches.add(ValueComparator.extraInApi2(
                    testCaseId,
                    "breakdown_funds_data.data[category_id=" + catId + "]",
                    catId,
                    "category_id='" + catId + "' present in API-2 but not in API-1"
                ));
            }
        }

        for (Map.Entry<String, BreakdownEntry> e : bmap1.entrySet()) {
            BreakdownEntry b2 = bmap2.get(e.getKey());
            if (b2 != null) {
                mismatches.addAll(BreakdownComparator.compare(testCaseId, e.getValue(), b2, percentageTolerance));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Map<String, FundEntry> fundsByPlanId(List<FundEntry> funds) {
        Map<String, FundEntry> map = new LinkedHashMap<>();
        for (FundEntry f : funds) {
            if (f.getPlanId() != null) map.put(f.getPlanId(), f);
        }
        return map;
    }

    private static Map<String, BreakdownEntry> breakdownsByCategoryId(List<BreakdownEntry> entries) {
        Map<String, BreakdownEntry> map = new LinkedHashMap<>();
        for (BreakdownEntry b : entries) {
            if (b.getCategoryId() != null) map.put(b.getCategoryId(), b);
        }
        return map;
    }

    private static BigDecimal readAmountTolerance() {
        try {
            return BigDecimal.valueOf(ConfigReader.getInstance().getComparisonAmountTolerance());
        } catch (Exception e) {
            log.warn("Could not read comparison amount tolerance from config; defaulting to 0.01");
            return new BigDecimal("0.01");
        }
    }

    private static BigDecimal readPercentageTolerance() {
        try {
            return BigDecimal.valueOf(ConfigReader.getInstance().getComparisonPercentageTolerance());
        } catch (Exception e) {
            log.warn("Could not read comparison percentage tolerance from config; defaulting to 0.01");
            return new BigDecimal("0.01");
        }
    }
}
