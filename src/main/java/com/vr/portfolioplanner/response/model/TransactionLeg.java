package com.vr.portfolioplanner.response.model;

import com.vr.portfolioplanner.normalize.TransactionTypeNormalizer;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents one transaction leg extracted from a fund entry.
 *
 * <p>API-1 source: {@code legs[].type} / {@code legs[].type_fmt}<br>
 * API-2 source: {@code inv_data[].txn_type_name}
 *
 * <p>The {@link #normalizedType} is computed automatically at construction time
 * by {@link TransactionTypeNormalizer}, so the same canonical string can be used
 * for comparison regardless of which API produced the entry.
 *
 * <pre>
 *   API-1 "sip"       → normalizedType = "SIP"
 *   API-1 "one_time"  → normalizedType = "ONE_TIME"
 *   API-2 "SIP"       → normalizedType = "SIP"
 *   API-2 "One-time"  → normalizedType = "ONE_TIME"
 * </pre>
 */
public final class TransactionLeg {

    /** The raw string as it appeared in the JSON (for traceability). */
    private final String rawType;

    /**
     * Canonical type after applying {@link TransactionTypeNormalizer}.
     * Use this field for cross-API comparison, not {@link #rawType}.
     */
    private final String normalizedType;

    /** Amount for this leg in the same currency unit as the parent fund. */
    private final BigDecimal amount;

    /**
     * @param rawType the type string exactly as extracted from the JSON
     * @param amount  monetary amount for this leg
     */
    public TransactionLeg(String rawType, BigDecimal amount) {
        this.rawType        = rawType;
        this.normalizedType = TransactionTypeNormalizer.normalize(rawType);
        this.amount         = amount;
    }

    public String     getRawType()        { return rawType; }
    public String     getNormalizedType() { return normalizedType; }
    public BigDecimal getAmount()         { return amount; }

    /** Returns {@code true} if the normalized type equals {@link TransactionTypeNormalizer#SIP}. */
    public boolean isSip()     { return TransactionTypeNormalizer.SIP.equals(normalizedType); }

    /** Returns {@code true} if the normalized type equals {@link TransactionTypeNormalizer#ONE_TIME}. */
    public boolean isOneTime() { return TransactionTypeNormalizer.ONE_TIME.equals(normalizedType); }

    @Override
    public String toString() {
        return String.format("TransactionLeg{raw='%s', normalized='%s', amount=%s}",
            rawType, normalizedType, amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionLeg other)) return false;
        return Objects.equals(normalizedType, other.normalizedType)
            && (amount == null ? other.amount == null
                               : other.amount != null && amount.compareTo(other.amount) == 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedType);
    }
}
