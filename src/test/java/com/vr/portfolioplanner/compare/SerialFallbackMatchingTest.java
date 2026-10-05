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
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Regression suite for the serial/order fallback pairing mechanism added to
 * {@link com.vr.portfolioplanner.altfund.AlternateFundValidator}: after exact
 * {@code plan_id} matching AND category_name matching have both been
 * attempted, any funds still left over are paired strictly by their original
 * response order/position, but ONLY when API-1's total fund count equals
 * API-2's total fund count AND the still-unresolved counts on both sides are
 * equal. Category-name pairing always takes priority — serial pairing only
 * ever consumes what it left genuinely unresolved.
 *
 * <p>Covers fund counts 5, 6, 7, 8; equal-total-with-replacements;
 * unequal-total (no fallback); and multiple simultaneous serial replacements
 * with orphans interleaved among exact matches (proving no re-sort happens).
 */
public class SerialFallbackMatchingTest {

    private static final Logger log = LoggerFactory.getLogger(SerialFallbackMatchingTest.class);
    private static final String TC = "TC_SERIAL";

    // =========================================================================
    // 5 funds — all exact, serial fallback never engages
    // =========================================================================

    @Test(groups = "compare", description = "5 funds, all exact matches — serial fallback must not engage "
                       + "when there is nothing left unresolved")
    public void fiveFunds_allExact_noFallback() {
        List<FundEntry> funds1 = exactFunds(5);
        List<FundEntry> funds2 = exactFunds(5);

        ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

        Assert.assertTrue(result.isPassed(), "all-exact 5-fund case must PASS: " + result.getFailingMismatches());
        Assert.assertTrue(result.getAlternateFundAudits().isEmpty());
        log.info("5 funds, all exact: {}", result);
    }

    // =========================================================================
    // 6 funds — 5 exact + 1 serial replacement
    // =========================================================================

