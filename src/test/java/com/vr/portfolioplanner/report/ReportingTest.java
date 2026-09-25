package com.vr.portfolioplanner.report;

import com.vr.portfolioplanner.compare.ComparisonResult;
import com.vr.portfolioplanner.compare.Mismatch;
import com.vr.portfolioplanner.compare.MismatchType;
import com.vr.portfolioplanner.compare.ResponseComparator;
import com.vr.portfolioplanner.compare.ValueComparator;
import com.vr.portfolioplanner.compare.FundComparator;
import com.vr.portfolioplanner.response.model.BreakdownEntry;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import com.vr.portfolioplanner.response.model.FundEntry;
import com.vr.portfolioplanner.response.model.TransactionLeg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Unit tests verifying that mismatch objects carry full fund context
 * (planId, fund names, difference, tolerance) for reporting purposes.
 *
 * <p>No live API calls. All assertions use in-memory data.
 */
public class ReportingTest {

    private static final Logger log = LoggerFactory.getLogger(ReportingTest.class);
    private static final String TC  = "TC_REPORT_01";
    private static final BigDecimal TOL = new BigDecimal("0.01");

    // =========================================================================
    // 1. Mismatch fund context
    // =========================================================================

    @Test(groups = "reporting", description = "1. FundComparator mismatch contains plan_id")
    public void mismatch_containsPlanId() {
        FundEntry f1 = fund("P1", "Alpha Dir-G",    "LC001", "Large Cap", bd(82000));
        FundEntry f2 = fund("P1", "Alpha Direct-G", "XX001", "Unknown",   bd(82000));

        List<Mismatch> mismatches = FundComparator.compare(TC, f1, f2, TOL);

        Assert.assertFalse(mismatches.isEmpty());
        mismatches.forEach(m -> {
            Assert.assertEquals(m.getPlanId(), "P1",
                "Every fund mismatch must carry planId; got null for type=" + m.getMismatchType());
        });
        log.info("1. planId verified in {} mismatch(es)", mismatches.size());
    }

    @Test(groups = "reporting", description = "2. FundComparator mismatch contains API-1 fund name")
    public void mismatch_containsApi1FundName() {
        FundEntry f1 = fund("P1", "Alpha Dir-G",    "LC001", "Large Cap", bd(82000));
        FundEntry f2 = fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(75000));

        List<Mismatch> mismatches = FundComparator.compare(TC, f1, f2, TOL);

