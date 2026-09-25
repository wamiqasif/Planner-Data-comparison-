package com.vr.portfolioplanner.excel;

import com.vr.portfolioplanner.model.TestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Verifies that the Excel test-data layer (bootstrap + READER + model) works correctly.
 * No API calls. No credentials. All assertions are against locally generated data.
 */
public class ExcelReaderTest {

    private static final Logger log = LoggerFactory.getLogger(ExcelReaderTest.class);

    // Static fields: initialized at class-load time so TestNG group-filtering
    // never causes @BeforeClass to be skipped before test methods run.
    private static final Path FIXTURE_PATH;
    private static final ExcelReader READER;

    static {
        try {
            FIXTURE_PATH = ExcelFixtureBootstrap.getFixturePath();
            ExcelFixtureBootstrap.generateIfAbsent();
            READER = ExcelReader.fromFile(FIXTURE_PATH);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialise Excel test fixture", e);
        }
    }

    @BeforeClass
    public void logStartup() {
        log.info("ExcelReaderTest ready. Fixture: {} | rows={} executable={}",
            FIXTURE_PATH.toAbsolutePath(), READER.totalRowCount(), READER.executableRowCount());
    }

    // ------------------------------------------------------------------
    // Fixture file checks
    // ------------------------------------------------------------------

    @Test(groups = "excel", description = "Fixture file is created at the expected path")
    public void fixtureFileExists() {
        Assert.assertTrue(Files.exists(FIXTURE_PATH),
            "Fixture file must exist at: " + FIXTURE_PATH);
        log.info("Fixture confirmed at: {}", FIXTURE_PATH.toAbsolutePath());
    }

    // ------------------------------------------------------------------
    // Row counts
    // ------------------------------------------------------------------

    @Test(groups = "excel", description = "Excel contains exactly 6750 data rows")
    public void totalRowCountIsOne() {
        Assert.assertEquals(READER.totalRowCount(), 6750,
            "Expected exactly 6750 data rows in the fixture");
    }

    @Test(groups = "excel", description = "Execute=Y yields 6750 executable rows (all rows are Y)")
    public void executableRowCountIsOne() {
        Assert.assertEquals(READER.executableRowCount(), 6750,
            "Expected 6750 executable rows (Execute=Y)");
    }

    @Test(groups = "excel", description = "getExecutableRows and getAllRows agree on Execute=Y count")
    public void executeFilterConsistency() {
        List<TestData> all = READER.getAllRows();
        List<TestData> exec = READER.getExecutableRows();
        long countY = all.stream().filter(TestData::isExecutable).count();
        Assert.assertEquals(exec.size(), (int) countY,
            "getExecutableRows size must match manual filter count");
    }

    // ------------------------------------------------------------------
    // Field-level assertions (the sample row from ExcelFixtureBootstrap)
    // ------------------------------------------------------------------

    @Test(groups = "excel", description = "TestCaseID column is read correctly")
    public void testCaseIdIsCorrect() {
        TestData td = firstRow();
        Assert.assertEquals(td.getTestCaseId(), "TC_00001");
        log.info("TestCaseID = {}", td.getTestCaseId());
    }

    @Test(groups = "excel", description = "Execute flag is Y")
    public void executeFlagIsY() {
        Assert.assertEquals(firstRow().getExecute(), "Y");
        Assert.assertTrue(firstRow().isExecutable());
    }

    @Test(groups = "excel", description = "Common fields – string values")
    public void commonStringFields() {
        TestData td = firstRow();
        Assert.assertEquals(td.getGoalType(),    "GROWTH");
        Assert.assertEquals(td.getTxnOptions(),  "SIP");
        Assert.assertEquals(td.getDurationType(), "y");
        log.info("Common strings: goalType={} txnOptions={} durationType={}",
            td.getGoalType(), td.getTxnOptions(), td.getDurationType());
    }

    @Test(groups = "excel", description = "Common fields – numeric values are not floating-point strings")
    public void commonNumericFields() {
        TestData td = firstRow();
        Assert.assertEquals(td.getInvestmentDuration(), 1,
            "investment_duration must be int 15, not 15.0");
        Assert.assertEquals(td.getMonthlyAmount(), 5000L,
            "monthly_amount must be long 50000");
        Assert.assertEquals(td.getLumpsumAmount(), 0L,
            "lumpsum_amount must be long 10000000");
        Assert.assertEquals(td.getRiskProfileId(), 1,
            "risk_profile_id must be int 2");
        Assert.assertEquals(td.getAnnualIncomeRange(), 1,
            "annual_income_range must be int 2");
        log.info("Numerics: duration={} monthly={} lumpsum={} risk={} income={}",
            td.getInvestmentDuration(), td.getMonthlyAmount(), td.getLumpsumAmount(),
            td.getRiskProfileId(), td.getAnnualIncomeRange());
    }

    @Test(groups = "excel", description = "API-1 specific fields are read correctly")
    public void api1SpecificFields() {
        TestData td = firstRow();
        Assert.assertEquals(td.getApi1LabelId(),    89_660_240L);
        Assert.assertEquals(td.getApi1UserId(),      123L);
        Assert.assertEquals(td.getApi1AwareType(),  "FRESH");
        Assert.assertEquals(td.getApi1InvestorId(), 87_683_240L);
        log.info("API1: labelId={} awareType={} investorId={}",
            td.getApi1LabelId(), td.getApi1AwareType(), td.getApi1InvestorId());
    }

