package com.vr.portfolioplanner.altfund;

import com.vr.portfolioplanner.compare.FundComparator;
import com.vr.portfolioplanner.compare.Mismatch;
import com.vr.portfolioplanner.compare.MismatchType;
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
import java.util.stream.Collectors;

/**
 * Pairs funds left unmatched by {@code plan_id} across API-1 and API-2, and
 * validates each pairing against 5 business rules. API-2 is always the
 * original/reference fund; API-1 is the alternate candidate. Rules are
 * evaluated <b>only</b> against the API-1 side.
 *
 * <h3>Pairing (deterministic, category_name-based)</h3>
 * Orphans on both sides are grouped by {@code category_name} (fetched live via
 * {@link FundDetailsClient}). A category shared by exactly one orphan on each
 * side is a unique pair. A category shared by more than one orphan on either
 * side cannot be uniquely resolved — every fund in that group is reported
 * {@link MatchType#PAIRING_AMBIGUOUS}, never guessed. A fetch failure for an
 * orphan's own details makes it {@link MatchType#REQUIRED_DATA_NOT_AVAILABLE}
 * directly. Orphans whose category matches nothing on the other side fall
 * through to the caller's existing MISSING_FIELD/EXTRA_FIELD handling — a real
 * absence, not a substitution.
 *
 * <h3>The 5 rules</h3>
 * See {@link FundClassificationRules} for the (currently empty, extensible)
 * reference data Rules 2 and 4 depend on. Rule 3 (Index Fund) and Rule 5
 * (Watchlist) resolve from data already on hand for every pairing — an empty
 * category_name/tag_name business meaning, not a missing configuration — so
 * neither ever reports {@code REQUIRED_DATA_NOT_AVAILABLE} for that reason.
 */
public final class AlternateFundValidator {

    private static final Logger log = LoggerFactory.getLogger(AlternateFundValidator.class);

    private static final String RULE1_NAME = "Rule 1 - Opinion";
    private static final String RULE2_NAME = "Rule 2 - Prohibited Classification";
    private static final String RULE3_NAME = "Rule 3 - Index Fund";
    private static final String RULE4_NAME = "Rule 4 - Good+NotAnalystPick+1/2/3-Star";
    private static final String RULE5_NAME = "Rule 5 - Analyst's Choice+Watchlisted";

