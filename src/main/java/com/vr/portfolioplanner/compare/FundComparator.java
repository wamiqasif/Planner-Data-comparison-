package com.vr.portfolioplanner.compare;

import com.vr.portfolioplanner.normalize.FundNameNormalizer;
import com.vr.portfolioplanner.normalize.TransactionTypeNormalizer;
import com.vr.portfolioplanner.response.model.FundEntry;
import com.vr.portfolioplanner.response.model.TransactionLeg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.vr.portfolioplanner.compare.ValueComparator.addIfPresent;
import static com.vr.portfolioplanner.compare.ValueComparator.str;

/**
 * Compares two {@link FundEntry} objects that have already been matched by
 * {@code plan_id}.  Collects ALL field-level mismatches without stopping.
 *
 * <p>Comparison order (per specification):
 * <ol>
 *   <li>Category ID</li>
 *   <li>Category name</li>
 *   <li>Fund name (with {@link FundNameNormalizer})</li>
 *   <li>Investment amount (BigDecimal tolerance)</li>
 *   <li>Transaction types present</li>
 *   <li>Transaction amounts per type</li>
 * </ol>
 */
public final class FundComparator {

    private static final Logger log = LoggerFactory.getLogger(FundComparator.class);

    private FundComparator() {}

    /**
     * Compares two fund entries matched by the same {@code plan_id}.
     *
     * @param testCaseId      identifier for mismatch reporting
     * @param f1              fund entry from API-1
     * @param f2              fund entry from API-2
     * @param amountTolerance absolute tolerance for monetary comparison
     * @return all mismatches found (may be empty; never null)
     */
    public static List<Mismatch> compare(
            String testCaseId, FundEntry f1, FundEntry f2, BigDecimal amountTolerance) {

        List<Mismatch> raw = new ArrayList<>();
        String planId  = f1.getPlanId();
        String f1Name  = f1.getPlanName();
        String f2Name  = f2.getPlanName();

        // 1. Category ID
        addIfPresent(raw, ValueComparator.compareStrings(
            testCaseId, fp(planId, "category_id"), f1.getCategoryId(), f2.getCategoryId()));

        // 2. Category name
        addIfPresent(raw, ValueComparator.compareStrings(
            testCaseId, fp(planId, "category_name"), f1.getCategoryFmt(), f2.getCategoryFmt()));

        // 3. Fund name (normalization-aware)
        addIfPresent(raw, compareFundName(testCaseId, planId, f1Name, f2Name));

        // 4. Investment amount
        addIfPresent(raw, ValueComparator.compareAmounts(
            testCaseId, fp(planId, "investment_amount"),
            f1.getAmount(), f2.getAmount(), amountTolerance));

        // 5 & 6. Transaction types and amounts
        raw.addAll(compareLegs(testCaseId, planId, f1.getLegs(), f2.getLegs(), amountTolerance));

        // Enrich every mismatch with fund context (planId + both fund names)
        List<Mismatch> mismatches = raw.stream()
            .map(m -> m.withFundContext(planId, f1Name, f2Name))
            .collect(java.util.stream.Collectors.toList());

        log.debug("Fund compare plan_id={} → {} mismatch(es)", planId, mismatches.size());
        return mismatches;
    }

    /**
     * Compares only the investment amount and transaction legs between two fund
     * entries paired as an accepted "good fund" replacement (see
     * {@link ResponseComparator}, fund-matching step 4c) rather than matched by
     * {@code plan_id}.
     *
     * <p>Category and fund name are intentionally NOT compared here: they are
     * expected to differ by design when one fund has been substituted for another
     * in the same category, so comparing them would flag the accepted substitution
     * as a false-positive mismatch.
     *
     * @param testCaseId      identifier for mismatch reporting
     * @param f1              fund entry from API-1 (the replaced fund)
     * @param f2              fund entry from API-2 (the accepted replacement)
     * @param amountTolerance absolute tolerance for monetary comparison
     * @return all mismatches found (may be empty; never null)
     */
    public static List<Mismatch> compareAmountAndLegs(
            String testCaseId, FundEntry f1, FundEntry f2, BigDecimal amountTolerance) {

        List<Mismatch> raw = new ArrayList<>();
        String planId = f1.getPlanId();

        addIfPresent(raw, ValueComparator.compareAmounts(
            testCaseId, fp(planId, "investment_amount"),
            f1.getAmount(), f2.getAmount(), amountTolerance));

        raw.addAll(compareLegs(testCaseId, planId, f1.getLegs(), f2.getLegs(), amountTolerance));

        List<Mismatch> mismatches = raw.stream()
            .map(m -> m.withFundContext(planId, f1.getPlanName(), f2.getPlanName()))
            .collect(java.util.stream.Collectors.toList());

        log.debug("Fund replacement compare plan_id={}→{} → {} mismatch(es)",
            planId, f2.getPlanId(), mismatches.size());
        return mismatches;
    }

    // -------------------------------------------------------------------------
    // Fund name normalization-aware comparison
    // -------------------------------------------------------------------------

