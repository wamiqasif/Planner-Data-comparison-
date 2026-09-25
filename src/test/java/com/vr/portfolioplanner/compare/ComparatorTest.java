package com.vr.portfolioplanner.compare;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.vr.portfolioplanner.altfund.AlternateFundAudit;
import com.vr.portfolioplanner.altfund.FundDetailsClient;
import com.vr.portfolioplanner.altfund.FundDetailsSnapshot;
import com.vr.portfolioplanner.altfund.FundOpinionClient;
import com.vr.portfolioplanner.altfund.FundOpinionSnapshot;
import com.vr.portfolioplanner.altfund.MatchType;
import com.vr.portfolioplanner.altfund.RuleOutcome;
import com.vr.portfolioplanner.response.model.BreakdownEntry;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import com.vr.portfolioplanner.response.model.FundEntry;
import com.vr.portfolioplanner.response.model.TransactionLeg;

/**
 * Unit tests for the response comparison engine.
 * Uses in-memory ExtractedResponse objects — no live API calls, no JSON parsing.
 */
public class ComparatorTest {

    private static final Logger log = LoggerFactory.getLogger(ComparatorTest.class);

    private static final String TC = "TC_001";
    private static final BigDecimal TOL_CENT  = new BigDecimal("0.01");
    private static final BigDecimal TOL_RUPEE = new BigDecimal("1.00");

    // =========================================================================
    // Scenario 1: Exact match → PASS
    // =========================================================================

