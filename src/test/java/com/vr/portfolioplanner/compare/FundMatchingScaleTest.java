package com.vr.portfolioplanner.compare;

import com.vr.portfolioplanner.altfund.AlternateFundAudit;
import com.vr.portfolioplanner.altfund.FundDetailsClient;
import com.vr.portfolioplanner.altfund.FundDetailsSnapshot;
import com.vr.portfolioplanner.altfund.FundOpinionClient;
import com.vr.portfolioplanner.altfund.FundOpinionSnapshot;
import com.vr.portfolioplanner.altfund.MatchType;
import com.vr.portfolioplanner.response.model.BreakdownEntry;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import com.vr.portfolioplanner.response.model.FundEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regression suite proving the fund matching/pairing algorithm in
 * {@link ResponseComparator#compare} + {@link com.vr.portfolioplanner.altfund.AlternateFundValidator}
 * is deterministic, Map/Set-keyed (never positional/index-based), and scales
 * correctly well past 5 funds per side — covering fund counts 1, 2, 5, 6, 7 and
 * 10+, and scenarios A–J from the fund-matching audit.
 *
 * <p>Every test also asserts the accounting invariant: total API-1 funds =
 * exact matches + alternate matches + rejected/ambiguous/unmatched API-1
 * funds (and symmetrically for API-2), and that no {@code plan_id} appears in
 * more than one of {matched, alternate-matched, missing, extra}.
 */
public class FundMatchingScaleTest {

    private static final Logger log = LoggerFactory.getLogger(FundMatchingScaleTest.class);
    private static final String TC = "TC_SCALE";

    // =========================================================================
    // Data provider: fund counts to sweep for pure exact-match scaling (Scenario A)
    // =========================================================================

    @DataProvider(name = "fundCounts")
    public Object[][] fundCounts() {
        return new Object[][] { {1}, {2}, {5}, {6}, {7}, {10}, {15} };
    }

    // =========================================================================
    // Scenario A: all exact plan_id matches, swept across fund counts
    // =========================================================================

    @Test(groups = "compare", dataProvider = "fundCounts",
          description = "Scenario A: N identical funds matched purely by plan_id, N in {1,2,5,6,7,10,15}")
    public void scenarioA_allExactMatches_scalesPastFive(int n) {
        List<FundEntry> funds1 = new ArrayList<>();
        List<FundEntry> funds2 = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            funds1.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
            funds2.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
        }
        ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

        Assert.assertTrue(result.isPassed(), "N=" + n + " identical funds must PASS: " + result.getFailingMismatches());
        Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
        Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
        Assert.assertTrue(result.getByType(MismatchType.COUNT_MISMATCH).isEmpty());
        assertNoDoubleCounting(result, funds1, funds2);
        assertAccounting(result, funds1.size(), funds2.size());
        log.info("Scenario A (N={} exact matches): {}", n, result);
    }

    // =========================================================================
    // Scenario B: exact matches + one valid alternate — at N=6 and N=7
    // =========================================================================

    @Test(groups = "compare", dataProvider = "sixAndSeven",
          description = "Scenario B: N-1 exact matches + exactly one valid category-based alternate")
    public void scenarioB_exactMatchesPlusOneAlternate(int n) {
        List<FundEntry> funds1 = new ArrayList<>();
        List<FundEntry> funds2 = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            funds1.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
            funds2.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
        }
        // Orphan pair: different plan_id, same category_name.
        funds1.add(fund("ALT1", "Alternate Fund Direct-G", "LQ1", "Liquid", bd(5000)));
        funds2.add(fund("ORIG1", "Original Fund Direct-G", "LQ1", "Liquid", bd(5000)));

        FundDetailsClient.primeForTest(Map.of(
            "ALT1", FundDetailsSnapshot.found("ALT1", "Liquid"),
            "ORIG1", FundDetailsSnapshot.found("ORIG1", "Liquid")));
        FundOpinionClient.primeForTest(Map.of(
            "ALT1", FundOpinionSnapshot.found("ALT1", 1, false, 4, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertTrue(result.isPassed(), "N=" + n + " must PASS: " + result.getFailingMismatches());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 1);
            Assert.assertEquals(result.getAlternateFundAudits().get(0).getMatchType(), MatchType.ALTERNATE_MATCH);
            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            assertNoDoubleCounting(result, funds1, funds2);
            assertAccounting(result, funds1.size(), funds2.size());
            log.info("Scenario B (N={}, 1 alternate): {}", n, result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    @DataProvider(name = "sixAndSeven")
    public Object[][] sixAndSeven() {
        return new Object[][] { {6}, {7} };
    }

    // =========================================================================
    // Scenario C: exact matches + multiple valid alternates, at N=10
    // =========================================================================

    @Test(groups = "compare", description = "Scenario C: 7 exact matches + 3 valid category-based alternates (N=10)")
    public void scenarioC_multipleValidAlternates_tenFunds() {
        List<FundEntry> funds1 = new ArrayList<>();
        List<FundEntry> funds2 = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            funds1.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
            funds2.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
        }
        funds1.add(fund("ALT_LQ", "Alt Liquid Direct-G", "LQ", "Liquid", bd(6000)));
        funds2.add(fund("ORIG_LQ", "Orig Liquid Direct-G", "LQ", "Liquid", bd(6000)));
        funds1.add(fund("ALT_MC", "Alt Mid Cap Direct-G", "MC", "Mid Cap", bd(7000)));
        funds2.add(fund("ORIG_MC", "Orig Mid Cap Direct-G", "MC", "Mid Cap", bd(7000)));
        funds1.add(fund("ALT_SC", "Alt Small Cap Direct-G", "SC", "Small Cap", bd(8000)));
        funds2.add(fund("ORIG_SC", "Orig Small Cap Direct-G", "SC", "Small Cap", bd(8000)));

        FundDetailsClient.primeForTest(Map.of(
            "ALT_LQ", FundDetailsSnapshot.found("ALT_LQ", "Liquid"), "ORIG_LQ", FundDetailsSnapshot.found("ORIG_LQ", "Liquid"),
            "ALT_MC", FundDetailsSnapshot.found("ALT_MC", "Mid Cap"), "ORIG_MC", FundDetailsSnapshot.found("ORIG_MC", "Mid Cap"),
            "ALT_SC", FundDetailsSnapshot.found("ALT_SC", "Small Cap"), "ORIG_SC", FundDetailsSnapshot.found("ORIG_SC", "Small Cap")));
        FundOpinionClient.primeForTest(Map.of(
            "ALT_LQ", FundOpinionSnapshot.found("ALT_LQ", 1, false, 4, null),
            "ALT_MC", FundOpinionSnapshot.found("ALT_MC", 1, false, 5, null),
            "ALT_SC", FundOpinionSnapshot.found("ALT_SC", 1, false, 4, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertTrue(result.isPassed(), "10-fund case must PASS: " + result.getFailingMismatches());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 3, "each pair produces its own audit row");
            Assert.assertTrue(result.getAlternateFundAudits().stream()
                .allMatch(a -> a.getMatchType() == MatchType.ALTERNATE_MATCH));
            assertNoDoubleCounting(result, funds1, funds2);
            assertAccounting(result, funds1.size(), funds2.size());
            log.info("Scenario C (N=10, 3 alternates): {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Scenario D: same category_name, very different plan_id numbering — must still pair
    // =========================================================================

    @Test(groups = "compare", description = "Scenario D: same category_name but wildly different plan_id must still pair "
                       + "(category_name is the only pairing key, plan_id value/shape is irrelevant)")
    public void scenarioD_sameCategoryDifferentPlanId_mustPair() {
        List<FundEntry> funds1 = List.of(fund("999999", "Fund Z Direct-G", "L1", "Liquid", bd(1000)));
        List<FundEntry> funds2 = List.of(fund("1", "Fund A Direct-G", "L1", "Liquid", bd(1000)));

        FundDetailsClient.primeForTest(Map.of(
            "999999", FundDetailsSnapshot.found("999999", "Liquid"),
            "1", FundDetailsSnapshot.found("1", "Liquid")));
        FundOpinionClient.primeForTest(Map.of(
            "999999", FundOpinionSnapshot.found("999999", 1, false, 4, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertEquals(result.getAlternateFundAudits().size(), 1);
            Assert.assertEquals(result.getAlternateFundAudits().get(0).getMatchType(), MatchType.ALTERNATE_MATCH);
            Assert.assertEquals(result.getAlternateFundAudits().get(0).getApi1PlanId(), "999999");
            Assert.assertEquals(result.getAlternateFundAudits().get(0).getApi2PlanId(), "1");
            log.info("Scenario D (mismatched plan_id shape, same category): {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Scenario E: different category_name — must NOT pair
    // =========================================================================

    @Test(groups = "compare", description = "Scenario E: different category_name AND unequal total fund "
                       + "count must NOT be paired at all — not by category (no shared category_name) "
                       + "and not by the serial fallback (counts don't line up, so it's not eligible)")
    public void scenarioE_differentCategoryUnequalTotals_mustNotPair() {
        List<FundEntry> funds1 = List.of(
            fund("16897", "Dynamic Alt Direct-G", "D1", "Dynamic Asset Allocation", bd(7000)),
            fund("EXTRA1", "Extra Fund Direct-G", "EX1", "Extra Category", bd(3000)));
        List<FundEntry> funds2 = List.of(fund("38387", "Equity Savings Orig Direct-G", "E1", "Equity Savings", bd(7000)));

        FundDetailsClient.primeForTest(Map.of(
            "16897", FundDetailsSnapshot.found("16897", "Dynamic Asset Allocation"),
            "EXTRA1", FundDetailsSnapshot.found("EXTRA1", "Extra Category"),
            "38387", FundDetailsSnapshot.found("38387", "Equity Savings")));
        try {
            Assert.assertNotEquals(funds1.size(), funds2.size(), "totals must genuinely differ for this scenario");
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertTrue(result.getAlternateFundAudits().isEmpty(),
                "different category_name with unequal totals must never produce an alternate audit/pairing");
            List<String> missingPlanIds = result.getByType(MismatchType.MISSING_FIELD).stream()
                .map(Mismatch::getPlanId).toList();
            List<String> extraPlanIds = result.getByType(MismatchType.EXTRA_FIELD).stream()
                .map(Mismatch::getPlanId).toList();
            Assert.assertTrue(missingPlanIds.containsAll(List.of("16897", "EXTRA1")));
            Assert.assertTrue(extraPlanIds.contains("38387"));
            log.info("Scenario E (different category, unequal totals, must not pair): {}", result);
        } finally {
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Scenario F: equal fund count but genuinely unmatched funds
    // =========================================================================

    @Test(groups = "compare", description = "Scenario F: equal TOTAL fund count (8=8) but an ambiguous "
                       + "category group consumes asymmetric counts (2 from API-1, 1 from API-2), leaving "
                       + "unequal remaining counts (1 vs 2) afterward — serial fallback must correctly "
                       + "refuse (equal totals alone are not sufficient; the still-unresolved counts must "
                       + "also be equal), so the leftover funds genuinely fall through to MISSING/EXTRA")
    public void scenarioF_equalTotalsButAsymmetricRemainder_mustNotFallBackToSerial() {
        List<FundEntry> funds1 = new ArrayList<>();
        List<FundEntry> funds2 = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            funds1.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
            funds2.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
        }
        // Ambiguous group: 2 API-1 candidates vs 1 API-2 candidate, same category — asymmetric consumption.
        funds1.add(fund("R1", "Replacement One Direct-G", "LC1", "Large Cap", bd(15000)));
        funds1.add(fund("R2", "Replacement Two Direct-G", "LC2", "Large Cap", bd(16000)));
        funds2.add(fund("O1", "Original One Direct-G", "LC1", "Large Cap", bd(15000)));
        // Genuinely different, unrelated categories on each side beyond the ambiguous group.
        funds1.add(fund("Z1", "Z1 Direct-G", "ZZ", "Zeta Category", bd(9000)));
        funds2.add(fund("Z2A", "Z2A Direct-G", "OM1", "Omega Category One", bd(4000)));
        funds2.add(fund("Z2B", "Z2B Direct-G", "OM2", "Omega Category Two", bd(5000)));

        FundDetailsClient.primeForTest(Map.of(
            "R1", FundDetailsSnapshot.found("R1", "Large Cap"),
            "R2", FundDetailsSnapshot.found("R2", "Large Cap"),
            "O1", FundDetailsSnapshot.found("O1", "Large Cap"),
            "Z1", FundDetailsSnapshot.found("Z1", "Zeta Category"),
            "Z2A", FundDetailsSnapshot.found("Z2A", "Omega Category One"),
            "Z2B", FundDetailsSnapshot.found("Z2B", "Omega Category Two")));
        try {
            Assert.assertEquals(funds1.size(), funds2.size(), "totals must be equal for this scenario (8=8)");
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertTrue(result.isFailed(), "equal totals must not force a PASS when funds are genuinely unmatched");
            Assert.assertTrue(result.getByType(MismatchType.COUNT_MISMATCH).isEmpty(), "totals really are equal");
            Assert.assertEquals(result.getByType(MismatchType.ALTERNATE_FUND_PAIRING_AMBIGUOUS).size(), 3,
                "R1, R2, O1 must all be reported ambiguous");
            List<String> missingPlanIds = result.getByType(MismatchType.MISSING_FIELD).stream()
                .map(Mismatch::getPlanId).toList();
            List<String> extraPlanIds = result.getByType(MismatchType.EXTRA_FIELD).stream()
                .map(Mismatch::getPlanId).toList();
            Assert.assertTrue(missingPlanIds.contains("Z1"), "Z1 has no category or serial candidate — must be MISSING_FIELD");
            Assert.assertTrue(extraPlanIds.containsAll(List.of("Z2A", "Z2B")),
                "Z2A/Z2B have no category or serial candidate — must be EXTRA_FIELD");
            Assert.assertTrue(result.getAlternateFundAudits().stream()
                    .noneMatch(a -> a.getMatchType() == MatchType.SERIAL_ALTERNATE_MATCH
                        || a.getMatchType() == MatchType.SERIAL_ALTERNATE_REJECTED),
                "serial fallback must never fire when the still-unresolved counts are unequal (1 vs 2)");
            assertNoDoubleCounting(result, funds1, funds2);
            assertAccounting(result, funds1.size(), funds2.size());
            log.info("Scenario F (equal totals, asymmetric ambiguous consumption, no serial fallback): {}", result);
        } finally {
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Scenario G: different fund count
    // =========================================================================

    @Test(groups = "compare", description = "Scenario G: different fund count → COUNT_MISMATCH, matching still proceeds for the rest")
    public void scenarioG_differentFundCount() {
        List<FundEntry> funds1 = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            funds1.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
        }
        List<FundEntry> funds2 = new ArrayList<>(funds1.subList(0, 6));

        ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

        Assert.assertFalse(result.getByType(MismatchType.COUNT_MISMATCH).isEmpty());
        Assert.assertEquals(result.getByType(MismatchType.MISSING_FIELD).size(), 2, "P7, P8 present only in API-1");
        assertNoDoubleCounting(result, funds1, funds2);
        assertAccounting(result, funds1.size(), funds2.size());
        log.info("Scenario G (8 vs 6 funds): {}", result);
    }

    // =========================================================================
    // Scenario H: multiple candidates share one category_name → PAIRING_AMBIGUOUS
    // =========================================================================

    @Test(groups = "compare", description = "Scenario H: 2 API-1 candidates share one category_name against 1 API-2 "
                       + "original → PAIRING_AMBIGUOUS for all 3, none arbitrarily selected")
    public void scenarioH_multipleCandidatesSameCategory_ambiguous() {
        List<FundEntry> funds1 = List.of(
            fund("R1", "Replacement One Direct-G", "LC001", "Large Cap", bd(15000)),
            fund("R2", "Replacement Two Direct-G", "LC002", "Large Cap", bd(16000)));
        List<FundEntry> funds2 = List.of(
            fund("O1", "Original One Direct-G", "LC001", "Large Cap", bd(15000)));

        FundDetailsClient.primeForTest(Map.of(
            "R1", FundDetailsSnapshot.found("R1", "Large Cap"),
            "R2", FundDetailsSnapshot.found("R2", "Large Cap"),
            "O1", FundDetailsSnapshot.found("O1", "Large Cap")));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertEquals(result.getByType(MismatchType.ALTERNATE_FUND_PAIRING_AMBIGUOUS).size(), 3);
            Assert.assertEquals(result.getAlternateFundAudits().size(), 3);
            Assert.assertTrue(result.getAlternateFundAudits().stream()
                .allMatch(a -> a.getMatchType() == MatchType.PAIRING_AMBIGUOUS));
            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty(),
                "ambiguous candidates must not fall through to MISSING_FIELD");
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty(),
                "ambiguous candidates must not fall through to EXTRA_FIELD");
            log.info("Scenario H (ambiguous pairing): {}", result);
        } finally {
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Scenario I: exact match + alternate match + genuine amount mismatch
    // =========================================================================

    @Test(groups = "compare", description = "Scenario I: exact match with a genuine amount mismatch coexists correctly "
                       + "with a valid alternate match elsewhere in the same response")
    public void scenarioI_exactPlusAlternatePlusGenuineAmountMismatch() {
        List<FundEntry> funds1 = List.of(
            fund("P1", "Fund One Direct-G", "C1", "Category 1", bd(20000)),
            fund("ALT1", "Alt Liquid Direct-G", "LQ", "Liquid", bd(5000)));
        List<FundEntry> funds2 = List.of(
            fund("P1", "Fund One Direct-G", "C1", "Category 1", bd(15000)), // genuine amount mismatch
            fund("ORIG1", "Orig Liquid Direct-G", "LQ", "Liquid", bd(5000)));

        FundDetailsClient.primeForTest(Map.of(
            "ALT1", FundDetailsSnapshot.found("ALT1", "Liquid"),
            "ORIG1", FundDetailsSnapshot.found("ORIG1", "Liquid")));
        FundOpinionClient.primeForTest(Map.of(
            "ALT1", FundOpinionSnapshot.found("ALT1", 1, false, 4, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertTrue(result.isFailed(), "genuine amount mismatch on the exact-matched fund must still fail");
            Assert.assertFalse(result.getByType(MismatchType.BUSINESS_VALUE_MISMATCH).isEmpty());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 1);
            Assert.assertEquals(result.getAlternateFundAudits().get(0).getMatchType(), MatchType.ALTERNATE_MATCH,
                "the unrelated valid alternate pair must still resolve correctly despite the other fund's amount mismatch");
            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            log.info("Scenario I (exact+alternate+genuine mismatch): {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Scenario J: TC_05264 shape — 7 funds/side, 5 exact + 1 valid alternate + 1 must-not-pair
    // =========================================================================

    @Test(groups = "compare", description = "Scenario J: TC_05264 shape — 5 exact matches (15752, 16151, 15866, 41574, "
                       + "15829), category alternate 15800(Liquid)->16554(Liquid), and the remaining "
                       + "38387(Equity Savings)/16897(Dynamic Asset Allocation) — no shared category, but "
                       + "totals and remaining counts line up, so serial fallback pairs them and runs "
                       + "business-rule validation rather than reporting MISSING/EXTRA outright")
    public void scenarioJ_tc05264Shape() {
        List<FundEntry> funds1 = List.of(
            fund("15752", "Fund A Direct-G", "C1", "Cat1", bd(1000)),
            fund("16151", "Fund B Direct-G", "C2", "Cat2", bd(2000)),
            fund("15866", "Fund C Direct-G", "C3", "Cat3", bd(3000)),
            fund("41574", "Fund D Direct-G", "C4", "Cat4", bd(4000)),
            fund("15829", "Fund E Direct-G", "C5", "Cat5", bd(5000)),
            fund("16554", "Mirae Asset Liquid Direct-G", "L1", "Debt: Liquid", bd(6000)),
            fund("16897", "Aditya Birla SL Balanced Advantage Direct-G", "D1", "Dynamic Asset Allocation", bd(7000)));

        List<FundEntry> funds2 = List.of(
            fund("15752", "Fund A Direct-G", "C1", "Cat1", bd(1000)),
            fund("16151", "Fund B Direct-G", "C2", "Cat2", bd(2000)),
            fund("15866", "Fund C Direct-G", "C3", "Cat3", bd(3000)),
            fund("41574", "Fund D Direct-G", "C4", "Cat4", bd(4000)),
            fund("15829", "Fund E Direct-G", "C5", "Cat5", bd(5000)),
            fund("15800", "Aditya Birla SL Liquid Direct-G", "L1", "Debt: Liquid", bd(6000)),
            fund("38387", "Mirae Asset Equity Savings Direct-G", "E1", "Equity Savings", bd(7000)));

        FundDetailsClient.primeForTest(Map.of(
            "16554", FundDetailsSnapshot.found("16554", "Debt: Liquid"),
            "15800", FundDetailsSnapshot.found("15800", "Debt: Liquid"),
            "16897", FundDetailsSnapshot.found("16897", "Dynamic Asset Allocation"),
            "38387", FundDetailsSnapshot.found("38387", "Equity Savings")));
        FundOpinionClient.primeForTest(Map.of(
            "16554", FundOpinionSnapshot.found("16554", 1, true, 4, null),
            "16897", FundOpinionSnapshot.found("16897", 1, false, 4, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            // 5 exact matches by plan_id.
            for (String exact : List.of("15752", "16151", "15866", "41574", "15829")) {
                Assert.assertTrue(result.getMismatches().stream()
                        .noneMatch(m -> exact.equals(m.getPlanId())
                            && (m.getMismatchType() == MismatchType.MISSING_FIELD
                                || m.getMismatchType() == MismatchType.EXTRA_FIELD)),
                    exact + " must be an exact plan_id match, not missing/extra");
            }

            Assert.assertEquals(result.getAlternateFundAudits().size(), 2);

            // Category alternate: 15800(API-2 original) <-> 16554(API-1 alternate) — Debt: Liquid.
            AlternateFundAudit categoryAudit = result.getAlternateFundAudits().stream()
                .filter(a -> "15800".equals(a.getApi2PlanId())).findFirst().orElseThrow();
            Assert.assertEquals(categoryAudit.getApi1PlanId(), "16554");
            Assert.assertEquals(categoryAudit.getMatchType(), MatchType.ALTERNATE_MATCH);

            // Serial fallback: 38387(API-2 original) <-> 16897(API-1 alternate) — no shared category_name,
            // but totals (7=7) and remaining counts (1=1) line up, so serial pairing runs business-rule
            // validation instead of reporting MISSING/EXTRA outright.
            AlternateFundAudit serialAudit = result.getAlternateFundAudits().stream()
                .filter(a -> "38387".equals(a.getApi2PlanId())).findFirst().orElseThrow();
            Assert.assertEquals(serialAudit.getApi1PlanId(), "16897");
            Assert.assertEquals(serialAudit.getMatchType(), MatchType.SERIAL_ALTERNATE_MATCH);

            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty(),
                "16897 must be validated via serial fallback, never reported MISSING_FIELD outright");
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty(),
                "38387 must be validated via serial fallback, never reported EXTRA_FIELD outright");

            assertNoDoubleCounting(result, funds1, funds2);
            assertAccounting(result, funds1.size(), funds2.size());
            log.info("Scenario J (TC_05264 shape): {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Invariant helpers
    // =========================================================================

    /**
     * Rule 11/12: every fund must have exactly one final state — never present
     * simultaneously in more than one of {directly-matched, alternate-matched,
     * missing, extra}.
     */
    private void assertNoDoubleCounting(ComparisonResult result, List<FundEntry> funds1, List<FundEntry> funds2) {
        Set<String> ids1 = new HashSet<>();
        for (FundEntry f : funds1) ids1.add(f.getPlanId());
        Set<String> ids2 = new HashSet<>();
        for (FundEntry f : funds2) ids2.add(f.getPlanId());

        Set<String> directMatched = new HashSet<>(ids1);
        directMatched.retainAll(ids2);

        Set<String> alternateApi1 = new HashSet<>();
        Set<String> alternateApi2 = new HashSet<>();
        for (AlternateFundAudit a : result.getAlternateFundAudits()) {
            if (a.getMatchType().isAcceptedMatch()) {
                alternateApi1.add(a.getApi1PlanId());
                alternateApi2.add(a.getApi2PlanId());
            }
        }

        Set<String> missing = result.getByType(MismatchType.MISSING_FIELD).stream()
            .map(Mismatch::getPlanId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<String> extra = result.getByType(MismatchType.EXTRA_FIELD).stream()
            .map(Mismatch::getPlanId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());

        for (String id : ids1) {
            int states = (directMatched.contains(id) ? 1 : 0)
                       + (alternateApi1.contains(id) ? 1 : 0)
                       + (missing.contains(id) ? 1 : 0);
            Assert.assertTrue(states <= 1,
                "API-1 plan_id=" + id + " must have exactly one final state, found " + states);
        }
        for (String id : ids2) {
            int states = (directMatched.contains(id) ? 1 : 0)
                       + (alternateApi2.contains(id) ? 1 : 0)
                       + (extra.contains(id) ? 1 : 0);
            Assert.assertTrue(states <= 1,
                "API-2 plan_id=" + id + " must have exactly one final state, found " + states);
        }
    }

    /**
     * Rule 12: total API-1 funds = exact matches + alternate matches +
     * rejected/ambiguous/data-gap/unmatched API-1 funds (symmetrically for API-2).
     */
    private void assertAccounting(ComparisonResult result, int totalApi1, int totalApi2) {
        long alternateMatched = result.getAlternateFundAudits().stream()
            .filter(a -> a.getMatchType().isAcceptedMatch()).count();
        long api1OtherOutcomes = result.getAlternateFundAudits().stream()
            .filter(a -> !a.getMatchType().isAcceptedMatch() && !a.getApi1PlanId().isEmpty())
            .count() + result.getByType(MismatchType.MISSING_FIELD).size();
        long api2OtherOutcomes = result.getAlternateFundAudits().stream()
            .filter(a -> !a.getMatchType().isAcceptedMatch() && !a.getApi2PlanId().isEmpty())
            .count() + result.getByType(MismatchType.EXTRA_FIELD).size();

        long exact = totalApi1 - alternateMatched - api1OtherOutcomes;
        Assert.assertEquals(totalApi1, exact + alternateMatched + api1OtherOutcomes,
            "API-1 accounting must balance: total = exact + alternate + rejected/ambiguous/unmatched");

        long exact2 = totalApi2 - alternateMatched - api2OtherOutcomes;
        Assert.assertEquals(exact, exact2, "exact-match count must agree from both sides");
        Assert.assertEquals(totalApi2, exact2 + alternateMatched + api2OtherOutcomes,
            "API-2 accounting must balance: total = exact + alternate + missing/rejected/ambiguous/unmatched");
    }

    // =========================================================================
    // Test data builders
    // =========================================================================

    private ExtractedResponse response(List<FundEntry> funds) {
        return ExtractedResponse.success("API", "success", null, "investor-test", funds, List.<BreakdownEntry>of(), List.of());
    }

    private FundEntry fund(String planId, String planName, String catId, String catFmt, BigDecimal amount) {
        return FundEntry.builder().planId(planId).planName(planName)
            .categoryId(catId).categoryFmt(catFmt).amount(amount).legs(List.of()).build();
    }

    private BigDecimal bd(long v) { return BigDecimal.valueOf(v); }
}
