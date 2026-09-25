package com.vr.portfolioplanner.compare;

import com.vr.portfolioplanner.response.model.BreakdownEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.vr.portfolioplanner.compare.ValueComparator.addIfPresent;

/**
 * Compares two {@link BreakdownEntry} objects that have been matched by
 * {@code category_id}.  Collects ALL field-level mismatches without stopping.
 *
 * <p>Comparison order:
 * <ol>
 *   <li>Category name ({@code category_fmt})</li>
 *   <li>Breakdown percentage (resolved via {@link BreakdownEntry#resolvePercentage()},
 *       compared with the supplied tolerance)</li>
 * </ol>
 */
public final class BreakdownComparator {

    private static final Logger log = LoggerFactory.getLogger(BreakdownComparator.class);

    private BreakdownComparator() {}

    /**
     * Compares two breakdown entries matched by the same {@code category_id}.
     *
     * @param testCaseId           identifier for mismatch reporting
     * @param b1                   breakdown entry from API-1
     * @param b2                   breakdown entry from API-2
     * @param percentageTolerance  absolute tolerance for percentage comparison
     * @return all mismatches found (may be empty; never null)
     */
    public static List<Mismatch> compare(
            String testCaseId, BreakdownEntry b1, BreakdownEntry b2,
            BigDecimal percentageTolerance) {

        List<Mismatch> mismatches = new ArrayList<>();
        String catId = b1.getCategoryId();

        // 11. Category name
        addIfPresent(mismatches, ValueComparator.compareStrings(
            testCaseId,
            fp(catId, "category_name"),
            b1.getCategoryFmt(), b2.getCategoryFmt()
        ));

        // 12. Breakdown percentage
        BigDecimal pct1 = b1.resolvePercentage();
        BigDecimal pct2 = b2.resolvePercentage();
        addIfPresent(mismatches, ValueComparator.compareAmounts(
            testCaseId,
            fp(catId, "percentage"),
            pct1, pct2, percentageTolerance
        ));

        log.debug("Breakdown compare category_id={} → {} mismatch(es)", catId, mismatches.size());
        return mismatches;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String fp(String catId, String field) {
        return "breakdown_funds_data.data[category_id=" + catId + "]." + field;
    }
}
