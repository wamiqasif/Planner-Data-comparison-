package com.vr.portfolioplanner.response.model;

import com.vr.portfolioplanner.normalize.CurrencyNormalizer;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable value object for one entry in {@code breakdown_funds_data.data[]}.
 * Both API-1 and API-2 return this structure; all fields are COMPARABLE.
 *
 * <h3>Field sources</h3>
 * <pre>
 *   categoryId  — flat field {@code category_id} in the breakdown item
 *   categoryFmt — flat field {@code category_fmt}
 *   percentage  — numeric field {@code percentage} (preferred)
 *   percFmt     — string field {@code perc_fmt} (e.g. "40.00%")
 *   amount      — numeric field {@code amount} (may be null)
 * </pre>
 *
 * <p>When {@code percentage} is absent, {@link #resolvePercentage()} falls back
 * to parsing {@code percFmt} via {@link CurrencyNormalizer#parsePercentage(String)}.
 */
public final class BreakdownEntry {

    private final String     categoryId;
    private final String     categoryFmt;
    /** Numeric percentage as extracted from the JSON {@code percentage} field. May be null. */
    private final BigDecimal percentage;
    /** Raw formatted string from the JSON {@code perc_fmt} field, e.g. "40.00%". May be null. */
    private final String     percFmt;
    /** Monetary amount for this category breakdown. May be null. */
    private final BigDecimal amount;

    public BreakdownEntry(String categoryId, String categoryFmt,
                          BigDecimal percentage, String percFmt, BigDecimal amount) {
        this.categoryId  = categoryId;
        this.categoryFmt = categoryFmt;
        this.percentage  = percentage;
        this.percFmt     = percFmt;
        this.amount      = amount;
    }

    public String     getCategoryId()  { return categoryId; }
    public String     getCategoryFmt() { return categoryFmt; }
    public BigDecimal getPercentage()  { return percentage; }
    public String     getPercFmt()     { return percFmt; }
    public BigDecimal getAmount()      { return amount; }

    /**
     * Returns the best available percentage as a {@link BigDecimal}.
     * Prefers the numeric {@code percentage} field; falls back to parsing {@code perc_fmt}.
     */
    public BigDecimal resolvePercentage() {
        if (percentage != null) return percentage;
        return CurrencyNormalizer.parsePercentage(percFmt);
    }

    @Override
    public String toString() {
        return String.format("BreakdownEntry{cat='%s'(%s), pct=%s percFmt='%s', amount=%s}",
            categoryFmt, categoryId, percentage, percFmt, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BreakdownEntry other)) return false;
        return Objects.equals(categoryId, other.categoryId)
            && Objects.equals(categoryFmt, other.categoryFmt)
            && percentageEquals(other);
    }

    private boolean percentageEquals(BreakdownEntry other) {
        BigDecimal a = resolvePercentage();
        BigDecimal b = other.resolvePercentage();
        if (a == null) return b == null;
        return b != null && a.compareTo(b) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryId, categoryFmt);
    }
}
