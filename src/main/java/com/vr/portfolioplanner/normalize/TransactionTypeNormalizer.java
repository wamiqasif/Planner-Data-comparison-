package com.vr.portfolioplanner.normalize;

import java.util.Map;

/**
 * Normalizes transaction-type strings from both APIs to a shared canonical vocabulary.
 *
 * <p>API-1 uses {@code legs[].type} (lower-snake) and {@code legs[].type_fmt} (display text).
 * API-2 uses {@code inv_data[].txn_type_name} (mixed-case display text).
 * Both must be normalised before comparison.
 *
 * <h3>Canonical values</h3>
 * <pre>
 *   SIP          — Systematic Investment Plan (periodic)
 *   ONE_TIME     — Single lump-sum investment
 * </pre>
 *
 * <h3>Mapping table (explicit, no fuzzy matching)</h3>
 * <pre>
 *   sip      → SIP
 *   SIP      → SIP
 *   one_time → ONE_TIME
 *   One-time → ONE_TIME
 * </pre>
 *
 * Any value not in the table is upper-cased and returned as-is so it can be
 * compared for equality while still being visible in mismatch reports.
 */
public final class TransactionTypeNormalizer {

    public static final String SIP      = "SIP";
    public static final String ONE_TIME = "ONE_TIME";

    /** Explicit lookup table — case-sensitive keys match what APIs actually send. */
    private static final Map<String, String> TABLE = Map.of(
        "sip",      SIP,
        "SIP",      SIP,
        "one_time", ONE_TIME,
        "One-time", ONE_TIME,
        "One-Time", ONE_TIME   // defensive: capitalised hyphenated variant
    );

    private TransactionTypeNormalizer() {}

    /**
     * Returns the canonical transaction type string.
     *
     * @param raw the raw value from the JSON field (may be null)
     * @return canonical string, or {@code null} if input is null/blank,
     *         or upper-cased raw value if not in the table
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String canonical = TABLE.get(raw.trim());
        return canonical != null ? canonical : raw.trim().toUpperCase().replace('-', '_');
    }

    /** Returns {@code true} when the input maps to a known canonical type. */
    public static boolean isKnown(String raw) {
        return raw != null && TABLE.containsKey(raw.trim());
    }
}