        Assert.assertFalse(mismatches.isEmpty());
        mismatches.forEach(m ->
            Assert.assertNotNull(m.getApi1FundName(),
                "api1FundName must be set for type=" + m.getMismatchType()));
        log.info("2. api1FundName='{}' in {} mismatch(es)",
            mismatches.get(0).getApi1FundName(), mismatches.size());
    }

    @Test(groups = "reporting", description = "3. FundComparator mismatch contains API-2 fund name")
    public void mismatch_containsApi2FundName() {
        FundEntry f1 = fund("P1", "Alpha Dir-G",    "LC001", "Large Cap", bd(82000));
        FundEntry f2 = fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(75000));

        List<Mismatch> mismatches = FundComparator.compare(TC, f1, f2, TOL);

        mismatches.forEach(m ->
            Assert.assertNotNull(m.getApi2FundName(),
                "api2FundName must be set for type=" + m.getMismatchType()));
        log.info("3. api2FundName='{}' verified", mismatches.get(0).getApi2FundName());
    }

    @Test(groups = "reporting", description = "4. Field mismatch is attached to the correct plan_id")
    public void mismatch_fieldAttachedToCorrectPlan() {
        FundEntry f1 = fund("PLAN_001", "Alpha Dir-G",    "LC001", "Large Cap", bd(20000));
        FundEntry f2 = fund("PLAN_001", "Alpha Direct-G", "XX001", "Unknown",   bd(20000));

        List<Mismatch> mismatches = FundComparator.compare(TC, f1, f2, TOL);

        // Category mismatches should reference PLAN_001
        mismatches.stream()
            .filter(m -> m.getFieldPath() != null && m.getFieldPath().contains("category"))
            .forEach(m -> {
                Assert.assertEquals(m.getPlanId(), "PLAN_001");
                Assert.assertTrue(m.getFieldPath().contains("PLAN_001"),
                    "fieldPath must embed the plan_id");
            });
        log.info("4. Category mismatches correctly tied to PLAN_001");
    }

    // =========================================================================
    // 5 & 6. Missing / extra fund — fund name context
    // =========================================================================

    @Test(groups = "reporting", description = "5. Missing fund shows API-1 fund name and 'NOT FOUND' for API-2")
    public void missingFund_showsApi1FundName() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(
                fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(20000)),
                fund("P2", "Beta Direct-G",  "MC001", "Mid Cap",   bd(15000))),
            List.of());
        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        List<Mismatch> missing = result.getByType(MismatchType.MISSING_FIELD);
        Assert.assertFalse(missing.isEmpty());
        Mismatch m = missing.stream()
            .filter(x -> "P2".equals(x.getPlanId()))
            .findFirst().orElseThrow(() -> new AssertionError("P2 MISSING_FIELD not found"));

        Assert.assertEquals(m.getPlanId(),       "P2");
        Assert.assertEquals(m.getApi1FundName(), "Beta Direct-G");
        Assert.assertEquals(m.getApi2FundName(), "NOT FOUND");
        log.info("5. Missing fund: planId={} api1Fund='{}' api2Fund='{}'",
            m.getPlanId(), m.getApi1FundName(), m.getApi2FundName());
    }

    @Test(groups = "reporting", description = "6. Extra fund shows API-2 fund name and 'NOT FOUND' for API-1")
    public void extraFund_showsApi2FundName() {
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());
        ExtractedResponse r2 = response("API-2", "success",
            List.of(
                fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(20000)),
                fund("P3", "Gamma Direct-G", "SC001", "Small Cap",  bd(10000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        List<Mismatch> extra = result.getByType(MismatchType.EXTRA_FIELD);
        Mismatch m = extra.stream()
            .filter(x -> "P3".equals(x.getPlanId()))
            .findFirst().orElseThrow(() -> new AssertionError("P3 EXTRA_FIELD not found"));

        Assert.assertEquals(m.getPlanId(),       "P3");
        Assert.assertEquals(m.getApi1FundName(), "NOT FOUND");
        Assert.assertEquals(m.getApi2FundName(), "Gamma Direct-G");
        log.info("6. Extra fund: planId={} api1Fund='{}' api2Fund='{}'",
            m.getPlanId(), m.getApi1FundName(), m.getApi2FundName());
    }

    // =========================================================================
    // 7 & 8. Difference and tolerance
    // =========================================================================

    @Test(groups = "reporting", description = "7. Difference is calculated correctly for numeric mismatch")
    public void difference_calculatedCorrectly() {
        Optional<Mismatch> opt = ValueComparator.compareAmounts(
            TC, "investment_amount", new BigDecimal("82000"), new BigDecimal("75000"), TOL);

        Assert.assertTrue(opt.isPresent());
        Mismatch m = opt.get();
        Assert.assertEquals(m.getDifference(), "7000",
            "Difference must be |82000 - 75000| = 7000");
        log.info("7. Difference: '{}' (expected '7000')", m.getDifference());
    }

    @Test(groups = "reporting", description = "8. Tolerance is present in numeric mismatch")
    public void tolerance_presentInMismatch() {
        Optional<Mismatch> opt = ValueComparator.compareAmounts(
            TC, "investment_amount", new BigDecimal("82000"), new BigDecimal("75000"), TOL);

        Assert.assertTrue(opt.isPresent());
        Assert.assertEquals(opt.get().getTolerance(), "0.01");
        log.info("8. Tolerance: '{}' (expected '0.01')", opt.get().getTolerance());
    }

    // =========================================================================
    // 9. NORMALIZATION_DIFFERENCE remains informational
    // =========================================================================

    @Test(groups = "reporting",
          description = "9. NORMALIZATION_DIFFERENCE does not cause FAIL and carries fund names")
    public void normalizationDifference_isInformational() {
        // Raw (un-normalized) names — in real flow normalizer is called before comparator,
        // so NORMALIZATION_DIFFERENCE only appears in unit tests with raw names.
        ExtractedResponse r1 = response("API-1", "success",
            List.of(fund("P1", "Alpha Fund Dir-G",    "LC001", "Large Cap", bd(20000))),
            List.of());
        ExtractedResponse r2 = response("API-2", "success",
            List.of(fund("P1", "Alpha Fund Direct-G", "LC001", "Large Cap", bd(20000))),
            List.of());

        ComparisonResult result = ResponseComparator.compare(TC, r1, r2);

        Assert.assertTrue(result.isPassed(), "NORMALIZATION_DIFFERENCE must not cause FAIL");
        List<Mismatch> normDiffs = result.getByType(MismatchType.NORMALIZATION_DIFFERENCE);
        Assert.assertEquals(normDiffs.size(), 1);
        Assert.assertEquals(normDiffs.get(0).getPlanId(), "P1");
        Assert.assertFalse(normDiffs.get(0).isFailing());
        log.info("9. NORMALIZATION_DIFFERENCE: verdict={} planId={} isFailing={}",
            result.getVerdict(), normDiffs.get(0).getPlanId(), normDiffs.get(0).isFailing());
    }

    // =========================================================================
    // 10. API_EXECUTION_ERROR reported separately
    // =========================================================================

    @Test(groups = "reporting", description = "10. Failed extraction → API_EXECUTION_ERROR, not fund mismatch")
    public void apiExecutionError_reportedSeparately() {
        ExtractedResponse ok     = response("API-1", "success",
            List.of(fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(20000))), List.of());
        ExtractedResponse failed = ExtractedResponse.failed("API-2", "Missing 'data' node");

        ComparisonResult result = ResponseComparator.compare(TC, ok, failed);

        Assert.assertTrue(result.isFailed());
        List<Mismatch> errors = result.getByType(MismatchType.API_EXECUTION_ERROR);
        Assert.assertFalse(errors.isEmpty(), "Must have API_EXECUTION_ERROR");
        // Fund mismatches should NOT be generated when extraction failed
        Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty(),
            "Should not generate MISSING_FIELD when extraction itself failed");
        log.info("10. API_EXECUTION_ERROR correctly isolated from fund mismatches");
    }

    // =========================================================================
    // 11. Multiple mismatches for one fund collected separately
    // =========================================================================

    @Test(groups = "reporting",
          description = "11. Multiple field failures on one fund are individually reported")
    public void multipleMismatchesForOneFund_allCollected() {
        // Same plan_id — different category AND different amount
        FundEntry f1 = fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(82000));
        FundEntry f2 = fund("P1", "Alpha Direct-G", "XX001", "Unknown",   bd(75000));

        List<Mismatch> mismatches = FundComparator.compare(TC, f1, f2, TOL);

        long catMismatches = mismatches.stream()
            .filter(m -> m.getFieldPath() != null && m.getFieldPath().contains("category"))
            .count();
        long amtMismatches = mismatches.stream()
            .filter(m -> m.getFieldPath() != null && m.getFieldPath().contains("investment_amount"))
            .count();

        Assert.assertTrue(catMismatches >= 2, "Both category_id and category_name must be reported");
        Assert.assertEquals(amtMismatches, 1, "Investment amount must be reported");
        Assert.assertTrue(mismatches.size() >= 3, "At least 3 mismatches for one fund");

        // All must share the same planId
        mismatches.forEach(m -> Assert.assertEquals(m.getPlanId(), "P1"));
        log.info("11. {} separate mismatches for plan P1", mismatches.size());
    }

    // =========================================================================
    // 12. Extent report can be initialised
    // =========================================================================

    @Test(groups = "reporting", description = "12. ExtentReportManager initialises without error")
    public void extentReportManager_initialisesSuccessfully() {
        var extent = ExtentReportManager.getInstance();
        Assert.assertNotNull(extent, "ExtentReports instance must not be null");
        log.info("12. ExtentReportManager.getInstance() OK — report: {}",
            ExtentReportManager.REPORT_FILE);
    }

    // =========================================================================
    // 13. Excel detail mismatch format contains fund context
    // =========================================================================

    @Test(groups = "reporting", description = "13. formatMismatchDetails includes fund name and plan_id")
    public void excelMismatchDetails_containsFundContext() {
        FundEntry f1 = fund("P1", "Alpha Dir-G",    "LC001", "Large Cap", bd(82000));
        FundEntry f2 = fund("P1", "Alpha Direct-G", "LC001", "Large Cap", bd(75000));

        ComparisonResult result = ResponseComparator.compare(TC,
            response("API-1", "success", List.of(f1), List.of()),
            response("API-2", "success", List.of(f2), List.of()));

        String details = ExcelResultWriter.formatMismatchDetails(result);
        Assert.assertFalse(details.isBlank(), "Mismatch details must not be blank");
        Assert.assertTrue(details.contains("P1"),               "Must contain planId P1");
        Assert.assertTrue(details.contains("investment_amount"), "Must contain the field name");
        Assert.assertTrue(details.contains("82000"),            "Must contain API-1 value");
        Assert.assertTrue(details.contains("75000"),            "Must contain API-2 value");
        log.info("13. Excel mismatch details contain fund context:\n{}", details);
    }

    // =========================================================================
    // ComparisonReportFormatter helpers
    // =========================================================================

    @Test(groups = "reporting", description = "friendlyField extracts trailing segment from fieldPath")
    public void friendlyField_extractsLastSegment() {
        Assert.assertEquals(
            ComparisonReportFormatter.friendlyField("funds_data.data[plan_id=P1].investment_amount"),
            "investment_amount");
        Assert.assertEquals(
            ComparisonReportFormatter.friendlyField("breakdown_funds_data.data[category_id=LC001].percentage"),
            "percentage");
        Assert.assertEquals(
            ComparisonReportFormatter.friendlyField("http.status"),
            "status");
    }

    // =========================================================================
    // Helpers
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

    private BigDecimal bd(long v) { return BigDecimal.valueOf(v); }
}
