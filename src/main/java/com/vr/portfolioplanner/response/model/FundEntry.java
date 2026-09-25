package com.vr.portfolioplanner.response.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value object holding the comparable business fields for one fund
 * allocation entry extracted from either API's {@code funds_data.data[]}.
 *
 * <h3>Field comparability</h3>
 * <pre>
 *   planId       API-1: plan_data.plan_id          API-2: plan_data.plan_id       COMPARABLE
 *   planName     API-1: plan_data.name              API-2: plan_data.name          COMPARABLE
 *                Apply FundNameNormalizer before comparison ("Dir-G" → "Direct-G")
 *   categoryId   API-1: category_data.category_id  API-2: category_data.category_id  COMPARABLE
 *   categoryFmt  API-1: category_data.category_fmt API-2: category_data.category_fmt COMPARABLE
 *   amount       API-1: first_month_amount          API-2: txn_data.amount         COMPARABLE
 *                Apply CurrencyNormalizer; use configurable tolerance
 *   legs         API-1: legs[].type/type_fmt        API-2: inv_data[].txn_type_name COMPARABLE
 *                TransactionLeg.normalizedType is already canonical at construction
 * </pre>
 *
 * <h3>NOT_COMPARABLE / EXCLUDED (documented, not stored here)</h3>
 * <pre>
 *   legs[].frequency / duration  — API-1 only; no equivalent in API-2 inv_data
 *   port_builder_id, output_id   — generated technical IDs; excluded by design
 * </pre>
 */
public final class FundEntry {

    /** Sentinel used in documentation/notes for unmappable fields. */
    public static final String NOT_COMPARABLE = "NOT_COMPARABLE";

    private final String              planId;
    private final String              planName;
    private final String              categoryId;
    private final String              categoryFmt;
    /** First-month investment amount (API-1: first_month_amount; API-2: txn_data.amount). */
    private final BigDecimal          amount;
    /**
     * Transaction legs for this fund.
     * API-1: from {@code legs[].type} / {@code legs[].type_fmt}.
     * API-2: from {@code inv_data[].txn_type_name}.
     * Each {@link TransactionLeg} carries {@code normalizedType} for comparison.
     */
    private final List<TransactionLeg> legs;

    private FundEntry(Builder b) {
        this.planId      = b.planId;
        this.planName    = b.planName;
        this.categoryId  = b.categoryId;
        this.categoryFmt = b.categoryFmt;
        this.amount      = b.amount;
        this.legs        = Collections.unmodifiableList(
                               b.legs != null ? new ArrayList<>(b.legs) : new ArrayList<>());
    }

    public String               getPlanId()      { return planId; }
    public String               getPlanName()    { return planName; }
    public String               getCategoryId()  { return categoryId; }
    public String               getCategoryFmt() { return categoryFmt; }
    public BigDecimal           getAmount()      { return amount; }
    public List<TransactionLeg> getLegs()        { return legs; }

    @Override
    public String toString() {
        return String.format("FundEntry{planId='%s', name='%s', cat='%s'(%s), amount=%s, legs=%d}",
            planId, planName, categoryFmt, categoryId, amount, legs.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FundEntry other)) return false;
        return Objects.equals(planId, other.planId)
            && Objects.equals(planName, other.planName)
            && Objects.equals(categoryId, other.categoryId)
            && Objects.equals(categoryFmt, other.categoryFmt)
            && (amount == null ? other.amount == null
                               : other.amount != null && amount.compareTo(other.amount) == 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planId, planName, categoryId, categoryFmt);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String              planId;
        private String              planName;
        private String              categoryId;
        private String              categoryFmt;
        private BigDecimal          amount;
        private List<TransactionLeg> legs;

        public Builder planId(String v)             { this.planId      = v; return this; }
        public Builder planName(String v)           { this.planName    = v; return this; }
        public Builder categoryId(String v)         { this.categoryId  = v; return this; }
        public Builder categoryFmt(String v)        { this.categoryFmt = v; return this; }
        public Builder amount(BigDecimal v)         { this.amount      = v; return this; }
        public Builder legs(List<TransactionLeg> v) { this.legs        = v; return this; }

        public FundEntry build() { return new FundEntry(this); }
    }
}
