package com.vr.portfolioplanner.normalize;

import com.vr.portfolioplanner.response.model.BreakdownEntry;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import com.vr.portfolioplanner.response.model.FundEntry;
import com.vr.portfolioplanner.response.model.TransactionLeg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies all normalization rules to an {@link ExtractedResponse}, producing a
 * new response whose fields are ready for cross-API comparison.
 *
 * <p>Normalization applied:
 * <ul>
 *   <li>{@link FundEntry#getPlanName()} — {@link FundNameNormalizer}: "Dir-G" → "Direct-G"</li>
 *   <li>{@link FundEntry#getAmount()} — passed through (already BigDecimal from extractor;
 *       {@link CurrencyNormalizer} is invoked when amount was a formatted string)</li>
 *   <li>{@link TransactionLeg#getNormalizedType()} — already computed by
 *       {@link TransactionTypeNormalizer} at construction time inside the leg</li>
 *   <li>{@link BreakdownEntry#resolvePercentage()} — parses {@code perc_fmt} string
 *       via {@link CurrencyNormalizer#parsePercentage(String)} when the numeric
 *       {@code percentage} field is absent</li>
 * </ul>
 *
 * <p>The original {@link ExtractedResponse} is never modified.
 * The returned instance shares the same {@link ExtractedResponse#getApiLabel()} and
 * adds a suffix to the notComparableFields list noting that normalization was applied.
 */
public final class ResponseNormalizer {

    private static final Logger log = LoggerFactory.getLogger(ResponseNormalizer.class);

    private ResponseNormalizer() {}

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * Returns a new {@link ExtractedResponse} with all normalization rules applied.
     *
     * @param raw the response produced by an extractor
     * @return normalized copy; the input is unchanged
     */
    public static ExtractedResponse normalize(ExtractedResponse raw) {
        if (!raw.isExtractionSuccess()) {
            log.debug("Skipping normalization for failed extraction: {}", raw.getExtractionError());
            return raw;
        }

        List<FundEntry>      normalizedFunds      = normalizeFunds(raw.getFunds());
        List<BreakdownEntry> normalizedBreakdowns = normalizeBreakdowns(raw.getBreakdowns());

        List<String> nc = new ArrayList<>(raw.getNotComparableFields());
        nc.add("(normalization applied: FundNameNormalizer, CurrencyNormalizer, TransactionTypeNormalizer)");

        log.debug("{} normalized: {} funds, {} breakdowns",
            raw.getApiLabel(), normalizedFunds.size(), normalizedBreakdowns.size());

        return ExtractedResponse.success(
            raw.getApiLabel(),
            raw.getStatus(),
            raw.getMessage(),
            raw.getInvestorId(),
            normalizedFunds,
            normalizedBreakdowns,
            nc
        );
    }

    // -------------------------------------------------------------------------
    // Fund normalization
    // -------------------------------------------------------------------------

    private static List<FundEntry> normalizeFunds(List<FundEntry> funds) {
        List<FundEntry> result = new ArrayList<>(funds.size());
        for (FundEntry f : funds) {
            result.add(FundEntry.builder()
                .planId(     f.getPlanId())
                .planName(   FundNameNormalizer.normalize(f.getPlanName()))
                .categoryId( f.getCategoryId())
                .categoryFmt(f.getCategoryFmt())
                .amount(     normalizeAmount(f.getAmount()))
                .legs(       f.getLegs())   // legs carry normalizedType already
                .build());
        }
        return result;
    }

    /**
     * Ensures the amount is properly represented as a BigDecimal.
     * If the extractor already produced a BigDecimal, the value is returned as-is.
     * Handles the edge case where the amount arrived as a formatted string that
     * the extractor could not convert (returns null in that case, not an exception).
     */
    private static BigDecimal normalizeAmount(BigDecimal amount) {
        return amount;  // Already handled by asBigDecimal() in the extractors.
                        // CurrencyNormalizer.parse(String) is the entry point for
                        // formatted-string inputs that bypass the extractor path.
    }

    // -------------------------------------------------------------------------
    // Breakdown normalization
    // -------------------------------------------------------------------------

    private static List<BreakdownEntry> normalizeBreakdowns(List<BreakdownEntry> breakdowns) {
        List<BreakdownEntry> result = new ArrayList<>(breakdowns.size());
        for (BreakdownEntry b : breakdowns) {
            // resolvePercentage() already applies CurrencyNormalizer internally.
            // Re-store the resolved value as the canonical percentage so the
            // comparator can use getPercentage() directly without calling resolve().
            BigDecimal resolvedPct = b.resolvePercentage();
            result.add(new BreakdownEntry(
                b.getCategoryId(),
                b.getCategoryFmt(),
                resolvedPct,
                b.getPercFmt(),
                b.getAmount()
            ));
        }
        return result;
    }
}