    /**
     * Compares fund names with normalization.
     * <ul>
     *   <li>Exact match → empty</li>
     *   <li>Differ but normalize to same → {@link MismatchType#NORMALIZATION_DIFFERENCE}
     *       (informational, does not fail)</li>
     *   <li>Differ after normalization → {@link MismatchType#VALUE_MISMATCH}</li>
     * </ul>
     */
    static Optional<Mismatch> compareFundName(
            String testCaseId, String planId, String name1, String name2) {

        if (Objects.equals(name1, name2)) return Optional.empty();

        String norm1 = FundNameNormalizer.normalize(name1);
        String norm2 = FundNameNormalizer.normalize(name2);

        if (Objects.equals(norm1, norm2)) {
            return Optional.of(Mismatch.builder(testCaseId, MismatchType.NORMALIZATION_DIFFERENCE)
                .fieldPath(fp(planId, "fund_name"))
                .api1Value(name1)
                .api2Value(name2)
                .message("Fund names differ in raw form but are equal after normalization "
                         + "('" + norm1 + "')")
                .build());
        }

        return Optional.of(Mismatch.builder(testCaseId, MismatchType.VALUE_MISMATCH)
            .fieldPath(fp(planId, "fund_name"))
            .api1Value(name1 + " [normalized: " + norm1 + "]")
            .api2Value(name2 + " [normalized: " + norm2 + "]")
            .message("Fund names differ even after normalization")
            .build());
    }

    // -------------------------------------------------------------------------
    // Transaction leg comparison
    // -------------------------------------------------------------------------

    /**
     * Compares transaction legs from both APIs by normalized type.
     * Legs are NOT compared by array order — they are grouped by {@code normalizedType}.
     * When multiple legs share the same type, their amounts are summed before comparison.
     *
     * <p>If either side provides no legs (e.g. API-2 does not expose per-fund leg data
     * at the {@code funds_data} level), the comparison is skipped entirely to avoid
     * false MISSING_FIELD positives.  The structural difference is documented in
     * {@link com.vr.portfolioplanner.normalize.FieldMapping#TRANSACTION_TYPE}.
     */
    private static List<Mismatch> compareLegs(
            String testCaseId, String planId,
            List<TransactionLeg> legs1, List<TransactionLeg> legs2,
            BigDecimal amountTolerance) {

        Map<String, BigDecimal> map1 = legAmountsByType(legs1);
        Map<String, BigDecimal> map2 = legAmountsByType(legs2);

        // Skip when either side has no leg data — prevents false positives when one
        // API does not expose per-fund leg breakdown (e.g. API-2 real response).
        if (map1.isEmpty() || map2.isEmpty()) {
            log.debug("Leg comparison skipped for plan_id={}: map1.size={} map2.size={}",
                planId, map1.size(), map2.size());
            return java.util.Collections.emptyList();
        }

        List<Mismatch> mismatches = new ArrayList<>();

        // 5. Transaction types in API-1 missing from API-2
        for (String type : map1.keySet()) {
            if (!map2.containsKey(type)) {
                mismatches.add(ValueComparator.missingInApi2(
                    testCaseId,
                    legFp(planId, type, "type"),
                    type,
                    "Transaction type '" + type + "' present in API-1 but absent in API-2"
                ));
            }
        }

        // Extra types in API-2 not in API-1
        for (String type : map2.keySet()) {
            if (!map1.containsKey(type)) {
                mismatches.add(ValueComparator.extraInApi2(
                    testCaseId,
                    legFp(planId, type, "type"),
                    type,
                    "Transaction type '" + type + "' present in API-2 but absent in API-1"
                ));
            }
        }

        // 6. Transaction amounts for matching types
        for (Map.Entry<String, BigDecimal> e : map1.entrySet()) {
            String type = e.getKey();
            if (map2.containsKey(type)) {
                addIfPresent(mismatches, ValueComparator.compareAmounts(
                    testCaseId,
                    legFp(planId, type, "amount"),
                    e.getValue(), map2.get(type), amountTolerance
                ));
            }
        }

        return mismatches;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Groups leg amounts by normalizedType; sums when multiple legs share a type. */
    private static Map<String, BigDecimal> legAmountsByType(List<TransactionLeg> legs) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (TransactionLeg leg : legs) {
            String type = leg.getNormalizedType();
            if (type == null) type = TransactionTypeNormalizer.normalize(leg.getRawType());
            if (type == null) continue;
            BigDecimal amt = leg.getAmount() != null ? leg.getAmount() : BigDecimal.ZERO;
            result.merge(type, amt, BigDecimal::add);
        }
        return result;
    }

    /** Builds a fund-level field path. */
    private static String fp(String planId, String field) {
        return "funds_data.data[plan_id=" + planId + "]." + field;
    }

    /** Builds a leg-level field path. */
    private static String legFp(String planId, String type, String field) {
        return "funds_data.data[plan_id=" + planId + "].legs[type=" + type + "]." + field;
    }
}
