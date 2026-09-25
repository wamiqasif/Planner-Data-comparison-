package com.vr.portfolioplanner.normalize;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Normalizes known fund-name display differences between API-1 and API-2.
 *
 * <p>API-1 uses abbreviated plan-class suffixes; API-2 uses their full equivalents.
 * Normalization replaces the API-1 short form with the API-2 full form so that
 * both sides produce the same string for comparison.
 *
 * <h3>Known substitutions (explicit, controlled — no fuzzy matching)</h3>
 * <pre>
 *   "Dir-G"  →  "Direct-G"       (Direct Growth plan class)
 *   "ABSL"   →  "Aditya Birla SL" (Aditya Birla Sun Life — API-1 abbreviation vs API-2 partial form)
 * </pre>
 *
 * <p>Word-boundary patterns ({@code \b}) are used to prevent accidental
 * replacement inside longer tokens.
 *
 * <p>Any fund-name substring not listed here passes through unchanged.  New
 * differences discovered in production must be added to {@link #SUBSTITUTIONS}
 * explicitly, not inferred.
 */
public final class FundNameNormalizer {

    /**
     * Each element: [0] = compiled pattern (word-boundary), [1] = replacement string.
     */
    private static final List<Object[]> SUBSTITUTIONS = List.of(
        new Object[]{ Pattern.compile("\\bDir-G\\b"),  "Direct-G"       },
        new Object[]{ Pattern.compile("\\bDir- G\\b"), "Direct-G"       },
        new Object[]{ Pattern.compile("\\bABSL\\b"),   "Aditya Birla SL"}
    );

    private FundNameNormalizer() {}

    /**
     * Applies all known substitutions to {@code fundName} and returns the result.
     * Input is returned unchanged if no substitution matches.
     *
     * @param fundName raw fund name from the API response (may be null)
     * @return normalized name, or {@code null} if input is null
     */
    public static String normalize(String fundName) {
        if (fundName == null) return null;
        String result = fundName;
        for (Object[] sub : SUBSTITUTIONS) {
            result = ((Pattern) sub[0]).matcher(result).replaceAll((String) sub[1]);
        }
        return result;
    }

    /**
     * Returns {@code true} when {@code raw} and {@code normalized} would be equal
     * after applying this normalizer to {@code raw}.
     */
    public static boolean matches(String raw, String normalized) {
        if (raw == null || normalized == null) return raw == normalized;
        return normalize(raw).equals(normalized);
    }
}
