package com.vr.portfolioplanner.report;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe accumulator for {@link TestCaseResult} objects produced during
 * a test run.
 *
 * <p>Each {@link com.vr.portfolioplanner.test.ApiComparisonTest} row appends
 * one result via {@link #add(TestCaseResult)}.  After the suite completes,
 * {@link com.vr.portfolioplanner.report.ExcelResultWriter} reads the full list
 * via {@link #getAll()} and writes the Excel report.
 *
 * <p>State is JVM-scoped: each {@code mvn test} invocation starts with an
 * empty list.
 */
public final class ResultCollector {

    private static final List<TestCaseResult> RESULTS = new CopyOnWriteArrayList<>();

    private ResultCollector() {}

    /** Appends one result to the collection. */
    public static void add(TestCaseResult result) {
        if (result != null) RESULTS.add(result);
    }

    /** Returns an unmodifiable snapshot of all collected results. */
    public static List<TestCaseResult> getAll() {
        return Collections.unmodifiableList(RESULTS);
    }

    public static int size() { return RESULTS.size(); }

    /** Clears all collected results (test-internal use only). */
    public static void clear() { RESULTS.clear(); }
}
