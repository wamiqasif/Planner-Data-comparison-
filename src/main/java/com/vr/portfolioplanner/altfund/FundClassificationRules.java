package com.vr.portfolioplanner.altfund;

import java.util.Set;

/**
 * Extensible, currently-empty reference data for Rules 2 and 4 of
 * {@link AlternateFundValidator}.
 *
 * <p>No PRD or business-rule document exists anywhere in this repository for
 * any of these values (confirmed by search). Per explicit product direction,
 * none of them may be guessed or inferred. Unlike Rule 4's {@link #NOT_RATED_CATEGORY_NAMES}
 * exception, Rule 2's {@link #PROHIBITED_CATEGORY_NAMES} being empty is not a
 * data gap: it means the rule evaluates against zero known prohibited
 * classifications, i.e. PASSes. Populating either set with the exact values
 * the business supplies is the only change needed to make that exception/rule
 * fully live — no other code changes.
 */
final class FundClassificationRules {

    /** Rule 2 — exact {@code category_name} values that are prohibited (ETF, closed-end, solution-oriented, etc). Empty means none are prohibited yet, not "unknown". */
    static final Set<String> PROHIBITED_CATEGORY_NAMES = Set.of();

    /** Rule 4 exception — {@code category_name} values where funds are not rated. None applied yet (explicit product decision). */
    static final Set<String> NOT_RATED_CATEGORY_NAMES = Set.of();

    /** Rule 5 — the exact {@code tag_name} value meaning "watchlisted". Not established yet. */
    static final String WATCHLIST_TAG_VALUE = null;

    private FundClassificationRules() {}
}
