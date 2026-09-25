package com.vr.portfolioplanner.normalize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Converts currency-formatted strings to {@link BigDecimal} for numeric comparison.
 *
 * <p>Handles the range of formats that appear in both API responses:
 * <pre>
 *   "₹50,000"     → 50000
 *   "50,000.00"   → 50000.00
 *   "1,00,000"    → 100000   (Indian numbering: 1 lakh)
 *   "40.00%"      → 40.00    (breakdown percentage)
 *   "50000"       → 50000    (already numeric string)
 *   20000  (int)  → 20000    (BigDecimal.valueOf — pass-through convenience)
 * </pre>
 *
 * <p>This normalizer applies only controlled pattern-based stripping — it does NOT
 * infer locale or apply fuzzy parsing.  Unknown formats that contain non-numeric
 * characters beyond the stripped set return {@code null} and log a warning.
 */
public final class CurrencyNormalizer {

    private static final Logger log = LoggerFactory.getLogger(CurrencyNormalizer.class);

    /** Characters that are formatting artefacts and should be stripped. */
    private static final Pattern STRIP = Pattern.compile("[₹$€£%,\\s]");

    private CurrencyNormalizer() {}

    // -------------------------------------------------------------------------
    // String input
    // -------------------------------------------------------------------------

    /**
     * Parses a currency-formatted string into a {@link BigDecimal}.
     *
     * @param formattedValue raw string from the API response field
     * @return parsed value, or {@code null} if the input is null/blank/unparseable
     */
    public static BigDecimal parse(String formattedValue) {
        if (formattedValue == null || formattedValue.isBlank()) return null;

        String cleaned = STRIP.matcher(formattedValue.trim()).replaceAll("");
        if (cleaned.isEmpty()) return null;

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("CurrencyNormalizer: cannot parse '{}' (cleaned: '{}')", formattedValue, cleaned);
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Numeric convenience overloads (already BigDecimal — identity)
    // -------------------------------------------------------------------------

    /**
     * Returns the value unchanged.  Provided so callers can apply the normaliser
     * uniformly without checking whether the extracted value was already numeric.
     */
    public static BigDecimal parse(BigDecimal value) {
        return value;
    }

    /** Wraps a long as BigDecimal. */
    public static BigDecimal parse(long value) {
        return BigDecimal.valueOf(value);
    }

    // -------------------------------------------------------------------------
    // Percentage helper
    // -------------------------------------------------------------------------

    /**
     * Strips a trailing {@code %} sign and returns the numeric value.
     * {@code "40.00%"} → {@code BigDecimal("40.00")}.
     * Delegates to {@link #parse(String)}.
     */
    public static BigDecimal parsePercentage(String formattedPct) {
        return parse(formattedPct);   // STRIP pattern already removes '%'
    }
}
