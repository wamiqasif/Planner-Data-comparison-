package com.vr.portfolioplanner.test;

import com.aventstack.extentreports.ExtentTest;
import com.vr.portfolioplanner.client.Api1Client;
import com.vr.portfolioplanner.client.Api2Client;
import com.vr.portfolioplanner.client.ApiResponse;
import com.vr.portfolioplanner.compare.ComparisonResult;
import com.vr.portfolioplanner.compare.MismatchType;
import com.vr.portfolioplanner.compare.ResponseComparator;
import com.vr.portfolioplanner.config.HeaderManager;
import com.vr.portfolioplanner.excel.ExcelFixtureBootstrap;
import com.vr.portfolioplanner.excel.ExcelReader;
import com.vr.portfolioplanner.model.TestData;
import com.vr.portfolioplanner.normalize.ResponseNormalizer;
import com.vr.portfolioplanner.payload.Api1JsonPayloadBuilder;
import com.vr.portfolioplanner.payload.Api2FormDataPayloadBuilder;
import com.vr.portfolioplanner.report.*;
import com.vr.portfolioplanner.response.Api1ResponseExtractor;
import com.vr.portfolioplanner.response.Api2ResponseExtractor;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import com.vr.portfolioplanner.response.model.FundEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * End-to-end Excel-driven comparison test for Portfolio Planner APIs.
 *
 * <h3>Per-row pipeline</h3>
 * <ol>
 *   <li>DataProvider: Execute=Y rows from Excel</li>
 *   <li>Build API-1 JSON payload  — {@link Api1JsonPayloadBuilder}</li>
 *   <li>Build API-2 form-data     — {@link Api2FormDataPayloadBuilder}</li>
 *   <li>Call API-1                — {@link Api1Client}</li>
 *   <li>Call API-2                — {@link Api2Client}</li>
 *   <li>Capture {@link ApiResponse} objects</li>
 *   <li>Extract business data     — {@link Api1ResponseExtractor} / {@link Api2ResponseExtractor}</li>
 *   <li>Normalize                 — {@link ResponseNormalizer}</li>
 *   <li>Compare (ALL mismatches)  — {@link ResponseComparator}</li>
 *   <li>Report                    — ExtentReports + Excel</li>
 *   <li>Assert PASS/FAIL after full mismatch collection</li>
 * </ol>
 */
public class ApiComparisonTest {

    private static final Logger log = LoggerFactory.getLogger(ApiComparisonTest.class);

    private static final Path REPORT_PATH =
        Paths.get("target", "reports", "API_Comparison_Result.xlsx");

    private static final DateTimeFormatter REQUEST_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final ExcelReader READER;

    static {
        try {
            ExcelFixtureBootstrap.generateIfAbsent();
            READER = ExcelReader.fromFile(ExcelFixtureBootstrap.getFixturePath());
            log.info("ApiComparisonTest: {} executable row(s) loaded",
                READER.executableRowCount());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Excel fixture", e);
        }
    }

    // -------------------------------------------------------------------------
    // Suite lifecycle
    // -------------------------------------------------------------------------

    @BeforeSuite(alwaysRun = true)
    public void initReports() {
        try { Files.createDirectories(Paths.get("target", "reports")); } catch (IOException ignored) {}
        ExtentReportManager.getInstance(); // initialise early
        log.info("Extent report initialised → {}",
            ExtentReportManager.REPORT_FILE.toAbsolutePath());
    }

    @AfterSuite(alwaysRun = true)
    public void generateReport() {
        // Excel
        try {
            Files.createDirectories(REPORT_PATH.getParent());
            ExcelResultWriter.write(ResultCollector.getAll(), REPORT_PATH);
        } catch (Exception e) {
            log.error("Excel report generation failed: {}", e.getMessage(), e);
        }

        // ExtentReports
        ExtentReportManager.flush();

        // Summary log
        long passed = ResultCollector.getAll().stream()
            .filter(r -> "PASS".equals(r.getExecutionStatus())).count();
        long failed = ResultCollector.getAll().stream()
            .filter(r -> "FAIL".equals(r.getExecutionStatus())).count();

        log.info("╔══════════════════════════════════════════════╗");
        log.info("  Excel  : {}", REPORT_PATH.toAbsolutePath());
        log.info("  Extent : {}", ExtentReportManager.REPORT_FILE.toAbsolutePath());
        log.info("  Rows   : {}  PASS={}  FAIL={}", ResultCollector.size(), passed, failed);
        log.info("╚══════════════════════════════════════════════╝");
    }

