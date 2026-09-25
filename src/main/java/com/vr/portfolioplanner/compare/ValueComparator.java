package com.vr.portfolioplanner.compare;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Atomic comparison operations that produce zero or one {@link Mismatch}.
 *
 * <p>Every method returns {@link Optional#empty()} when the values are
 * considered equal, and an {@link Optional} containing a {@link Mismatch}
 * when they are not.  No exceptions are thrown.
 */
public final class ValueComparator {

    private ValueComparator() {}

    // -------------------------------------------------------------------------
    // String comparison
    // -------------------------------------------------------------------------

    /**
     * Exact string equality (null-safe).
     * Produces {@link MismatchType#VALUE_MISMATCH} on difference,
     * {@link MismatchType#TYPE_MISMATCH} when one side is null and the other is not.
     */
    public static Optional<Mismatch> compareStrings(
            String testCaseId, String fieldPath, String a, String b) {

        if (Objects.equals(a, b)) return Optional.empty();

        MismatchType type = (a == null || b == null)
            ? MismatchType.TYPE_MISMATCH
            : MismatchType.VALUE_MISMATCH;

        return Optional.of(Mismatch.builder(testCaseId, type)
            .fieldPath(fieldPath)
            .api1Value(str(a))
            .api2Value(str(b))
            .message("String values differ")
            .build());
    }

    // -------------------------------------------------------------------------
    // Numeric comparison with tolerance
    // -------------------------------------------------------------------------

    /**
     * Compares two {@link BigDecimal} values within an absolute {@code tolerance}.
     * <ul>
     *   <li>If {@code |a − b| ≤ tolerance} → equal (empty Optional).</li>
     *   <li>If one side is null but not the other → {@link MismatchType#TYPE_MISMATCH}.</li>
     *   <li>If {@code |a − b| > tolerance} → {@link MismatchType#BUSINESS_VALUE_MISMATCH}.</li>
     * </ul>
     */
    public static Optional<Mismatch> compareAmounts(
            String testCaseId, String fieldPath,
            BigDecimal a, BigDecimal b, BigDecimal tolerance) {

        if (a == null && b == null) return Optional.empty();

        if (a == null || b == null) {
            return Optional.of(Mismatch.builder(testCaseId, MismatchType.TYPE_MISMATCH)
                .fieldPath(fieldPath)
                .api1Value(str(a))
                .api2Value(str(b))
                .message("One side is null; the other is not")
                .build());
        }

        BigDecimal diff = a.subtract(b).abs();
        if (diff.compareTo(tolerance) <= 0) return Optional.empty();

        return Optional.of(Mismatch.builder(testCaseId, MismatchType.BUSINESS_VALUE_MISMATCH)
            .fieldPath(fieldPath)
            .api1Value(a.toPlainString())
            .api2Value(b.toPlainString())
            .difference(diff.toPlainString())
            .tolerance(tolerance.toPlainString())
            .message("Values differ by " + diff.toPlainString()
                     + " (tolerance: " + tolerance.toPlainString() + ")")
            .build());
    }

    // -------------------------------------------------------------------------
    // Presence checks
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link MismatchType#MISSING_FIELD} mismatch: field present in
     * API-1 but absent in API-2.
     */
    public static Mismatch missingInApi2(String testCaseId, String fieldPath,
                                         String api1Value, String detail) {
        return Mismatch.builder(testCaseId, MismatchType.MISSING_FIELD)
            .fieldPath(fieldPath)
            .api1Value(api1Value)
            .api2Value("(not found)")
            .message(detail)
            .build();
    }

    /**
     * Creates an {@link MismatchType#EXTRA_FIELD} mismatch: field present in
     * API-2 but absent in API-1.
     */
    public static Mismatch extraInApi2(String testCaseId, String fieldPath,
                                        String api2Value, String detail) {
        return Mismatch.builder(testCaseId, MismatchType.EXTRA_FIELD)
            .fieldPath(fieldPath)
            .api1Value("(not found)")
            .api2Value(api2Value)
            .message(detail)
            .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static String str(Object v) {
        return v == null ? "(null)" : v.toString();
    }

    static void addIfPresent(java.util.List<Mismatch> list, Optional<Mismatch> opt) {
        opt.ifPresent(list::add);
    }
}
