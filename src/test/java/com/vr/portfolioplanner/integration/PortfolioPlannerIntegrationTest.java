package com.vr.portfolioplanner.integration;

import com.vr.portfolioplanner.client.Api1Client;
import com.vr.portfolioplanner.client.Api2Client;
import com.vr.portfolioplanner.client.ApiResponse;
import com.vr.portfolioplanner.config.HeaderManager;
import com.vr.portfolioplanner.excel.ExcelFixtureBootstrap;
import com.vr.portfolioplanner.excel.ExcelReader;
import com.vr.portfolioplanner.model.TestData;
import com.vr.portfolioplanner.payload.Api1JsonPayloadBuilder;
import com.vr.portfolioplanner.payload.Api2FormDataPayloadBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * TEMPORARY integration smoke test.
 *
 * <p>Calls both APIs with the sample Excel row and prints status + timing.
 * Response bodies are not compared here — that comes in a later step.
 *
 * <p>Expected result without credentials: 4xx (unauthorized).
 * The test still passes because the assertion only verifies that both
 * clients returned an ApiResponse object without crashing.
 *
 * <p>Printed output:
 * <pre>
 *   TestCaseID   : TC_001
 *   API-1 status : &lt;http-status or ERROR&gt;
 *   API-2 status : &lt;http-status or ERROR&gt;
 *   API-1 time   : &lt;ms&gt;
 *   API-2 time   : &lt;ms&gt;
 * </pre>
 */
public class PortfolioPlannerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PortfolioPlannerIntegrationTest.class);

    private static final ExcelReader READER;

    static {
        try {
            ExcelFixtureBootstrap.generateIfAbsent();
            READER = ExcelReader.fromFile(ExcelFixtureBootstrap.getFixturePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test fixture", e);
        }
    }

    @Test(groups = "integration",
          description = "Call both APIs with sample Excel row; print status and response time")
    public void callBothApisAndPrintStatus() {
        List<TestData> rows = READER.getExecutableRows();
        Assert.assertFalse(rows.isEmpty(), "No Execute=Y rows found in the Excel fixture");

        TestData td = rows.get(0);
        HeaderManager hm = new HeaderManager();

        // Payloads built by dedicated builders — not inline here
        String              api1Json   = Api1JsonPayloadBuilder.build(td);
        Map<String, String> api2Fields = Api2FormDataPayloadBuilder.build(td);

        // Call both APIs
        ApiResponse api1Response = new Api1Client().post(api1Json,   hm.getApi1Headers());
        ApiResponse api2Response = new Api2Client().post(api2Fields, hm.getApi2Headers());

        // ---- Required output ----
        log.info("======================================================");
        log.info("  TestCaseID   : {}", td.getTestCaseId());
        log.info("  API-1 status : {}", statusLabel(api1Response));
        log.info("  API-2 status : {}", statusLabel(api2Response));
        log.info("  API-1 time   : {} ms", api1Response.getResponseTimeMs());
        log.info("  API-2 time   : {} ms", api2Response.getResponseTimeMs());
        log.info("======================================================");

        log.debug("API-1 body (300 chars): {}", truncate(api1Response.getResponseBody(), 300));
        log.debug("API-2 body (300 chars): {}", truncate(api2Response.getResponseBody(), 300));

        // Assertions: responses are objects (not null), statusCode is initialised.
        // No status-code assertions — 4xx expected without credentials.
        Assert.assertNotNull(api1Response);
        Assert.assertNotNull(api2Response);
        Assert.assertTrue(api1Response.getStatusCode() != 0,
            "API-1 statusCode must be an HTTP code or -1; got 0 (uninitialised)");
        Assert.assertTrue(api2Response.getStatusCode() != 0,
            "API-2 statusCode must be an HTTP code or -1; got 0 (uninitialised)");
    }

    private static String statusLabel(ApiResponse r) {
        return r.isNetworkError()
            ? "ERROR (" + r.getErrorMessage() + ")"
            : String.valueOf(r.getStatusCode());
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "(empty)";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
