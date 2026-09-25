package com.vr.portfolioplanner.compare;

import com.vr.portfolioplanner.altfund.AlternateFundAudit;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Outcome of one full response comparison run for a single test case.
 *
 * <h3>Verdict rules</h3>
 * <pre>
 *   PASS — zero failing mismatches
 *          (NORMALIZATION_DIFFERENCE entries are informational; they do not cause FAIL)
 *   FAIL — one or more failing mismatches exist
 * </pre>
 */
public final class ComparisonResult {

    private final String         testCaseId;
    private final List<Mismatch> mismatches;   // all, including informational
    private final List<AlternateFundAudit> alternateFundAudits;

    public ComparisonResult(String testCaseId, List<Mismatch> mismatches) {
        this(testCaseId, mismatches, Collections.emptyList());
    }

    public ComparisonResult(String testCaseId, List<Mismatch> mismatches,
                             List<AlternateFundAudit> alternateFundAudits) {
        this.testCaseId = testCaseId;
        this.mismatches = Collections.unmodifiableList(mismatches);
        this.alternateFundAudits = Collections.unmodifiableList(alternateFundAudits);
    }

    // -------------------------------------------------------------------------
    // Verdict
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when there are no <em>failing</em> mismatches.
     * {@link MismatchType#NORMALIZATION_DIFFERENCE} entries do not affect this flag.
     */
    public boolean isPassed() { return getFailingMismatchCount() == 0; }

    public boolean isFailed() { return !isPassed(); }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String         getTestCaseId()   { return testCaseId; }

    /** All mismatches, including informational (NORMALIZATION_DIFFERENCE). */
    public List<Mismatch> getMismatches()   { return mismatches; }

    /** Only mismatches whose type is {@link MismatchType#isFailing()}. */
    public List<Mismatch> getFailingMismatches() {
        return mismatches.stream().filter(Mismatch::isFailing)
                         .collect(Collectors.toUnmodifiableList());
    }

    /** Mismatches of a specific type. */
    public List<Mismatch> getByType(MismatchType type) {
        return mismatches.stream().filter(m -> m.getMismatchType() == type)
                         .collect(Collectors.toUnmodifiableList());
    }

    /** Alternate-fund validation audit rows (one per orphaned fund that entered pairing). */
    public List<AlternateFundAudit> getAlternateFundAudits() { return alternateFundAudits; }

    public int getTotalMismatchCount()   { return mismatches.size(); }
    public int getFailingMismatchCount() { return (int) mismatches.stream().filter(Mismatch::isFailing).count(); }

    public String getVerdict() { return isPassed() ? "PASS" : "FAIL"; }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("ComparisonResult{testCase='%s' verdict=%s failing=%d total=%d}",
            testCaseId, getVerdict(), getFailingMismatchCount(), getTotalMismatchCount());
    }
}