    @Test(groups = "compare", description = "6 funds: 5 exact matches + 1 fund with no category_name "
                       + "match on either side, counts line up (1=1, totals 6=6) — serial fallback pairs "
                       + "it and runs business-rule validation")
    public void sixFunds_oneSerialReplacement() {
        List<FundEntry> funds1 = new ArrayList<>(exactFunds(5));
        List<FundEntry> funds2 = new ArrayList<>(exactFunds(5));
        funds1.add(fund("ALT1", "Alt Fund Direct-G", "AA", "Alpha Category", bd(6000)));
        funds2.add(fund("ORIG1", "Orig Fund Direct-G", "BB", "Beta Category", bd(6000)));

        FundDetailsClient.primeForTest(Map.of(
            "ALT1", FundDetailsSnapshot.found("ALT1", "Alpha Category"),
            "ORIG1", FundDetailsSnapshot.found("ORIG1", "Beta Category")));
        FundOpinionClient.primeForTest(Map.of(
            "ALT1", FundOpinionSnapshot.found("ALT1", 1, false, 4, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 1);
            AlternateFundAudit audit = result.getAlternateFundAudits().get(0);
            Assert.assertEquals(audit.getMatchType(), MatchType.SERIAL_ALTERNATE_MATCH);
            Assert.assertEquals(audit.getApi1PlanId(), "ALT1");
            Assert.assertEquals(audit.getApi2PlanId(), "ORIG1");
            log.info("6 funds, 1 serial replacement: {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // 7 funds — 6 exact + 1 serial replacement
    // =========================================================================

    @Test(groups = "compare", description = "7 funds: 6 exact matches + 1 serial-fallback replacement")
    public void sevenFunds_oneSerialReplacement() {
        List<FundEntry> funds1 = new ArrayList<>(exactFunds(6));
        List<FundEntry> funds2 = new ArrayList<>(exactFunds(6));
        funds1.add(fund("ALT1", "Alt Fund Direct-G", "AA", "Alpha Category", bd(7000)));
        funds2.add(fund("ORIG1", "Orig Fund Direct-G", "BB", "Beta Category", bd(7000)));

        FundDetailsClient.primeForTest(Map.of(
            "ALT1", FundDetailsSnapshot.found("ALT1", "Alpha Category"),
            "ORIG1", FundDetailsSnapshot.found("ORIG1", "Beta Category")));
        FundOpinionClient.primeForTest(Map.of(
            "ALT1", FundOpinionSnapshot.found("ALT1", 1, false, 4, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 1);
            Assert.assertEquals(result.getAlternateFundAudits().get(0).getMatchType(), MatchType.SERIAL_ALTERNATE_MATCH);
            log.info("7 funds, 1 serial replacement: {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // 8 funds — multiple serial replacements, interleaved among exact matches
    // =========================================================================

    @Test(groups = "compare", description = "8 funds: 6 exact matches + 2 serial-fallback replacements, "
                       + "with the orphans interleaved among the exact matches (not clustered at the end) "
                       + "to prove pairing follows ORIGINAL response order, never a re-sort")
    public void eightFunds_multipleSerialReplacements_interleavedOrder() {
        // Position 1: exact. Position 2: orphan A. Positions 3-6: exact. Position 7: orphan B. Position 8: exact.
        List<FundEntry> funds1 = List.of(
            fund("P1", "Fund 1 Direct-G", "C1", "Category 1", bd(1000)),
            fund("ALT_A", "Alt A Direct-G", "AA", "Alpha Category", bd(2000)),
            fund("P3", "Fund 3 Direct-G", "C3", "Category 3", bd(3000)),
            fund("P4", "Fund 4 Direct-G", "C4", "Category 4", bd(4000)),
            fund("P5", "Fund 5 Direct-G", "C5", "Category 5", bd(5000)),
            fund("P6", "Fund 6 Direct-G", "C6", "Category 6", bd(6000)),
            fund("ALT_B", "Alt B Direct-G", "CC", "Gamma Category", bd(7000)),
            fund("P8", "Fund 8 Direct-G", "C8", "Category 8", bd(8000)));

        List<FundEntry> funds2 = List.of(
            fund("P1", "Fund 1 Direct-G", "C1", "Category 1", bd(1000)),
            fund("ORIG_A", "Orig A Direct-G", "DD", "Delta Category", bd(2000)),
            fund("P3", "Fund 3 Direct-G", "C3", "Category 3", bd(3000)),
            fund("P4", "Fund 4 Direct-G", "C4", "Category 4", bd(4000)),
            fund("P5", "Fund 5 Direct-G", "C5", "Category 5", bd(5000)),
            fund("P6", "Fund 6 Direct-G", "C6", "Category 6", bd(6000)),
            fund("ORIG_B", "Orig B Direct-G", "EE", "Epsilon Category", bd(7000)),
            fund("P8", "Fund 8 Direct-G", "C8", "Category 8", bd(8000)));

        FundDetailsClient.primeForTest(Map.of(
            "ALT_A", FundDetailsSnapshot.found("ALT_A", "Alpha Category"),
            "ORIG_A", FundDetailsSnapshot.found("ORIG_A", "Delta Category"),
            "ALT_B", FundDetailsSnapshot.found("ALT_B", "Gamma Category"),
            "ORIG_B", FundDetailsSnapshot.found("ORIG_B", "Epsilon Category")));
        FundOpinionClient.primeForTest(Map.of(
            "ALT_A", FundOpinionSnapshot.found("ALT_A", 1, false, 4, null),
            "ALT_B", FundOpinionSnapshot.found("ALT_B", 1, false, 5, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 2);

            // Pairing must follow ORIGINAL response order: ALT_A (1st orphan in API-1) <-> ORIG_A
            // (1st orphan in API-2), ALT_B (2nd) <-> ORIG_B (2nd) — never cross-paired.
            AlternateFundAudit firstPair = result.getAlternateFundAudits().stream()
                .filter(a -> "ALT_A".equals(a.getApi1PlanId())).findFirst().orElseThrow();
            Assert.assertEquals(firstPair.getApi2PlanId(), "ORIG_A");
            Assert.assertEquals(firstPair.getMatchType(), MatchType.SERIAL_ALTERNATE_MATCH);

            AlternateFundAudit secondPair = result.getAlternateFundAudits().stream()
                .filter(a -> "ALT_B".equals(a.getApi1PlanId())).findFirst().orElseThrow();
            Assert.assertEquals(secondPair.getApi2PlanId(), "ORIG_B");
            Assert.assertEquals(secondPair.getMatchType(), MatchType.SERIAL_ALTERNATE_MATCH);

            log.info("8 funds, 2 interleaved serial replacements (original order preserved): {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Equal total count with replacements
    // =========================================================================

    @Test(groups = "compare", description = "Equal total fund count (10=10) with 3 serial replacements "
                       + "— all pair correctly by position")
    public void equalTotalCount_withReplacements() {
        List<FundEntry> funds1 = new ArrayList<>(exactFunds(7));
        List<FundEntry> funds2 = new ArrayList<>(exactFunds(7));
        for (int i = 1; i <= 3; i++) {
            funds1.add(fund("ALT" + i, "Alt " + i + " Direct-G", "A" + i, "AlphaCat" + i, bd(1000L * i)));
            funds2.add(fund("ORIG" + i, "Orig " + i + " Direct-G", "B" + i, "BetaCat" + i, bd(1000L * i)));
        }

        Map<String, FundDetailsSnapshot> details = new java.util.HashMap<>();
        Map<String, FundOpinionSnapshot> opinions = new java.util.HashMap<>();
        for (int i = 1; i <= 3; i++) {
            details.put("ALT" + i, FundDetailsSnapshot.found("ALT" + i, "AlphaCat" + i));
            details.put("ORIG" + i, FundDetailsSnapshot.found("ORIG" + i, "BetaCat" + i));
            opinions.put("ALT" + i, FundOpinionSnapshot.found("ALT" + i, 1, false, 4, null));
        }
        FundDetailsClient.primeForTest(details);
        FundOpinionClient.primeForTest(opinions);
        try {
            Assert.assertEquals(funds1.size(), funds2.size(), "totals must be equal (10=10)");
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertTrue(result.getByType(MismatchType.MISSING_FIELD).isEmpty());
            Assert.assertTrue(result.getByType(MismatchType.EXTRA_FIELD).isEmpty());
            Assert.assertEquals(result.getAlternateFundAudits().size(), 3);
            Assert.assertTrue(result.getAlternateFundAudits().stream()
                .allMatch(a -> a.getMatchType() == MatchType.SERIAL_ALTERNATE_MATCH));
            for (int i = 1; i <= 3; i++) {
                final int idx = i;
                Assert.assertTrue(result.getAlternateFundAudits().stream()
                        .anyMatch(a -> ("ALT" + idx).equals(a.getApi1PlanId()) && ("ORIG" + idx).equals(a.getApi2PlanId())),
                    "ALT" + idx + " must pair with ORIG" + idx + " by original position");
            }
            log.info("Equal total count (10=10) with 3 replacements: {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Unequal total count — serial fallback must never engage
    // =========================================================================

    @Test(groups = "compare", description = "Unequal total fund count (7 vs 6) — serial fallback must "
                       + "never engage even though category_name doesn't match either; genuine "
                       + "MISSING_FIELD/EXTRA_FIELD instead")
    public void unequalTotalCount_noFallback() {
        List<FundEntry> funds1 = new ArrayList<>(exactFunds(5));
        funds1.add(fund("ALT1", "Alt Fund Direct-G", "AA", "Alpha Category", bd(9000)));
        funds1.add(fund("ALT2", "Alt2 Fund Direct-G", "AB", "Gamma Category", bd(9500)));
        List<FundEntry> funds2 = new ArrayList<>(exactFunds(5));
        funds2.add(fund("ORIG1", "Orig Fund Direct-G", "BB", "Beta Category", bd(9000)));

        FundDetailsClient.primeForTest(Map.of(
            "ALT1", FundDetailsSnapshot.found("ALT1", "Alpha Category"),
            "ALT2", FundDetailsSnapshot.found("ALT2", "Gamma Category"),
            "ORIG1", FundDetailsSnapshot.found("ORIG1", "Beta Category")));
        try {
            Assert.assertNotEquals(funds1.size(), funds2.size(), "totals must genuinely differ (7 vs 6)");
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            Assert.assertFalse(result.getByType(MismatchType.COUNT_MISMATCH).isEmpty());
            Assert.assertTrue(result.getAlternateFundAudits().isEmpty(),
                "unequal totals must disqualify serial fallback entirely");
            List<String> missingPlanIds = result.getByType(MismatchType.MISSING_FIELD).stream()
                .map(Mismatch::getPlanId).toList();
            List<String> extraPlanIds = result.getByType(MismatchType.EXTRA_FIELD).stream()
                .map(Mismatch::getPlanId).toList();
            Assert.assertTrue(missingPlanIds.containsAll(List.of("ALT1", "ALT2")));
            Assert.assertTrue(extraPlanIds.contains("ORIG1"));
            log.info("Unequal total count (7 vs 6), no fallback: {}", result);
        } finally {
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Category pairing takes priority over serial pairing
    // =========================================================================

    @Test(groups = "compare", description = "When a valid category_name pairing exists, it must be used "
                       + "instead of serial position — even when serial position would have paired "
                       + "different funds")
    public void categoryPairingTakesPriorityOverSerialPosition() {
        // By original position: API-1[0]=SAME_CAT_1, API-2[0]=DIFF_CAT — would serial-pair wrongly.
        // But SAME_CAT_1 and SAME_CAT_2 (API-2, 2nd position) share a category_name — must category-pair instead.
        List<FundEntry> funds1 = List.of(
            fund("SAME_CAT_1", "Same Cat Fund Direct-G", "SC", "Shared Category", bd(5000)),
            fund("OTHER1", "Other Fund Direct-G", "OT", "Other Category", bd(6000)));
        List<FundEntry> funds2 = List.of(
            fund("DIFF_CAT", "Diff Cat Fund Direct-G", "DC", "Different Category", bd(9999)),
            fund("SAME_CAT_2", "Same Cat Fund 2 Direct-G", "SC", "Shared Category", bd(5000)));

        FundDetailsClient.primeForTest(Map.of(
            "SAME_CAT_1", FundDetailsSnapshot.found("SAME_CAT_1", "Shared Category"),
            "OTHER1", FundDetailsSnapshot.found("OTHER1", "Other Category"),
            "DIFF_CAT", FundDetailsSnapshot.found("DIFF_CAT", "Different Category"),
            "SAME_CAT_2", FundDetailsSnapshot.found("SAME_CAT_2", "Shared Category")));
        FundOpinionClient.primeForTest(Map.of(
            "SAME_CAT_1", FundOpinionSnapshot.found("SAME_CAT_1", 1, false, 4, null),
            "OTHER1", FundOpinionSnapshot.found("OTHER1", 1, false, 4, null)));
        try {
            ComparisonResult result = ResponseComparator.compare(TC, response(funds1), response(funds2));

            // SAME_CAT_1/SAME_CAT_2 must be paired by category_name — NOT by position (position 0
            // of each side is SAME_CAT_1 and DIFF_CAT, which must never be paired together).
            AlternateFundAudit categoryAudit = result.getAlternateFundAudits().stream()
                .filter(a -> "SAME_CAT_1".equals(a.getApi1PlanId())).findFirst().orElse(null);
            Assert.assertNotNull(categoryAudit, "SAME_CAT_1 must be paired via category_name, not by position");
            Assert.assertEquals(categoryAudit.getApi2PlanId(), "SAME_CAT_2");
            Assert.assertEquals(categoryAudit.getMatchType(), MatchType.ALTERNATE_MATCH);

            // Whatever category-matching leaves over (OTHER1/DIFF_CAT here) is then, separately,
            // eligible for the serial fallback — that's expected and not what this test is about.
            Assert.assertTrue(result.getAlternateFundAudits().stream()
                    .noneMatch(a -> "SAME_CAT_1".equals(a.getApi1PlanId()) && "DIFF_CAT".equals(a.getApi2PlanId())),
                "SAME_CAT_1 must never be paired with DIFF_CAT by raw array position");
            log.info("Category pairing takes priority over serial position: {}", result);
        } finally {
            FundOpinionClient.resetForTest();
            FundDetailsClient.resetForTest();
        }
    }

    // =========================================================================
    // Test data builders
    // =========================================================================

    private List<FundEntry> exactFunds(int n) {
        List<FundEntry> funds = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            funds.add(fund("P" + i, "Fund " + i + " Direct-G", "C" + i, "Category " + i, bd(1000L * i)));
        }
        return funds;
    }

    private ExtractedResponse response(List<FundEntry> funds) {
        return ExtractedResponse.success("API", "success", null, "investor-test", funds, List.<BreakdownEntry>of(), List.of());
    }

    private FundEntry fund(String planId, String planName, String catId, String catFmt, BigDecimal amount) {
        return FundEntry.builder().planId(planId).planName(planName)
            .categoryId(catId).categoryFmt(catFmt).amount(amount).legs(List.of()).build();
    }

    private BigDecimal bd(long v) { return BigDecimal.valueOf(v); }
}
