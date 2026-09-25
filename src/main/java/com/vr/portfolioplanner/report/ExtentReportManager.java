package com.vr.portfolioplanner.report;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.vr.portfolioplanner.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Singleton manager for the ExtentReports HTML report.
 *
 * <p>Output: {@code target/reports/PortfolioPlanner_ExtentReport.html}
 *
 * <p>Usage pattern in TestNG:
 * <pre>
 *   // @BeforeSuite or first call in @Test
 *   ExtentReportManager.getInstance();  // initialises on first call
 *
 *   // @AfterSuite
 *   ExtentReportManager.flush();        // writes HTML to disk
 * </pre>
 *
 * <p>Thread-safe: {@code getInstance()} is synchronised; the underlying
 * {@link ExtentReports} object is also thread-safe for concurrent test writes.
 */
public final class ExtentReportManager {

    private static final Logger log = LoggerFactory.getLogger(ExtentReportManager.class);

    private static final Path REPORT_DIR  = Paths.get("target", "reports");
    public static final  Path REPORT_FILE = REPORT_DIR.resolve("PortfolioPlanner_ExtentReport.html");

    private static volatile ExtentReports instance;

    private ExtentReportManager() {}

    /** Returns the shared {@link ExtentReports} instance, initialising it on first call. */
    public static synchronized ExtentReports getInstance() {
        if (instance == null) {
            instance = build();
        }
        return instance;
    }

    /**
     * Flushes the report to disk.  Safe to call even if the report was never
     * initialised (e.g. when all tests are skipped).
     */
    public static synchronized void flush() {
        if (instance != null) {
            instance.flush();
            log.info("Extent report written: {}", REPORT_FILE.toAbsolutePath());
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static ExtentReports build() {
        try {
            Files.createDirectories(REPORT_DIR);
        } catch (IOException e) {
            log.warn("Could not create report directory {}: {}", REPORT_DIR, e.getMessage());
        }

        ExtentSparkReporter spark = new ExtentSparkReporter(REPORT_FILE.toFile());
        spark.config().setDocumentTitle("Portfolio Planner – API Comparison Report");
        spark.config().setReportName("API-1 vs API-2 Business Response Comparison");
        spark.config().setTheme(Theme.STANDARD);
        spark.config().setTimeStampFormat("yyyy-MM-dd HH:mm:ss");
        spark.config().setEncoding("UTF-8");

        ExtentReports extent = new ExtentReports();
        extent.attachReporter(spark);
        extent.setSystemInfo("Framework",   "Java 17 + Maven + TestNG + REST Assured");
        extent.setSystemInfo("API-1 Base",  "https://qaappapi.valueresearch.in");
        extent.setSystemInfo("API-2 Base",  "https://advappapi.valueresearchonline.com");
        extent.setSystemInfo("Amount Tolerance",
            String.valueOf(ConfigReader.getInstance().getComparisonAmountTolerance()));
        extent.setSystemInfo("Percentage Tolerance",
            String.valueOf(ConfigReader.getInstance().getComparisonPercentageTolerance()));

        log.info("ExtentReports initialised → {}", REPORT_FILE.toAbsolutePath());
        return extent;
    }
}
