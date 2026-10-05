package com.vr.portfolioplanner.altfund;

/**
 * Classification of one fund slot's outcome when comparing API-1 against
 * API-2 (see {@link AlternateFundValidator}). API-2 is always the
 * original/reference fund; API-1 is the alternate candidate when its
 * {@code plan_id} differs.
 */
public enum MatchType {

    /** Same {@code plan_id} on both sides — no alternate validation required. */
    DIRECT_MATCH,

    /** Different {@code plan_id}s, uniquely paired by category_name, and the API-1 alternate passed all applicable rules. */
    ALTERNATE_MATCH,

    /** Different {@code plan_id}s, uniquely paired by category_name, and the API-1 alternate failed at least one rule. */
    ALTERNATE_REJECTED,

    /**
     * Fallback pairing used only when category_name matching could not resolve a fund at
     * all (no shared category on the other side — not the ambiguous case) AND API-1's total
     * fund count equals API-2's total fund count AND the still-unresolved counts on both
     * sides are equal: paired strictly by original response order/serial position. The API-1
     * alternate passed all applicable rules.
     */
    SERIAL_ALTERNATE_MATCH,

    /** Same fallback-eligibility as {@link #SERIAL_ALTERNATE_MATCH}, but the API-1 alternate failed at least one rule. */
    SERIAL_ALTERNATE_REJECTED,

    /** Multiple unmatched funds on one or both sides share the same category_name — pairing cannot be uniquely resolved. */
    PAIRING_AMBIGUOUS,

    /** A pairing/rule decision could not be made because required data (a fetch, or reference data) is unavailable. */
    REQUIRED_DATA_NOT_AVAILABLE;

    /** Whether this outcome is a fully-accepted substitute (by either pairing method) — used for fund-count accounting. */
    public boolean isAcceptedMatch() {
        return this == ALTERNATE_MATCH || this == SERIAL_ALTERNATE_MATCH;
    }
}
