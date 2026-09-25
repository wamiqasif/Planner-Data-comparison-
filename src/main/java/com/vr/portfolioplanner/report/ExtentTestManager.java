package com.vr.portfolioplanner.report;

import com.aventstack.extentreports.ExtentTest;

/**
 * ThreadLocal holder for the {@link ExtentTest} associated with the
 * currently-running TestNG test method.
 *
 * <p>Each thread gets its own {@link ExtentTest} so that concurrent DataProvider
 * invocations do not share a single test node.  Call {@link #start} at the
 * beginning of each test and {@link #remove} in a finally block or @AfterMethod.
 */
public final class ExtentTestManager {

    private static final ThreadLocal<ExtentTest> CURRENT = new ThreadLocal<>();

    private ExtentTestManager() {}

    /**
     * Creates a new {@link ExtentTest} for the current thread and stores it.
     *
     * @param name        test display name
     * @param description one-line description (shown below the name in the report)
     * @return the newly created test
     */
    public static ExtentTest start(String name, String description) {
        ExtentTest test = ExtentReportManager.getInstance().createTest(name, description);
        CURRENT.set(test);
        return test;
    }

    /** Returns the {@link ExtentTest} for the current thread; {@code null} if not started. */
    public static ExtentTest get() {
        return CURRENT.get();
    }

    /** Removes the {@link ExtentTest} from the current thread's context. */
    public static void remove() {
        CURRENT.remove();
    }
}