    @Test(groups = "compare", description = "Identical responses → PASS, zero mismatches")
    public void scenario01_exactMatch_pass() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000), sipLeg(15000), oneTimeLeg(4000000))),
            List.of(breakdown("LC001", "Large Cap", bd(100))));

        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000), sipLeg(15000), oneTimeLeg(4000000))),
            List.of(breakdown("LC001", "Large Cap", bd(100))));

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isPassed(), "Exact match must PASS: " + result.getFailingMismatches());
        Assert.assertEquals(result.getFailingMismatchCount(), 0);
        log.info("Scenario 1 (exact match): {}", result);
    }

    // =========================================================================
    // Scenario 2: Fund missing in API-2 → FAIL
    // =========================================================================

    @Test(groups = "compare", description = "API-2 missing a fund → COUNT_MISMATCH + MISSING_FIELD")
    public void scenario02_fundMissingInApi2_fail() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(
                fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap",  bd(20000)),
                fund("P2", "Beta Fund Direct-G",  "MC001", "Mid Cap",    bd(15000))),
            List.of());

        ExtractedResponse r2 = response("API-2", "success",
            List.of(
                fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap",  bd(20000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isFailed(), "Missing fund must cause FAIL");

        List<Mismatch> countMis = result.getByType(MismatchType.COUNT_MISMATCH);
        List<Mismatch> missingMis = result.getByType(MismatchType.MISSING_FIELD);
        Assert.assertFalse(countMis.isEmpty(),   "Must have COUNT_MISMATCH");
        Assert.assertFalse(missingMis.isEmpty(),  "Must have MISSING_FIELD");
        Assert.assertTrue(missingMis.get(0).getFieldPath().contains("P2"),
            "MISSING_FIELD must reference plan_id=P2");

        log.info("Scenario 2 (missing fund): {}", result);
        result.getFailingMismatches().forEach(m -> log.info("  {}", m));
    }

    // =========================================================================
    // Scenario 3: Extra fund in API-2 → FAIL
    // =========================================================================

    @Test(groups = "compare", description = "API-2 has extra fund → COUNT_MISMATCH + EXTRA_FIELD")
    public void scenario03_extraFundInApi2_fail() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());

        ExtractedResponse r2 = response("API-2", "success",
            List.of(
                fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000)),
                fund("P3", "Gamma Fund Direct-G", "SC001", "Small Cap",  bd(10000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isFailed());
        Assert.assertFalse(result.getByType(MismatchType.EXTRA_FIELD).isEmpty(),
            "Must have EXTRA_FIELD for P3");
        Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD)
            .get(0).getFieldPath().contains("P3"));

        log.info("Scenario 3 (extra fund): {}", result);
    }

    // =========================================================================
    // Scenario 4: Investment amount mismatch → FAIL
    // =========================================================================

    @Test(groups = "compare", description = "Amounts differ beyond tolerance → BUSINESS_VALUE_MISMATCH")
    public void scenario04_amountMismatch_fail() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(82000))),
            List.of());

        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(75000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isFailed());
        List<Mismatch> businessMis = result.getByType(MismatchType.BUSINESS_VALUE_MISMATCH);
        Assert.assertFalse(businessMis.isEmpty(), "Must have BUSINESS_VALUE_MISMATCH");
        Mismatch m = businessMis.get(0);
        Assert.assertEquals(m.getApi1Value(), "82000");
        Assert.assertEquals(m.getApi2Value(), "75000");
        Assert.assertTrue(m.getFieldPath().contains("investment_amount"));

        log.info("Scenario 4 (amount mismatch): {}", m);
    }

    // =========================================================================
    // Scenario 5: Category mismatch → FAIL
    // =========================================================================

    @Test(groups = "compare", description = "Different category_id → VALUE_MISMATCH")
    public void scenario05_categoryMismatch_fail() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund", "LC001", "Large Cap", bd(20000))),
            List.of());

        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Alpha Fund", "MC001", "Mid Cap", bd(20000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isFailed());
        // Both category_id and category_name differ
        long valueMismatches = result.getByType(MismatchType.VALUE_MISMATCH).size();
        Assert.assertTrue(valueMismatches >= 2,
            "Expected at least 2 VALUE_MISMATCH (category_id + category_name); got: " + valueMismatches);

        log.info("Scenario 5 (category mismatch): {}", result);
    }

    // =========================================================================
    // Scenario 6: Breakdown percentage mismatch → FAIL
    // =========================================================================

    @Test(groups = "compare",
          description = "Breakdown percentages differ beyond tolerance → BUSINESS_VALUE_MISMATCH")
    public void scenario06_breakdownMismatch_fail() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(),
            List.of(
                breakdown("LC001", "Large Cap", bd("75.00")),
                breakdown("MC001", "Mid Cap",   bd("25.00"))));

        ExtractedResponse r2 = response("API-2", "success",
            List.of(),
            List.of(
                breakdown("LC001", "Large Cap", bd("70.00")),
                breakdown("MC001", "Mid Cap",   bd("30.00"))));

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isFailed());
        List<Mismatch> busMis = result.getByType(MismatchType.BUSINESS_VALUE_MISMATCH);
        Assert.assertEquals(busMis.size(), 2, "Both breakdown percentages must be reported");
        busMis.forEach(m -> {
            Assert.assertTrue(m.getFieldPath().contains("percentage"),
                "Field path must reference percentage: " + m.getFieldPath());
            log.info("Scenario 6 breakdown mismatch: {}", m);
        });
    }

    // =========================================================================
    // Scenario 7: Multiple mismatches — all collected → FAIL
    // =========================================================================

    @Test(groups = "compare", description = "Multiple field differences → all collected, single FAIL")
    public void scenario07_multipleMismatches_allCollected() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of(breakdown("LC001", "Large Cap", bd("40.00"))));

        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "XX001", "Unknown",   bd(15000))),
            List.of(breakdown("LC001", "Large Cap", bd("50.00"))));

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isFailed());
        Assert.assertTrue(result.getFailingMismatchCount() >= 3,
            "Expected ≥3 mismatches (category_id, category_name, amount); got: "
            + result.getFailingMismatchCount());

        log.info("Scenario 7 (multiple mismatches): {} found", result.getFailingMismatchCount());
        result.getFailingMismatches().forEach(m -> log.info("  {}", m));
    }

    // =========================================================================
    // Scenario 8: Different fund array order → PASS
    // =========================================================================

    @Test(groups = "compare",
          description = "Same funds in different array order → PASS (matched by plan_id)")
    public void scenario08_differentFundOrder_pass() {
        // API-1: P1, P2, P3
        ExtractedResponse r1 = response("API-1", "success",
            List.of(
                fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(20000)),
                fund("P2", "Beta Direct-G",  "MC001", "Mid Cap",   bd(15000)),
                fund("P3", "Gamma Direct-G", "SC001", "Small Cap", bd(15000))),
            List.of());

        // API-2: P3, P1, P2  (reversed order)
        ExtractedResponse r2 = response("API-2", "success",
            List.of(
                fund("P3", "Gamma Direct-G", "SC001", "Small Cap", bd(15000)),
                fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(20000)),
                fund("P2", "Beta Direct-G",  "MC001", "Mid Cap",   bd(15000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isPassed(),
            "Different array order must still PASS; mismatches: " + result.getFailingMismatches());
        log.info("Scenario 8 (different order): {} — {}", result.getVerdict(), result);
    }

    // =========================================================================
    // Scenario 9: Dir-G vs Direct-G normalization → PASS with NORMALIZATION_DIFFERENCE
    // =========================================================================

    @Test(groups = "compare",
          description = "'Dir-G' in API-1 vs 'Direct-G' in API-2 → PASS, informational NORMALIZATION_DIFFERENCE")
    public void scenario09_dirGNormalization_passWithNote() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Large Cap Fund Dir-G",    "LC001", "Large Cap", bd(20000))),
            List.of());

        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Alpha Large Cap Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        // Must PASS — names normalize to the same value
        Assert.assertTrue(result.isPassed(),
            "Dir-G vs Direct-G must PASS after normalization; failing: "
            + result.getFailingMismatches());

        // Must record NORMALIZATION_DIFFERENCE for traceability
        List<Mismatch> normMis = result.getByType(MismatchType.NORMALIZATION_DIFFERENCE);
        Assert.assertEquals(normMis.size(), 1,
            "Expected exactly one NORMALIZATION_DIFFERENCE mismatch");
        Assert.assertEquals(normMis.get(0).getApi1Value(), "Alpha Large Cap Fund Dir-G");
        Assert.assertEquals(normMis.get(0).getApi2Value(), "Alpha Large Cap Fund Direct-G");

        log.info("Scenario 9 (Dir-G norm): {} | normDiffs={}", result.getVerdict(), normMis.size());
        log.info("  {}", normMis.get(0));
    }

    // =========================================================================
    // Scenario 10: Monetary tolerance
    // =========================================================================

    @Test(groups = "compare", description = "Amount difference within tolerance → PASS")
    public void scenario10a_amountWithinTolerance_pass() {
        // |50000.00 - 50000.005| = 0.005 ≤ 0.01 → equal
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Fund Direct-G", "LC001", "Large Cap", new BigDecimal("50000.00"))),
            List.of());

        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Fund Direct-G", "LC001", "Large Cap", new BigDecimal("50000.005"))),
            List.of());

        // We can't directly pass a custom tolerance to ResponseComparator (it reads from config).
        // Use ValueComparator directly for this assertion instead.
        var opt = ValueComparator.compareAmounts(
            TC, "investment_amount",
            new BigDecimal("50000.00"), new BigDecimal("50000.005"), TOL_CENT);

        Assert.assertFalse(opt.isPresent(),
            "Difference of 0.005 within tolerance 0.01 → no mismatch");
        log.info("Scenario 10a (within tolerance 0.01): no mismatch — PASS");
    }

    @Test(groups = "compare", description = "Amount difference outside tolerance → FAIL")
    public void scenario10b_amountOutsideTolerance_fail() {
        // |82000 - 75000| = 7000 > 0.01 → mismatch
        var opt = ValueComparator.compareAmounts(
            TC, "funds_data.data[plan_id=P1].investment_amount",
            new BigDecimal("82000"), new BigDecimal("75000"), TOL_CENT);

        Assert.assertTrue(opt.isPresent(), "7000 difference must cause BUSINESS_VALUE_MISMATCH");
        Assert.assertEquals(opt.get().getMismatchType(), MismatchType.BUSINESS_VALUE_MISMATCH);
        Assert.assertEquals(opt.get().getApi1Value(), "82000");
        Assert.assertEquals(opt.get().getApi2Value(), "75000");
        log.info("Scenario 10b (outside tolerance): {}", opt.get());
    }

    @Test(groups = "compare", description = "Percentage difference within tolerance → PASS")
    public void scenario10c_percentageWithinTolerance_pass() {
        // |75.0 - 75.005| ≤ 0.01 → no mismatch
        var opt = ValueComparator.compareAmounts(
            TC, "breakdown.percentage",
            new BigDecimal("75.00"), new BigDecimal("75.005"), TOL_CENT);

        Assert.assertFalse(opt.isPresent());
        log.info("Scenario 10c (pct within tolerance): no mismatch");
    }

    @Test(groups = "compare", description = "Percentage difference outside tolerance → FAIL")
    public void scenario10d_percentageOutsideTolerance_fail() {
        ExtractedResponse r1 = response("API-1", "success", List.of(),
            List.of(breakdown("LC001", "Large Cap", bd("75"))));
        ExtractedResponse r2 = response("API-2", "success", List.of(),
            List.of(breakdown("LC001", "Large Cap", bd("70"))));

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);
        Assert.assertTrue(result.isFailed());
        Assert.assertFalse(result.getByType(MismatchType.BUSINESS_VALUE_MISMATCH).isEmpty());
        log.info("Scenario 10d (pct outside tolerance): {}", result);
    }

    // =========================================================================
    // Additional: transaction type mismatch
    // =========================================================================

    @Test(groups = "compare",
          description = "API-1 has SIP+ONE_TIME; API-2 has only SIP → MISSING_FIELD for ONE_TIME")
    public void transactionTypeMismatch_missingOneTime() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Fund Direct-G", "LC001", "Large Cap", bd(20000),
                         sipLeg(15000), oneTimeLeg(4000000))),
            List.of());

        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Fund Direct-G", "LC001", "Large Cap", bd(20000),
                         sipLeg(15000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isFailed());
        List<Mismatch> missing = result.getByType(MismatchType.MISSING_FIELD);
        Assert.assertFalse(missing.isEmpty(), "ONE_TIME leg must be flagged as MISSING_FIELD");
        Assert.assertTrue(missing.stream()
            .anyMatch(m -> m.getFieldPath().contains("ONE_TIME")),
            "MISSING_FIELD must reference ONE_TIME type");
        log.info("Transaction type mismatch: {}", missing.get(0));
    }

    @Test(groups = "compare",
          description = "Same transaction types, SIP amounts differ → BUSINESS_VALUE_MISMATCH for SIP amount")
    public void transactionAmountMismatch() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Fund Direct-G", "LC001", "Large Cap", bd(20000),
                         sipLeg(15000))),
            List.of());

        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Fund Direct-G", "LC001", "Large Cap", bd(20000),
                         sipLeg(10000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isFailed());
        List<Mismatch> busMis = result.getByType(MismatchType.BUSINESS_VALUE_MISMATCH);
        Assert.assertFalse(busMis.isEmpty());
        Assert.assertTrue(busMis.stream().anyMatch(m -> m.getFieldPath().contains("SIP")),
            "SIP amount mismatch must reference SIP in field path");
        log.info("Transaction amount mismatch: {}", busMis.get(0));
    }

    // =========================================================================
    // Additional: ComparisonResult API
    // =========================================================================

    @Test(groups = "compare", description = "NORMALIZATION_DIFFERENCE does not increment failing count")
    public void normDifferenceDoesNotFail() {
        ComparisonResult result = ResponseComparator.compare(TC,
            response("API-1", "success",
                List.of(fund("P1", "Fund Dir-G", "LC001", "Large Cap", bd(20000))), List.of()),
            response("API-2", "success",
                List.of(fund("P1", "Fund Direct-G", "LC001", "Large Cap", bd(20000))), List.of()));

        Assert.assertEquals(result.getVerdict(), "PASS");
        Assert.assertEquals(result.getFailingMismatchCount(), 0);
        Assert.assertEquals(result.getByType(MismatchType.NORMALIZATION_DIFFERENCE).size(), 1);
        Assert.assertEquals(result.getTotalMismatchCount(), 1);
    }

    @Test(groups = "compare",
          description = "Status mismatch between responses → STATUS_MISMATCH")
    public void responseStatusMismatch() {
        ExtractedResponse r1 = response("API-1", "success", List.of(), List.of());
        ExtractedResponse r2 = response("API-2", "error",   List.of(), List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isFailed());
        Assert.assertFalse(result.getByType(MismatchType.VALUE_MISMATCH).isEmpty(),
            "Status string mismatch must be a VALUE_MISMATCH");
        log.info("Status mismatch: {}", result.getByType(MismatchType.VALUE_MISMATCH).get(0));
    }

    @Test(groups = "compare", description = "Failed extraction produces API_EXECUTION_ERROR")
    public void failedExtractionProducesError() {
        ExtractedResponse ok     = response("API-1", "success", List.of(), List.of());
        ExtractedResponse failed = ExtractedResponse.failed("API-2", "parse error");

        ComparisonResult result = ResponseComparator.compare(TC, ok, failed);

        Assert.assertTrue(result.isFailed());
        Assert.assertFalse(result.getByType(MismatchType.API_EXECUTION_ERROR).isEmpty());
        log.info("Extraction failure: {}", result);
    }

    // =========================================================================
    // Alternate-fund business validation — see AlternateFundValidator, called
    // from ResponseComparator step 4b. API-2 is always the original/reference
    // fund; API-1 is the alternate candidate when plan_id differs. Funds are
    // paired by category_name (Fund Details API) — never by plan_id or array
    // order — and Rules 1-5 run only against the API-1 side.
    // FundOpinionClient/FundDetailsClient primeForTest/resetForTest bypass the
    // live network calls so these stay in-memory unit tests.
    // =========================================================================

    @Test(groups = "compare",
          description = "API-1 alternate not present in fund-opinion-data (Rule 1 fails) → "
                       + "ALTERNATE_REJECTED, never MISSING_FIELD/EXTRA_FIELD")
    public void alternateFund_rejectedByRule1_notInOpinionData_fail() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());
        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P9", "Beta Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());

        FundDetailsClient.primeForTest(Map.of(
            "P1", FundDetailsSnapshot.found("P1", "Large Cap"),
            "P9", FundDetailsSnapshot.found("P9", "Large Cap")));
        FundOpinionClient.primeForTest(Map.of()); // P1 not present in fund-opinion-data
        try {
            ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

            Assert.assertTrue(result.isFailed());
            Assert.assertFalse(result.getByType(MismatchType.ALTERNATE_FUND_REJECTED).isEmpty(),
                "Unrecognised alternate must be ALTERNATE_REJECTED, not MISSING/EXTRA");
            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty(),
                "A valid alternate scenario must never fall back to MISSING_FIELD");
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 1);
            AlternateFundAudit audit = result.getAlternateFundAudits().get(0);
            Assert.assertEquals(audit.getMatchType(), MatchType.ALTERNATE_REJECTED);
            Assert.assertEquals(audit.getRule1().getOutcome(), RuleOutcome.FAIL);
            log.info("Alternate fund (Rule 1 rejected): {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    @Test(groups = "compare",
          description = "API-1 alternate is Good, not Analyst's Choice, 1-3 star (Rule 4 fails) → "
                       + "ALTERNATE_REJECTED")
    public void alternateFund_rejectedByRule4_goodNotPickLowStar_fail() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());
        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P9", "Beta Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());

        FundDetailsClient.primeForTest(Map.of(
            "P1", FundDetailsSnapshot.found("P1", "Large Cap"),
            "P9", FundDetailsSnapshot.found("P9", "Large Cap")));
        FundOpinionClient.primeForTest(Map.of(
            "P1", FundOpinionSnapshot.found("P1", 1, false, 2, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

            Assert.assertTrue(result.isFailed());
            Assert.assertFalse(result.getByType(MismatchType.ALTERNATE_FUND_REJECTED).isEmpty());
            AlternateFundAudit audit = result.getAlternateFundAudits().get(0);
            Assert.assertEquals(audit.getMatchType(), MatchType.ALTERNATE_REJECTED);
            Assert.assertEquals(audit.getRule4().getOutcome(), RuleOutcome.FAIL);
            log.info("Alternate fund (Rule 4 rejected): {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    @Test(groups = "compare",
          description = "API-1 alternate passes all 5 rules — Rule 2's empty prohibited list means "
                       + "'nothing prohibited' (PASS) and Rule 3 is NOT_APPLICABLE for a non-Index-Fund "
                       + "category, neither is a data gap → overall ALTERNATE_MATCH")
    public void alternateFund_cleanCandidate_resolvesToAlternateMatch() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());
        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P9", "Beta Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());

        FundDetailsClient.primeForTest(Map.of(
            "P1", FundDetailsSnapshot.found("P1", "Large Cap"),
            "P9", FundDetailsSnapshot.found("P9", "Large Cap")));
        FundOpinionClient.primeForTest(Map.of(
            "P1", FundOpinionSnapshot.found("P1", 1, true, 4, null))); // Good, Analyst's Choice, no tag
        try {
            ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

            Assert.assertFalse(result.isFailed(), "clean alternate candidate must not fail the row");
            Assert.assertFalse(result.getByType(MismatchType.ALTERNATE_FUND_MATCH).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.ALTERNATE_FUND_DATA_UNAVAILABLE).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.ALTERNATE_FUND_REJECTED).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());

            AlternateFundAudit audit = result.getAlternateFundAudits().get(0);
            Assert.assertEquals(audit.getMatchType(), MatchType.ALTERNATE_MATCH);
            Assert.assertEquals(audit.getRule1().getOutcome(), RuleOutcome.PASS);
            Assert.assertEquals(audit.getRule2().getOutcome(), RuleOutcome.PASS);
            Assert.assertEquals(audit.getRule3().getOutcome(), RuleOutcome.NOT_APPLICABLE);
            Assert.assertEquals(audit.getRule4().getOutcome(), RuleOutcome.PASS);
            Assert.assertEquals(audit.getRule5().getOutcome(), RuleOutcome.PASS);
            log.info("Alternate fund (clean candidate, alternate match): {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    @Test(groups = "compare",
          description = "Real-world shape (TC_05264): 4 funds matched directly by plan_id, 3 "
                       + "funds paired as alternates by category_name — each independently "
                       + "evaluated; all resolve ALTERNATE_MATCH (none is a prohibited or Index "
                       + "Fund category, so Rules 2/3 PASS/NOT_APPLICABLE rather than gapping)")
    public void alternateFund_multiplePairs_TC05264Shape() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(
                // 4 funds matched directly by plan_id — unaffected by alternate-fund logic.
                fund("M1", "Fund M1 Direct-G", "CAT1", "Category 1", bd(10000)),
                fund("M2", "Fund M2 Direct-G", "CAT2", "Category 2", bd(20000)),
                fund("M3", "Fund M3 Direct-G", "CAT3", "Category 3", bd(30000)),
                fund("M4", "Fund M4 Direct-G", "CAT4", "Category 4", bd(40000)),
                // 3 funds API-1 offers as alternates — different plan_id/name.
                fund("R1", "Replacement Fund One Direct-G",   "LC001", "Large Cap",  bd(15000)),
                fund("R2", "Replacement Fund Two Direct-G",   "MC001", "Mid Cap",    bd(25000)),
                fund("R3", "Replacement Fund Three Direct-G", "SC001", "Small Cap",  bd(35000))),
            List.of());

        ExtractedResponse r2 = response("API-2", "success",
            List.of(
                fund("M1", "Fund M1 Direct-G", "CAT1", "Category 1", bd(10000)),
                fund("M2", "Fund M2 Direct-G", "CAT2", "Category 2", bd(20000)),
                fund("M3", "Fund M3 Direct-G", "CAT3", "Category 3", bd(30000)),
                fund("M4", "Fund M4 Direct-G", "CAT4", "Category 4", bd(40000)),
                // The 3 original funds API-1 substitutes.
                fund("O1", "Original Fund One Direct-G",   "LC001", "Large Cap",  bd(15000)),
                fund("O2", "Original Fund Two Direct-G",   "MC001", "Mid Cap",    bd(25000)),
                fund("O3", "Original Fund Three Direct-G", "SC001", "Small Cap",  bd(35000))),
            List.of());

        // Pairing is by Fund Details category_name — independent of each API's own category_id.
        FundDetailsClient.primeForTest(Map.of(
            "R1", FundDetailsSnapshot.found("R1", "Large Cap"), "O1", FundDetailsSnapshot.found("O1", "Large Cap"),
            "R2", FundDetailsSnapshot.found("R2", "Mid Cap"),   "O2", FundDetailsSnapshot.found("O2", "Mid Cap"),
            "R3", FundDetailsSnapshot.found("R3", "Small Cap"), "O3", FundDetailsSnapshot.found("O3", "Small Cap")));
        FundOpinionClient.primeForTest(Map.of(
            "R1", FundOpinionSnapshot.found("R1", 1, true, 4, null),
            "R2", FundOpinionSnapshot.found("R2", 1, true, 5, null),
            "R3", FundOpinionSnapshot.found("R3", 1, true, 3, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

            Assert.assertFalse(result.isFailed(), "clean alternates across all 3 pairs must not fail the row");
            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 3,
                "Each of the 3 pairs must produce its own audit row");
            Assert.assertTrue(result.getAlternateFundAudits().stream()
                .allMatch(a -> a.getMatchType() == MatchType.ALTERNATE_MATCH));
            log.info("Alternate fund (TC_05264 shape, 3 pairs): {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    @Test(groups = "compare",
          description = "2 API-1 orphans share one category_name against 1 API-2 orphan → pairing "
                       + "cannot be uniquely resolved → PAIRING_AMBIGUOUS for all 3, never guessed")
    public void alternateFund_ambiguousPairing_multipleCandidatesShareCategory() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(
                fund("R1", "Replacement Fund One Direct-G", "LC001", "Large Cap", bd(15000)),
                fund("R2", "Replacement Fund Two Direct-G", "LC002", "Large Cap", bd(16000))),
            List.of());
        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("O1", "Original Fund One Direct-G", "LC001", "Large Cap", bd(15000))),
            List.of());

        FundDetailsClient.primeForTest(Map.of(
            "R1", FundDetailsSnapshot.found("R1", "Large Cap"),
            "R2", FundDetailsSnapshot.found("R2", "Large Cap"),
            "O1", FundDetailsSnapshot.found("O1", "Large Cap")));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

            Assert.assertTrue(result.isFailed());
            Assert.assertEquals(result.getByType(MismatchType.ALTERNATE_FUND_PAIRING_AMBIGUOUS).size(), 3,
                "All 3 candidates sharing the category must be flagged ambiguous");
            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 3);
            Assert.assertTrue(result.getAlternateFundAudits().stream()
                .allMatch(a -> a.getMatchType() == MatchType.PAIRING_AMBIGUOUS));
            log.info("Alternate fund (ambiguous pairing): {}", result);
        } finally {
            FundDetailsClient.resetForTest();
        }
    }

    @Test(groups = "compare",
          description = "Fund Details fetch fails for an orphan → REQUIRED_DATA_NOT_AVAILABLE, "
                       + "never silently falls back to MISSING_FIELD/EXTRA_FIELD")
    public void alternateFund_fundDetailsFetchFailure_dataGap() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());
        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P9", "Beta Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());

        FundDetailsClient.primeFailureForTest(Set.of("P1"));
        FundDetailsClient.primeForTest(Map.of("P9", FundDetailsSnapshot.found("P9", "Large Cap")));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

            Assert.assertTrue(result.isFailed());
            Assert.assertFalse(result.getByType(MismatchType.ALTERNATE_FUND_DATA_UNAVAILABLE).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            log.info("Alternate fund (Fund Details fetch failure): {}", result);
        } finally {
            FundDetailsClient.resetForTest();
        }
    }

    @Test(groups = "compare",
          description = "API-1 orphan's category_name matches nothing on the API-2 side → genuine "
                       + "absence, falls through to MISSING_FIELD/EXTRA_FIELD unchanged")
    public void alternateFund_noCategoryMatchOnOtherSide_stillMissingAndExtra() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());
        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P9", "Beta Fund Direct-G", "MC001", "Mid Cap", bd(20000))),
            List.of());

        FundDetailsClient.primeForTest(Map.of(
            "P1", FundDetailsSnapshot.found("P1", "Large Cap"),
            "P9", FundDetailsSnapshot.found("P9", "Mid Cap")));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

            Assert.assertTrue(result.isFailed());
            Assert.assertFalse(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertFalse(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            Assert.assertTrue(result.getAlternateFundAudits().isEmpty(),
                "No pairing candidate exists on the other side — a real absence, not an alternate scenario");
            log.info("Alternate fund (no category match, still missing/extra): {}", result);
        } finally {
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Helpers — test data builders
    // =========================================================================

    private ExtractedResponse response(String label, String status,
                                        List<FundEntry> funds, List<BreakdownEntry> breakdowns) {
        return ExtractedResponse.success(label, status, null, "investor-test",
            funds, breakdowns, List.of());
    }

    private FundEntry fund(String planId, String planName, String catId, String catFmt,
                            BigDecimal amount, TransactionLeg... legs) {
        return FundEntry.builder()
            .planId(planId).planName(planName)
            .categoryId(catId).categoryFmt(catFmt)
            .amount(amount)
            .legs(legs.length > 0 ? List.of(legs) : List.of())
            .build();
    }

    private TransactionLeg sipLeg(long amount) {
        return new TransactionLeg("SIP", BigDecimal.valueOf(amount));
    }

    private TransactionLeg oneTimeLeg(long amount) {
        return new TransactionLeg("One-time", BigDecimal.valueOf(amount));
    }

    private BreakdownEntry breakdown(String catId, String catFmt, BigDecimal pct) {
        return new BreakdownEntry(catId, catFmt, pct, null, null);
    }

    private BigDecimal bd(long v)   { return BigDecimal.valueOf(v); }
    private BigDecimal bd(String v) { return new BigDecimal(v); }
}
