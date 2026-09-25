package com.vr.portfolioplanner.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Minimal smoke test.
 * Purpose: verify that Maven + TestNG + all declared dependencies
 * compile and are available on the classpath.
 *
 * No API calls are made. No credentials are required.
 */
public class SmokeTest {

    private static final Logger log = LoggerFactory.getLogger(SmokeTest.class);

    @Test(groups = "smoke", description = "Verify TestNG is wired correctly")
    public void testNgSanityCheck() {
        log.info("TestNG sanity check running");
        Assert.assertTrue(true, "TestNG is wired correctly");
        log.info("TestNG sanity check PASSED");
    }

    @Test(groups = "smoke", description = "Verify REST Assured is on the classpath")
    public void restAssuredClasspathCheck() {
        log.info("Checking REST Assured classpath availability");
        // Just instantiate the class – no HTTP call, no credentials needed
        String baseUri = RestAssured.baseURI;   // default is "http://localhost"
        Assert.assertNotNull(baseUri, "REST Assured base URI should not be null");
        log.info("REST Assured default baseURI = {}", baseUri);
    }

    @Test(groups = "smoke", description = "Verify Jackson ObjectMapper is on the classpath")
    public void jacksonClasspathCheck() {
        log.info("Checking Jackson ObjectMapper classpath availability");
        ObjectMapper mapper = new ObjectMapper();
        Assert.assertNotNull(mapper, "Jackson ObjectMapper should not be null");
        log.info("Jackson ObjectMapper instantiated successfully: {}", mapper.getClass().getName());
    }

    @Test(groups = "smoke", description = "Verify Apache POI is on the classpath")
    public void apachePoiClasspathCheck() {
        log.info("Checking Apache POI classpath availability");
        // WorkbookFactory is the top-level POI entry point for both .xls and .xlsx
        String poiClass = WorkbookFactory.class.getName();
        Assert.assertNotNull(poiClass, "Apache POI WorkbookFactory class name should not be null");
        log.info("Apache POI WorkbookFactory found: {}", poiClass);
    }

    @Test(groups = "smoke", description = "Verify SLF4J/Logback logger is operational")
    public void loggingCheck() {
        Logger testLogger = LoggerFactory.getLogger("SmokeTest.logging");
        testLogger.debug("DEBUG level message");
        testLogger.info("INFO  level message");
        testLogger.warn("WARN  level message");
        Assert.assertNotNull(testLogger, "SLF4J Logger should not be null");
        log.info("SLF4J/Logback logging check PASSED");
    }
}