    private AlternateFundValidator() {}

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Pairs and validates every fund left unmatched by {@code plan_id} after
     * direct matching. {@code unmatched1}/{@code unmatched2} are consumed
     * (paired, ambiguous, or data-gapped) plan_ids are reported back via
     * {@link PairingOutcome#getConsumedApi1()}/{@link PairingOutcome#getConsumedApi2()}
     * so the caller can remove them before falling back to its own
     * MISSING_FIELD/EXTRA_FIELD handling for whatever remains.
     */
    public static PairingOutcome pairAndValidate(
            String testCaseId,
            Map<String, FundEntry> unmatched1,
            Map<String, FundEntry> unmatched2,
            BigDecimal amountTolerance) {

        PairingOutcome outcome = new PairingOutcome();
        if (unmatched1.isEmpty() || unmatched2.isEmpty()) return outcome;

        // 1. Fund Details for every orphan on both sides; a fetch failure is a direct data gap.
        Map<String, FundDetailsSnapshot> details1 = new LinkedHashMap<>();
        Map<String, FundDetailsSnapshot> details2 = new LinkedHashMap<>();
        fetchDetailsOrGap(testCaseId, unmatched1, details1, outcome, true);
        fetchDetailsOrGap(testCaseId, unmatched2, details2, outcome, false);

        // 2. Group successfully-fetched orphans by category_name.
        Map<String, List<String>> byCategory1 = groupByCategory(details1);
        Map<String, List<String>> byCategory2 = groupByCategory(details2);

        Set<String> sharedCategories = new LinkedHashSet<>(byCategory1.keySet());
        sharedCategories.retainAll(byCategory2.keySet());

        List<String[]> uniquePairs = new ArrayList<>(); // [api2PlanId, api1PlanId]

        for (String category : sharedCategories) {
            List<String> list1 = byCategory1.get(category);
            List<String> list2 = byCategory2.get(category);

            if (list1.size() == 1 && list2.size() == 1) {
                uniquePairs.add(new String[] { list2.get(0), list1.get(0) });
                outcome.consumedApi1.add(list1.get(0));
                outcome.consumedApi2.add(list2.get(0));
            } else {
                String reason = String.format(
                    "%d candidate(s) on API-1 side / %d candidate(s) on API-2 side share category_name '%s'",
                    list1.size(), list2.size(), category);
                for (String planId : list1) {
                    emitAmbiguous(testCaseId, outcome, unmatched1.get(planId), category, reason, true);
                    outcome.consumedApi1.add(planId);
                }
                for (String planId : list2) {
                    emitAmbiguous(testCaseId, outcome, unmatched2.get(planId), category, reason, false);
                    outcome.consumedApi2.add(planId);
                }
            }
        }

        // If a Fund Details fetch failed for any orphan on either side, no remaining unconsumed
        // orphan on the OTHER side can be confidently declared genuinely missing/extra either — its
        // true category can't be ruled out as a match for the one that failed. Gap them too rather
        // than guess.
        boolean anyDetailsFetchFailure = details1.size() < unmatched1.size() || details2.size() < unmatched2.size();
        if (anyDetailsFetchFailure) {
            for (Map.Entry<String, FundDetailsSnapshot> e : details1.entrySet()) {
                if (outcome.consumedApi1.contains(e.getKey())) continue;
                emitUnresolvedDueToSiblingFailure(testCaseId, outcome, unmatched1.get(e.getKey()), true);
                outcome.consumedApi1.add(e.getKey());
            }
            for (Map.Entry<String, FundDetailsSnapshot> e : details2.entrySet()) {
                if (outcome.consumedApi2.contains(e.getKey())) continue;
                emitUnresolvedDueToSiblingFailure(testCaseId, outcome, unmatched2.get(e.getKey()), false);
                outcome.consumedApi2.add(e.getKey());
            }
        }

        if (uniquePairs.isEmpty()) return outcome;

        // 3. One batched Fund Opinion fetch for all API-1 alternates in the confirmed pairs.
        List<String> api1PlanIds = uniquePairs.stream().map(p -> p[1]).collect(Collectors.toList());
        Map<String, FundOpinionSnapshot> opinions;
        String opinionFetchError = null;
        try {
            opinions = FundOpinionClient.fetch(api1PlanIds);
        } catch (FundDataUnavailableException e) {
            opinions = Map.of();
            opinionFetchError = e.getMessage();
            log.warn("fund-opinion-data fetch failed for {} alternate candidate(s): {}",
                api1PlanIds.size(), e.getMessage());
        }

        for (String[] pair : uniquePairs) {
            String api2PlanId = pair[0];
            String api1PlanId = pair[1];
            FundEntry api2Fund = unmatched2.get(api2PlanId);
            FundEntry api1Fund = unmatched1.get(api1PlanId);
            FundOpinionSnapshot opinion = opinions.getOrDefault(api1PlanId, FundOpinionSnapshot.notFound(api1PlanId));
            FundDetailsSnapshot details = details1.get(api1PlanId);
            FundDetailsSnapshot api2Details = details2.get(api2PlanId);

            AlternateFundAudit audit = evaluateAlternate(
                testCaseId, api2Fund, api1Fund, opinion, opinionFetchError, details, api2Details);
            outcome.audits.add(audit);
            emitMismatchForAudit(testCaseId, outcome, audit, api2Fund, api1Fund, amountTolerance);
        }

        return outcome;
    }

    // -------------------------------------------------------------------------
    // Rule evaluation
    // -------------------------------------------------------------------------

    /** Evaluates all 5 rules against the API-1 alternate and combines them into one audit row. */
    static AlternateFundAudit evaluateAlternate(
            String testCaseId, FundEntry api2Original, FundEntry api1Alternate,
            FundOpinionSnapshot opinion, String opinionFetchError, FundDetailsSnapshot details,
            FundDetailsSnapshot api2Details) {

        RuleResult r1 = evaluateRule1(opinion, opinionFetchError);
        RuleResult r2 = evaluateRule2(details);
        RuleResult r3 = evaluateRule3(api2Details.getCategoryName(), details.getCategoryName());
        RuleResult r4 = evaluateRule4(opinion, opinionFetchError, details);
        RuleResult r5 = evaluateRule5(opinion, opinionFetchError);

        List<RuleResult> rules = List.of(r1, r2, r3, r4, r5);
        List<String> names = List.of(RULE1_NAME, RULE2_NAME, RULE3_NAME, RULE4_NAME, RULE5_NAME);

        boolean anyFail = rules.stream().anyMatch(r -> r.getOutcome() == RuleOutcome.FAIL);
        boolean anyGap  = rules.stream().anyMatch(r -> r.getOutcome() == RuleOutcome.REQUIRED_DATA_NOT_AVAILABLE);

        MatchType matchType;
        String finalResult;
        if (anyFail) {
            matchType = MatchType.ALTERNATE_REJECTED;
            finalResult = "FAIL";
        } else if (anyGap) {
            matchType = MatchType.REQUIRED_DATA_NOT_AVAILABLE;
            finalResult = "REQUIRED_DATA_NOT_AVAILABLE";
        } else {
            matchType = MatchType.ALTERNATE_MATCH;
            finalResult = "PASS";
        }

        StringBuilder failedCondition = new StringBuilder();
        for (int i = 0; i < rules.size(); i++) {
            RuleOutcome o = rules.get(i).getOutcome();
            if (o == RuleOutcome.FAIL || o == RuleOutcome.REQUIRED_DATA_NOT_AVAILABLE) {
                if (failedCondition.length() > 0) failedCondition.append("; ");
                failedCondition.append(names.get(i)).append(" [").append(o).append("] ")
                    .append(rules.get(i).getReason());
            }
        }

        return AlternateFundAudit.builder(testCaseId)
            .api2Plan(api2Original.getPlanId(), api2Original.getPlanName())
            .api1Plan(api1Alternate.getPlanId(), api1Alternate.getPlanName())
            .matchType(matchType)
            .api1CategoryName(details.getCategoryName())
            .api1Opinion(opinion.getOpinionTypeId(), opinion.getIsAnalystPick(), opinion.getVrRating(), opinion.getTagName())
            .rules(r1, r2, r3, r4, r5)
            .failedCondition(failedCondition.toString())
            .finalResult(finalResult)
            .build();
    }

    /** Rule 1 — Opinion: {@code opinion_type_id == 1} means "Good Fund". */
    private static RuleResult evaluateRule1(FundOpinionSnapshot opinion, String opinionFetchError) {
        if (opinionFetchError != null) {
            return RuleResult.dataUnavailable("fund-opinion-data unavailable: " + opinionFetchError);
        }
        if (!opinion.isFound()) {
            return RuleResult.fail("plan_id not present in fund-opinion-data (no positive opinion)");
        }
        Integer t = opinion.getOpinionTypeId();
        if (t == null) {
            return RuleResult.dataUnavailable("opinion_type_id missing in fund-opinion-data response");
        }
        return t == 1
            ? RuleResult.pass("opinion_type_id=1 (Good Fund)")
            : RuleResult.fail("opinion_type_id=" + t + " (!= 1)");
    }

    /**
     * Rule 2 — Prohibited classification (ETF / closed-end / solution-oriented).
     * {@link FundClassificationRules#PROHIBITED_CATEGORY_NAMES} being empty means
     * no classification is documented as prohibited yet — it is reference data
     * to fail specific categories against, not a precondition for evaluating the
     * rule at all, so an empty list simply never fails anything (PASS).
     */
    private static RuleResult evaluateRule2(FundDetailsSnapshot details) {
        String cat = details.getCategoryName();
        if (cat == null) {
            return RuleResult.dataUnavailable("category_name missing from Fund Details response");
        }
        return FundClassificationRules.PROHIBITED_CATEGORY_NAMES.contains(cat)
            ? RuleResult.fail("category_name '" + cat + "' is a prohibited classification")
            : RuleResult.pass("category_name '" + cat + "' not prohibited");
    }

    /**
     * Rule 3 — Index Fund exception: an Index Fund can only be replaced by
     * another Index Fund. Evaluated from the API-2 original fund's own
     * category_name (already on hand from pairing) — there is no model-slot
     * requirement involved.
     */
    private static RuleResult evaluateRule3(String api2CategoryName, String api1CategoryName) {
        if (!isIndexFundCategory(api2CategoryName)) {
            return RuleResult.notApplicable(
                "API-2 original category_name '" + api2CategoryName + "' is not an Index Fund");
        }
        return isIndexFundCategory(api1CategoryName)
            ? RuleResult.pass("API-2 original is an Index Fund and API-1 alternate category_name '"
                + api1CategoryName + "' is also an Index Fund")
            : RuleResult.fail("API-2 original is an Index Fund but API-1 alternate category_name '"
                + api1CategoryName + "' is not an Index Fund");
    }

    /** Category-name-based Index Fund detection — no separate reference list needed or maintained. */
    private static boolean isIndexFundCategory(String categoryName) {
        return categoryName != null && categoryName.toLowerCase().contains("index");
    }

    /** Rule 4 — Good + not Analyst's Choice + 1/2/3-star. No "not-rated" exception applied yet. */
    private static RuleResult evaluateRule4(
            FundOpinionSnapshot opinion, String opinionFetchError, FundDetailsSnapshot details) {
        if (opinionFetchError != null) {
            return RuleResult.dataUnavailable("fund-opinion-data unavailable: " + opinionFetchError);
        }
        if (!opinion.isFound() || !Integer.valueOf(1).equals(opinion.getOpinionTypeId())) {
            return RuleResult.notApplicable("fund is not opinion_type_id=1 (Good Fund)");
        }
        Boolean pick = opinion.getIsAnalystPick();
        if (pick == null) {
            return RuleResult.dataUnavailable("is_analyst_pick missing in fund-opinion-data response");
        }
        if (Boolean.TRUE.equals(pick)) {
            return RuleResult.pass("is_analyst_pick=true (Analyst's Choice)");
        }
        String category = details.getCategoryName();
        if (category != null && FundClassificationRules.NOT_RATED_CATEGORY_NAMES.contains(category)) {
            return RuleResult.notApplicable("category_name '" + category + "' is exempt (not rated)");
        }
        Integer rating = opinion.getVrRating();
        if (rating == null) {
            return RuleResult.dataUnavailable("vr_rating missing in fund-opinion-data response");
        }
        return (rating >= 1 && rating <= 3)
            ? RuleResult.fail("Good fund, not Analyst's Choice, vr_rating=" + rating + " (1-3 star)")
            : RuleResult.pass("vr_rating=" + rating + " (not 1-3 star)");
    }

    /**
     * Rule 5 — Analyst's Choice + Watchlisted.
     *
     * <p>{@code tag_name == null} is a valid, meaningful business value: it means the fund is
     * NOT present in the Watchlist. It is therefore never {@link RuleOutcome#REQUIRED_DATA_NOT_AVAILABLE}
     * — only a non-null {@code tag_name} whose meaning can't be resolved (because
     * {@link FundClassificationRules#WATCHLIST_TAG_VALUE} isn't configured yet) falls back to that.
     */
    private static RuleResult evaluateRule5(FundOpinionSnapshot opinion, String opinionFetchError) {
        if (opinionFetchError != null) {
            return RuleResult.dataUnavailable("fund-opinion-data unavailable: " + opinionFetchError);
        }
        Boolean pick = opinion.getIsAnalystPick();
        if (!Boolean.TRUE.equals(pick)) {
            return RuleResult.notApplicable("not Analyst's Choice (is_analyst_pick != true)");
        }
        String tag = opinion.getTagName();
        if (tag == null) {
            return RuleResult.pass("Analyst's Choice fund is not watchlisted");
        }
        if (FundClassificationRules.WATCHLIST_TAG_VALUE == null) {
            return RuleResult.dataUnavailable(
                "tag_name='" + tag + "' present but exact WATCHLIST value not configured");
        }
        return FundClassificationRules.WATCHLIST_TAG_VALUE.equalsIgnoreCase(tag)
            ? RuleResult.fail("Analyst's Choice fund is watchlisted")
            : RuleResult.pass("tag_name='" + tag + "' does not indicate watchlist");
    }

    // -------------------------------------------------------------------------
    // Pairing helpers
    // -------------------------------------------------------------------------

    private static void fetchDetailsOrGap(
            String testCaseId, Map<String, FundEntry> orphans,
            Map<String, FundDetailsSnapshot> successOut, PairingOutcome outcome, boolean isApi1Side) {

        for (Map.Entry<String, FundEntry> e : orphans.entrySet()) {
            String planId = e.getKey();
            try {
                successOut.put(planId, FundDetailsClient.fetch(planId));
            } catch (FundDataUnavailableException ex) {
                FundEntry fund = e.getValue();
                String reason = "Fund Details unavailable for plan_id=" + planId + ": " + ex.getMessage();

                AlternateFundAudit.Builder b = AlternateFundAudit.builder(testCaseId)
                    .matchType(MatchType.REQUIRED_DATA_NOT_AVAILABLE)
                    .failedCondition(reason)
                    .finalResult("REQUIRED_DATA_NOT_AVAILABLE");
                if (isApi1Side) b.api1Plan(planId, fund.getPlanName());
                else            b.api2Plan(planId, fund.getPlanName());
                outcome.audits.add(b.build());

                Mismatch.Builder mb = Mismatch.builder(testCaseId, MismatchType.ALTERNATE_FUND_DATA_UNAVAILABLE)
                    .fieldPath("funds_data.data[plan_id=" + planId + "]")
                    .planId(planId)
                    .message(reason + " — cannot determine category_name for pairing");
                if (isApi1Side) {
                    mb.api1Value(planId).api2Value("(unknown)")
                      .api1FundName(fund.getPlanName()).api2FundName("(unknown)");
                } else {
                    mb.api1Value("(unknown)").api2Value(planId)
                      .api1FundName("(unknown)").api2FundName(fund.getPlanName());
                }
                outcome.mismatches.add(mb.build());

                if (isApi1Side) outcome.consumedApi1.add(planId); else outcome.consumedApi2.add(planId);
            }
        }
    }

    private static void emitAmbiguous(
            String testCaseId, PairingOutcome outcome, FundEntry fund,
            String category, String reason, boolean isApi1Side) {

        AlternateFundAudit.Builder b = AlternateFundAudit.builder(testCaseId)
            .matchType(MatchType.PAIRING_AMBIGUOUS)
            .api1CategoryName(category)
            .failedCondition(reason)
            .finalResult("PAIRING_AMBIGUOUS");
        if (isApi1Side) b.api1Plan(fund.getPlanId(), fund.getPlanName());
        else            b.api2Plan(fund.getPlanId(), fund.getPlanName());
        outcome.audits.add(b.build());

        Mismatch.Builder mb = Mismatch.builder(testCaseId, MismatchType.ALTERNATE_FUND_PAIRING_AMBIGUOUS)
            .fieldPath("funds_data.data[plan_id=" + fund.getPlanId() + "]")
            .planId(fund.getPlanId())
            .message("Cannot uniquely pair plan_id='" + fund.getPlanId() + "' (category_name='"
                + category + "'): " + reason);
        if (isApi1Side) {
            mb.api1Value(fund.getPlanId()).api2Value("(ambiguous)")
              .api1FundName(fund.getPlanName()).api2FundName("(ambiguous)");
        } else {
            mb.api1Value("(ambiguous)").api2Value(fund.getPlanId())
              .api1FundName("(ambiguous)").api2FundName(fund.getPlanName());
        }
        outcome.mismatches.add(mb.build());
    }

    private static void emitUnresolvedDueToSiblingFailure(
            String testCaseId, PairingOutcome outcome, FundEntry fund, boolean isApi1Side) {

        String reason = "Fund Details unavailable for one or more candidate(s) on the other side; "
            + "plan_id='" + fund.getPlanId() + "' cannot be confirmed as genuinely unmatched";

        AlternateFundAudit.Builder b = AlternateFundAudit.builder(testCaseId)
            .matchType(MatchType.REQUIRED_DATA_NOT_AVAILABLE)
            .failedCondition(reason)
            .finalResult("REQUIRED_DATA_NOT_AVAILABLE");
        if (isApi1Side) b.api1Plan(fund.getPlanId(), fund.getPlanName());
        else            b.api2Plan(fund.getPlanId(), fund.getPlanName());
        outcome.audits.add(b.build());

        Mismatch.Builder mb = Mismatch.builder(testCaseId, MismatchType.ALTERNATE_FUND_DATA_UNAVAILABLE)
            .fieldPath("funds_data.data[plan_id=" + fund.getPlanId() + "]")
            .planId(fund.getPlanId())
            .message(reason);
        if (isApi1Side) {
            mb.api1Value(fund.getPlanId()).api2Value("(unknown)")
              .api1FundName(fund.getPlanName()).api2FundName("(unknown)");
        } else {
            mb.api1Value("(unknown)").api2Value(fund.getPlanId())
              .api1FundName("(unknown)").api2FundName(fund.getPlanName());
        }
        outcome.mismatches.add(mb.build());
    }

    private static void emitMismatchForAudit(
            String testCaseId, PairingOutcome outcome, AlternateFundAudit audit,
            FundEntry api2Fund, FundEntry api1Fund, BigDecimal amountTolerance) {

        String api1PlanId = api1Fund.getPlanId();
        String api2PlanId = api2Fund.getPlanId();

        switch (audit.getMatchType()) {
            case ALTERNATE_MATCH -> {
                outcome.mismatches.add(Mismatch.builder(testCaseId, MismatchType.ALTERNATE_FUND_MATCH)
                    .fieldPath("funds_data.data[plan_id=" + api1PlanId + "]")
                    .api1Value(api1PlanId).api2Value(api2PlanId)
                    .planId(api1PlanId).api1FundName(api1Fund.getPlanName()).api2FundName(api2Fund.getPlanName())
                    .message("API-1 alternate plan_id='" + api1PlanId + "' accepted as a valid substitute for "
                        + "API-2 original plan_id='" + api2PlanId + "' (category_name='"
                        + audit.getApi1CategoryName() + "'); all applicable business rules passed")
                    .build());
                outcome.mismatches.addAll(
                    FundComparator.compareAmountAndLegs(testCaseId, api1Fund, api2Fund, amountTolerance));
            }
            case ALTERNATE_REJECTED -> outcome.mismatches.add(
                Mismatch.builder(testCaseId, MismatchType.ALTERNATE_FUND_REJECTED)
                    .fieldPath("funds_data.data[plan_id=" + api1PlanId + "]")
                    .api1Value(api1PlanId).api2Value(api2PlanId)
                    .planId(api1PlanId).api1FundName(api1Fund.getPlanName()).api2FundName(api2Fund.getPlanName())
                    .message("API-1 alternate plan_id='" + api1PlanId + "' rejected as a substitute for API-2 "
                        + "original plan_id='" + api2PlanId + "': " + audit.getFailedCondition())
                    .build());
            case REQUIRED_DATA_NOT_AVAILABLE -> outcome.mismatches.add(
                Mismatch.builder(testCaseId, MismatchType.ALTERNATE_FUND_DATA_UNAVAILABLE)
                    .fieldPath("funds_data.data[plan_id=" + api1PlanId + "]")
                    .api1Value(api1PlanId).api2Value(api2PlanId)
                    .planId(api1PlanId).api1FundName(api1Fund.getPlanName()).api2FundName(api2Fund.getPlanName())
                    .message("Cannot determine whether API-1 alternate plan_id='" + api1PlanId + "' is a valid "
                        + "substitute for API-2 original plan_id='" + api2PlanId + "': " + audit.getFailedCondition())
                    .build());
            default -> throw new IllegalStateException(
                "Unexpected matchType for a paired candidate: " + audit.getMatchType());
        }
    }

    private static Map<String, List<String>> groupByCategory(Map<String, FundDetailsSnapshot> details) {
        Map<String, List<String>> byCategory = new LinkedHashMap<>();
        for (Map.Entry<String, FundDetailsSnapshot> e : details.entrySet()) {
            String cat = e.getValue().getCategoryName();
            if (cat == null || cat.isBlank()) continue; // fetched fine, but no category — can't pair; falls to MISSING/EXTRA
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(e.getKey());
        }
        return byCategory;
    }

    // -------------------------------------------------------------------------
    // Result holder
    // -------------------------------------------------------------------------

    public static final class PairingOutcome {
        private final List<Mismatch> mismatches = new ArrayList<>();
        private final List<AlternateFundAudit> audits = new ArrayList<>();
        private final Set<String> consumedApi1 = new LinkedHashSet<>();
        private final Set<String> consumedApi2 = new LinkedHashSet<>();

        public List<Mismatch> getMismatches()           { return mismatches; }
        public List<AlternateFundAudit> getAudits()     { return audits; }
        public Set<String> getConsumedApi1()            { return consumedApi1; }
        public Set<String> getConsumedApi2()            { return consumedApi2; }
    }
}