    // -------------------------------------------------------------------------
    // DataProvider
    // -------------------------------------------------------------------------

    /**
     * Supplies all 6,750 Excel rows. {@code parallel=true} allows TestNG to
     * dispatch up to {@code data-provider-thread-count} rows concurrently —
     * set in testng.xml on the API Comparison Tests &lt;test&gt; element.
     */
    @DataProvider(name = "portfolioPlannerData", parallel = true)
    public Object[][] portfolioPlannerData() {
        return READER.asDataProvider();
    }

    // -------------------------------------------------------------------------
    // Main test
    // -------------------------------------------------------------------------

    @Test(
        dataProvider = "portfolioPlannerData",
        groups       = "api-comparison",
        description  = "Portfolio Planner API-1 vs API-2 business response comparison"
    )
    public void comparePortfolioPlanner(TestData testData) {

        String testCaseId = testData.getTestCaseId();

        // Start Extent test
        ExtentTest extentTest = ExtentTestManager.start(
            testCaseId + " — Portfolio Planner API Comparison",
            testData.getDescription()
        );

        TestCaseResult.Builder rb = TestCaseResult.builder()
            .testCaseId(testCaseId)
            .description(testData.getDescription());

        ApiResponse       api1Response = null;
        ApiResponse       api2Response = null;
        String            api1Json     = null;
        Map<String, String> api2Fields = null;
        ExtractedResponse norm1        = ExtractedResponse.failed("API-1", "not run");
        ExtractedResponse norm2        = ExtractedResponse.failed("API-2", "not run");
        ComparisonResult  compResult   = null;

        try {
            // ── Step 3 & 4: build payloads ───────────────────────────────────
            api1Json   = Api1JsonPayloadBuilder.build(testData);
            api2Fields = Api2FormDataPayloadBuilder.build(testData);

            HeaderManager hm = new HeaderManager();

            // ── Steps 5 & 6: call both APIs ──────────────────────────────────
            api1Response = new Api1Client().post(api1Json,   hm.getApi1Headers());
            api2Response = new Api2Client().post(api2Fields, hm.getApi2Headers());

            // ── Step 7: log HTTP layer ────────────────────────────────────────
            log.info("API-1 → HTTP {} in {} ms (body {} bytes)",
                api1Response.getStatusCode(), api1Response.getResponseTimeMs(),
                api1Response.getResponseBody().length());
            log.info("API-2 → HTTP {} in {} ms (body {} bytes)",
                api2Response.getStatusCode(), api2Response.getResponseTimeMs(),
                api2Response.getResponseBody().length());

            // ── Step 8: extract ───────────────────────────────────────────────
            ExtractedResponse ext1 = Api1ResponseExtractor.extract(api1Response.getResponseBody());
            ExtractedResponse ext2 = Api2ResponseExtractor.extract(api2Response.getResponseBody());

            // ── Step 9: normalize ─────────────────────────────────────────────
            norm1 = ResponseNormalizer.normalize(ext1);
            norm2 = ResponseNormalizer.normalize(ext2);

            // ── Steps 10–12: compare ──────────────────────────────────────────
            compResult = ResponseComparator.compare(
                testCaseId, api1Response, api2Response, norm1, norm2);

            // ── Compute fund stats ────────────────────────────────────────────
            Set<String> ids1 = fundPlanIds(norm1);
            Set<String> ids2 = fundPlanIds(norm2);
            int matchedFunds = (int) ids1.stream().filter(ids2::contains).count();
            int missingFunds = (int) ids1.stream().filter(id -> !ids2.contains(id)).count();
            int extraFunds   = (int) ids2.stream().filter(id -> !ids1.contains(id)).count();

            // ── Build result record ───────────────────────────────────────────
            rb.executionStatus(compResult.isPassed() ? "PASS" : "FAIL")
              .api1HttpStatus(api1Response.getStatusCode())
              .api2HttpStatus(api2Response.getStatusCode())
              .api1ResponseTimeMs(api1Response.getResponseTimeMs())
              .api2ResponseTimeMs(api2Response.getResponseTimeMs())
              .api1RequestTimestamp(api1Response.getRequestTimestamp().format(REQUEST_TIMESTAMP_FORMAT))
              .api2RequestTimestamp(api2Response.getRequestTimestamp().format(REQUEST_TIMESTAMP_FORMAT))
              .api1Payload(api1Json)
              .api2Payload(formatApi2Fields(api2Fields))
              .api1ResponseBody(api1Response.getResponseBody())
              .api2ResponseBody(api2Response.getResponseBody())
              .api1FundCount(norm1.isExtractionSuccess() ? norm1.getFunds().size() : -1)
              .api2FundCount(norm2.isExtractionSuccess() ? norm2.getFunds().size() : -1)
              .matchedFundCount(matchedFunds)
              .missingFundCount(missingFunds)
              .extraFundCount(extraFunds)
              .comparisonResult(compResult.getVerdict())
              .mismatchCount(compResult.getFailingMismatchCount())
              .normalizationDiffCount(
                  compResult.getByType(MismatchType.NORMALIZATION_DIFFERENCE).size())
              .mismatchDetails(ExcelResultWriter.formatMismatchDetails(compResult))
              .mismatches(compResult.getMismatches())
              .alternateFundAudits(compResult.getAlternateFundAudits());

            // ── Console output ────────────────────────────────────────────────
            log.info(ComparisonReportFormatter.formatConsoleBlock(
                testCaseId, compResult, norm1, norm2,
                api1Response.getStatusCode(), api2Response.getStatusCode(),
                api1Response.getResponseTimeMs(), api2Response.getResponseTimeMs()));

            // ── Extent report population ──────────────────────────────────────
            ComparisonReportFormatter.populateExtentTest(
                extentTest, testCaseId, compResult, norm1, norm2,
                api1Response.getStatusCode(), api2Response.getStatusCode(),
                api1Response.getResponseTimeMs(), api2Response.getResponseTimeMs());

            // ── Assert AFTER all mismatches collected ─────────────────────────
            Assert.assertTrue(compResult.isPassed(), buildFailureMessage(compResult));

        } catch (AssertionError ae) {
            throw ae;

        } catch (Exception e) {
            rb.executionStatus("ERROR")
              .comparisonResult("N/A")
              .api1Payload(api1Json)
              .api2Payload(formatApi2Fields(api2Fields))
              .api1ResponseBody(api1Response != null ? api1Response.getResponseBody() : null)
              .api2ResponseBody(api2Response != null ? api2Response.getResponseBody() : null)
              .mismatchDetails("Execution error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (extentTest != null) extentTest.fail("Unexpected error: " + e.getMessage());
            log.error("Unexpected error for {}: {}", testCaseId, e.getMessage(), e);
            Assert.fail("Execution error for " + testCaseId + ": " + e.getMessage());

        } finally {
            ResultCollector.add(rb.build());
            // Progress log every 250 completed scenarios
            int done = ResultCollector.size();
            if (done % 250 == 0 || done == 1) {
                long pass = ResultCollector.getAll().stream()
                    .filter(r -> "PASS".equals(r.getExecutionStatus())).count();
                long fail = ResultCollector.getAll().stream()
                    .filter(r -> "FAIL".equals(r.getExecutionStatus())).count();
                log.info("Progress: {}/{} done  PASS={}  FAIL={}",
                    done, READER.executableRowCount(), pass, fail);
            }
            ExtentTestManager.remove();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Renders API-2's form-data field map as "key=value" lines for the Excel report. */
    private static String formatApi2Fields(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) return "";
        return fields.entrySet().stream()
            .map(en -> en.getKey() + "=" + en.getValue())
            .collect(Collectors.joining("\n"));
    }

    private static Set<String> fundPlanIds(ExtractedResponse r) {
        if (!r.isExtractionSuccess()) return Set.of();
        return r.getFunds().stream()
                .map(FundEntry::getPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static String buildFailureMessage(ComparisonResult result) {
        String mismatchLines = result.getFailingMismatches().stream()
            .map(m -> {
                StringBuilder sb = new StringBuilder(String.format(
                    "  [%-25s] %s%n      api1: %s%n      api2: %s",
                    m.getMismatchType(), m.getFieldPath(),
                    m.getApi1Value(), m.getApi2Value()));
                if (m.getPlanId()       != null) sb.append("\n      planId: ").append(m.getPlanId());
                if (m.getApi1FundName() != null) sb.append("\n      fund1:  ").append(m.getApi1FundName());
                if (m.getApi2FundName() != null) sb.append("\n      fund2:  ").append(m.getApi2FundName());
                if (m.getDifference()   != null) sb.append("\n      diff:   ").append(m.getDifference());
                if (m.getMessage()      != null) sb.append("\n      note:   ").append(m.getMessage());
                return sb.toString();
            })
            .collect(Collectors.joining("\n"));

        return String.format("%n%s — FAILED with %d mismatch(es):%n%s",
            result.getTestCaseId(), result.getFailingMismatchCount(), mismatchLines);
    }
}