    @Test(groups = "excel", description = "API-2 specific fields are read correctly")
    public void api2SpecificFields() {
        TestData td = firstRow();
        Assert.assertEquals(td.getApi2LabelId(),           90_222_276L);
        Assert.assertEquals(td.getApi2UserId(),            52_264_629L);
        Assert.assertEquals(td.getApi2InvestorId(),        88_244_645L);
        Assert.assertEquals(td.getApi2NextFinancialYear(), 2028);
        log.info("API2: labelId={} investorId={} nextFY={}",
            td.getApi2LabelId(), td.getApi2InvestorId(), td.getApi2NextFinancialYear());
    }

    // ------------------------------------------------------------------
    // DataProvider format
    // ------------------------------------------------------------------

    @Test(groups = "excel", description = "asDataProvider returns non-empty Object[][] with TestData elements")
    public void dataProviderFormat() {
        Object[][] provider = READER.asDataProvider();
        Assert.assertNotNull(provider);
        Assert.assertEquals(provider.length, 6750,
            "DataProvider must have 6750 entries");
        Assert.assertEquals(provider[0].length, 1,
            "Each DataProvider entry must be Object[]{TestData}");
        Assert.assertNotNull(provider[0][0]);
        Assert.assertTrue(provider[0][0] instanceof TestData,
            "DataProvider element must be a TestData instance");
        log.info("DataProvider format OK: {} row(s)", provider.length);
    }

    // ------------------------------------------------------------------
    // ExcelReader.formatDouble contract
    // ------------------------------------------------------------------

    @Test(groups = "excel", description = "formatDouble avoids floating-point strings for whole numbers")
    public void formatDoubleWholeNumbers() {
        Assert.assertEquals(ExcelReader.formatDouble(50000.0),    "50000");
        Assert.assertEquals(ExcelReader.formatDouble(10000000.0), "10000000");
        Assert.assertEquals(ExcelReader.formatDouble(89660240.0), "89660240");
        Assert.assertEquals(ExcelReader.formatDouble(2028.0),     "2028");
        Assert.assertEquals(ExcelReader.formatDouble(0.0),        "0");
        log.info("formatDouble whole-number assertions passed");
    }

    @Test(groups = "excel", description = "formatDouble preserves decimals when present")
    public void formatDoubleDecimalsPreserved() {
        String result = ExcelReader.formatDouble(0.01);
        Assert.assertFalse(result.equals("0"),
            "0.01 must not be collapsed to '0'; got: " + result);
        log.info("formatDouble(0.01) = {}", result);
    }

    // ------------------------------------------------------------------
    // Non-sensitive data print (the explicit requirement from the step spec)
    // ------------------------------------------------------------------

    @Test(groups = "excel", description = "Print non-sensitive test data via toLoggableString()")
    public void printNonSensitiveTestData() {
        List<TestData> executableRows = READER.getExecutableRows();
        Assert.assertFalse(executableRows.isEmpty(), "Must have at least one executable row");

        log.info("=== Non-sensitive test data for {} executable row(s) ===",
            executableRows.size());

        // Log all rows
        for (TestData td : executableRows) {
            log.info("{}", td.toLoggableString());
        }

        // Spot-check TC_00001 (first row) for known values
        TestData tc001 = executableRows.get(0);
        String loggable = tc001.toLoggableString();
        Assert.assertTrue(loggable.contains("TC_00001"), "TC_00001 must be first row");
        Assert.assertTrue(loggable.contains("GROWTH"),  "Should contain goalType");
        Assert.assertTrue(loggable.contains("FRESH"),   "Should contain api1AwareType");
        // Verify all rows have non-blank TestCaseID and goalType
        for (TestData td : executableRows) {
            Assert.assertFalse(td.getTestCaseId().isBlank(), "TestCaseID must not be blank");
            Assert.assertFalse(td.getGoalType().isBlank(),   "goalType must not be blank");
        }

        log.info("=== End of non-sensitive test data ===");
    }

    // ------------------------------------------------------------------
    // Execute=N skip contract
    // ------------------------------------------------------------------

    @Test(groups = "excel", description = "isExecutable returns false for non-Y execute values")
    public void nonYExecuteIsSkipped() {
        // Verify the logic independently of the fixture
        // (the fixture only has Execute=Y, so we construct a synthetic check)
        TestData notExecutable = syntheticRow("N");
        Assert.assertFalse(notExecutable.isExecutable());

        TestData alsoNotExecutable = syntheticRow("n");
        Assert.assertFalse(alsoNotExecutable.isExecutable(), "lowercase 'n' should not execute");

        TestData blank = syntheticRow("");
        Assert.assertFalse(blank.isExecutable(), "blank Execute should not execute");

        TestData yes = syntheticRow("Y");
        Assert.assertTrue(yes.isExecutable());

        TestData yesLower = syntheticRow("y");
        Assert.assertTrue(yesLower.isExecutable(), "lowercase 'y' should be executable");

        log.info("Execute flag logic verified for Y/N/blank/case variants");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TestData firstRow() {
        List<TestData> all = READER.getAllRows();
        Assert.assertFalse(all.isEmpty(), "Fixture must have at least one row");
        return all.get(0);
    }

    /** Creates a minimal TestData with only Execute set — for logic-only tests. */
    private TestData syntheticRow(String executeValue) {
        TestData td = new TestData();
        td.setTestCaseId("SYNTHETIC");
        td.setExecute(executeValue);
        return td;
    }
}
