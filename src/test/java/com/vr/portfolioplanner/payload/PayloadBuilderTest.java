package com.vr.portfolioplanner.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vr.portfolioplanner.excel.ExcelFixtureBootstrap;
import com.vr.portfolioplanner.excel.ExcelReader;
import com.vr.portfolioplanner.model.TestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Unit-level tests for {@link Api1JsonPayloadBuilder} and
 * {@link Api2FormDataPayloadBuilder}.
 *
 * <p>Test data comes from the Excel fixture — no values are hardcoded here.
 * Assertions use the same TestData that the builders consume, confirming that
 * the correct columns are mapped to the correct API field names.
 */
public class PayloadBuilderTest {

    private static final Logger log = LoggerFactory.getLogger(PayloadBuilderTest.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Loaded at class-load time to avoid @BeforeClass group-filter issues
    private static final TestData SAMPLE;

    static {
        try {
            ExcelFixtureBootstrap.generateIfAbsent();
            ExcelReader reader = ExcelReader.fromFile(ExcelFixtureBootstrap.getFixturePath());
            List<TestData> rows = reader.getExecutableRows();
            if (rows.isEmpty()) {
                throw new IllegalStateException(
                    "No Execute=Y rows found in the Excel fixture");
            }
            SAMPLE = rows.get(0);
            log.info("PayloadBuilderTest loaded sample row: {}", SAMPLE.getTestCaseId());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test fixture for PayloadBuilderTest", e);
        }
    }

    // =========================================================================
    // API 1 – JSON payload
    // =========================================================================

    @Test(groups = "payload", description = "API-1 builder produces valid JSON")
    public void api1JsonIsValidJson() throws Exception {
        String json = Api1JsonPayloadBuilder.build(SAMPLE);
        Assert.assertNotNull(json);
        Assert.assertFalse(json.isBlank(), "JSON must not be blank");
        JsonNode root = MAPPER.readTree(json);
        Assert.assertTrue(root.isObject(), "JSON root must be an object");
        log.info("API-1 JSON ({} bytes): {}", json.length(), json);
    }

    @Test(groups = "payload", description = "API-1 JSON contains every required field")
    public void api1JsonContainsAllRequiredFields() throws Exception {
        JsonNode root = MAPPER.readTree(Api1JsonPayloadBuilder.build(SAMPLE));
        String[] expected = {
            "goal_type", "txn_options", "duration_type", "investment_duration",
            "monthly_amount", "lumpsum_amount", "label_id", "user_id",
            "risk_profile_id", "annual_income_range", "aware_type", "investor_id"
        };
        for (String field : expected) {
            Assert.assertTrue(root.has(field),
                "API-1 JSON must contain field '" + field + "'");
        }
        Assert.assertEquals(root.size(), expected.length,
            "API-1 JSON must contain exactly " + expected.length + " fields");
        log.info("API-1 JSON field count OK: {}", root.size());
    }

    @Test(groups = "payload", description = "API-1 JSON common-field values match TestData")
    public void api1JsonCommonFieldsMatchTestData() throws Exception {
        JsonNode root = MAPPER.readTree(Api1JsonPayloadBuilder.build(SAMPLE));

        Assert.assertEquals(root.get("goal_type").asText(),
            SAMPLE.getGoalType(),         "goal_type mismatch");
        Assert.assertEquals(root.get("txn_options").asText(),
            SAMPLE.getTxnOptions(),        "txn_options mismatch");
        Assert.assertEquals(root.get("duration_type").asText(),
            SAMPLE.getDurationType(),      "duration_type mismatch");
        Assert.assertEquals(root.get("investment_duration").asInt(),
            SAMPLE.getInvestmentDuration(), "investment_duration mismatch");
        Assert.assertEquals(root.get("monthly_amount").asLong(),
            SAMPLE.getMonthlyAmount(),      "monthly_amount mismatch");
        Assert.assertEquals(root.get("lumpsum_amount").asLong(),
            SAMPLE.getLumpsumAmount(),      "lumpsum_amount mismatch");
        Assert.assertEquals(root.get("risk_profile_id").asInt(),
            SAMPLE.getRiskProfileId(),      "risk_profile_id mismatch");
        Assert.assertEquals(root.get("annual_income_range").asInt(),
            SAMPLE.getAnnualIncomeRange(),  "annual_income_range mismatch");

        log.info("API-1 common fields verified");
    }

    @Test(groups = "payload",
          description = "API-1 JSON uses api1_* Excel columns for label_id/user_id/aware_type/investor_id")
    public void api1JsonApi1SpecificFieldsMatchTestData() throws Exception {
        JsonNode root = MAPPER.readTree(Api1JsonPayloadBuilder.build(SAMPLE));

        Assert.assertEquals(root.get("label_id").asLong(),
            SAMPLE.getApi1LabelId(),    "label_id must come from api1_label_id");
        Assert.assertEquals(root.get("user_id").asLong(),
            SAMPLE.getApi1UserId(),     "user_id must come from api1_user_id");
        Assert.assertEquals(root.get("aware_type").asText(),
            SAMPLE.getApi1AwareType(),  "aware_type must come from api1_aware_type");
        Assert.assertEquals(root.get("investor_id").asLong(),
            SAMPLE.getApi1InvestorId(), "investor_id must come from api1_investor_id");

        log.info("API-1 specific fields: label={} user={} aware={} investor={}",
            root.get("label_id").asLong(), root.get("user_id").asLong(),
            root.get("aware_type").asText(), root.get("investor_id").asLong());
    }

    @Test(groups = "payload",
          description = "API-1 JSON does NOT use api2_* IDs for label_id/user_id/investor_id")
    public void api1JsonDoesNotUseApi2Ids() throws Exception {
        JsonNode root = MAPPER.readTree(Api1JsonPayloadBuilder.build(SAMPLE));

        Assert.assertNotEquals(root.get("label_id").asLong(), SAMPLE.getApi2LabelId(),
            "API-1 label_id must NOT equal api2_label_id");
        Assert.assertNotEquals(root.get("user_id").asLong(), SAMPLE.getApi2UserId(),
            "API-1 user_id must NOT equal api2_user_id");
        Assert.assertNotEquals(root.get("investor_id").asLong(), SAMPLE.getApi2InvestorId(),
            "API-1 investor_id must NOT equal api2_investor_id");

        log.info("API-1 ID isolation confirmed");
    }

    @Test(groups = "payload",
          description = "API-1 JSON numeric values are integers, not floating-point")
    public void api1JsonNumericValuesAreIntegers() throws Exception {
        String json = Api1JsonPayloadBuilder.build(SAMPLE);
        // Floating-point values like 50000.0 must NOT appear in the JSON
        Assert.assertFalse(json.contains(".0"),
            "JSON must not contain floating-point notation: " + json);
        log.info("API-1 no floating-point in JSON — OK");
    }

    // =========================================================================
    // API 2 – multipart form-data payload
    // =========================================================================

    @Test(groups = "payload", description = "API-2 builder produces a non-empty field map")
    public void api2FormDataIsNotEmpty() {
        Map<String, String> fields = Api2FormDataPayloadBuilder.build(SAMPLE);
        Assert.assertNotNull(fields);
        Assert.assertFalse(fields.isEmpty(), "API-2 form fields must not be empty");
        log.info("API-2 form fields ({}): {}", fields.size(), fields.keySet());
    }

    @Test(groups = "payload", description = "API-2 form-data contains every required field")
    public void api2FormDataContainsAllRequiredFields() {
        Map<String, String> fields = Api2FormDataPayloadBuilder.build(SAMPLE);
        String[] expected = {
            "annual_income_range", "duration_type", "goal_type", "investment_duration",
            "investor_id", "label_id", "monthly_amount", "next_financial_year",
            "risk_profile_id", "txn_options", "user_id", "lumpsum_amount"
        };
        for (String field : expected) {
            Assert.assertTrue(fields.containsKey(field),
                "API-2 form data must contain field '" + field + "'");
            Assert.assertFalse(fields.get(field).isBlank(),
                "API-2 field '" + field + "' must not be blank");
        }
        Assert.assertEquals(fields.size(), expected.length,
            "API-2 form data must contain exactly " + expected.length + " fields");
        log.info("API-2 form-data field count OK: {}", fields.size());
    }

    @Test(groups = "payload", description = "API-2 form-data common-field values match TestData")
    public void api2FormDataCommonFieldsMatchTestData() {
        Map<String, String> fields = Api2FormDataPayloadBuilder.build(SAMPLE);

        Assert.assertEquals(fields.get("goal_type"),
            SAMPLE.getGoalType(),                        "goal_type mismatch");
        Assert.assertEquals(fields.get("txn_options"),
            SAMPLE.getTxnOptions(),                      "txn_options mismatch");
        Assert.assertEquals(fields.get("duration_type"),
            SAMPLE.getDurationType(),                    "duration_type mismatch");
        Assert.assertEquals(fields.get("investment_duration"),
            String.valueOf(SAMPLE.getInvestmentDuration()), "investment_duration mismatch");
        Assert.assertEquals(fields.get("monthly_amount"),
            String.valueOf(SAMPLE.getMonthlyAmount()),   "monthly_amount mismatch");
        Assert.assertEquals(fields.get("lumpsum_amount"),
            String.valueOf(SAMPLE.getLumpsumAmount()),   "lumpsum_amount mismatch");
        Assert.assertEquals(fields.get("risk_profile_id"),
            String.valueOf(SAMPLE.getRiskProfileId()),   "risk_profile_id mismatch");
        Assert.assertEquals(fields.get("annual_income_range"),
            String.valueOf(SAMPLE.getAnnualIncomeRange()), "annual_income_range mismatch");

        log.info("API-2 common fields verified");
    }

    @Test(groups = "payload",
          description = "API-2 form-data uses api2_* Excel columns for label_id/user_id/investor_id/next_financial_year")
    public void api2FormDataApi2SpecificFieldsMatchTestData() {
        Map<String, String> fields = Api2FormDataPayloadBuilder.build(SAMPLE);

        Assert.assertEquals(fields.get("label_id"),
            String.valueOf(SAMPLE.getApi2LabelId()),           "label_id must come from api2_label_id");
        Assert.assertEquals(fields.get("user_id"),
            String.valueOf(SAMPLE.getApi2UserId()),            "user_id must come from api2_user_id");
        Assert.assertEquals(fields.get("investor_id"),
            String.valueOf(SAMPLE.getApi2InvestorId()),        "investor_id must come from api2_investor_id");
        Assert.assertEquals(fields.get("next_financial_year"),
            String.valueOf(SAMPLE.getApi2NextFinancialYear()), "next_financial_year must come from api2_next_financial_year");

        log.info("API-2 specific fields: label={} user={} investor={} year={}",
            fields.get("label_id"), fields.get("user_id"),
            fields.get("investor_id"), fields.get("next_financial_year"));
    }

    @Test(groups = "payload",
          description = "API-2 form-data does NOT use api1_* IDs for label_id/user_id/investor_id")
    public void api2FormDataDoesNotUseApi1Ids() {
        Map<String, String> fields = Api2FormDataPayloadBuilder.build(SAMPLE);

        Assert.assertNotEquals(fields.get("label_id"),
            String.valueOf(SAMPLE.getApi1LabelId()),    "API-2 label_id must NOT equal api1_label_id");
        Assert.assertNotEquals(fields.get("user_id"),
            String.valueOf(SAMPLE.getApi1UserId()),     "API-2 user_id must NOT equal api1_user_id");
        Assert.assertNotEquals(fields.get("investor_id"),
            String.valueOf(SAMPLE.getApi1InvestorId()), "API-2 investor_id must NOT equal api1_investor_id");

        log.info("API-2 ID isolation confirmed");
    }

    @Test(groups = "payload",
          description = "API-2 form-data numeric values are integer strings, not floating-point")
    public void api2FormDataNumericValuesAreIntegerStrings() {
        Map<String, String> fields = Api2FormDataPayloadBuilder.build(SAMPLE);
        String[] numericFields = {
            "annual_income_range", "investment_duration", "monthly_amount",
            "lumpsum_amount", "risk_profile_id", "investor_id",
            "label_id", "user_id", "next_financial_year"
        };
        // Amount fields may be 0 for SIP-only or LUMPSUM-only scenarios;
        // the key contract is that they are non-floating-point integer strings.
        java.util.Set<String> allowZero = java.util.Set.of("monthly_amount","lumpsum_amount");
        for (String field : numericFields) {
            String value = fields.get(field);
            Assert.assertFalse(value.contains("."),
                "Numeric field '" + field + "' must not be floating-point; got: " + value);
            long parsed = Long.parseLong(value);
            if (!allowZero.contains(field)) {
                Assert.assertTrue(parsed > 0,
                    "Numeric field '" + field + "' must be > 0; got: " + value);
            } else {
                Assert.assertTrue(parsed >= 0,
                    "Numeric field '" + field + "' must be >= 0; got: " + value);
            }
        }
        log.info("API-2 no floating-point in form values — OK");
    }

    @Test(groups = "payload", description = "API-2 form-data map is unmodifiable")
    public void api2FormDataIsImmutable() {
        Map<String, String> fields = Api2FormDataPayloadBuilder.build(SAMPLE);
        Assert.assertThrows(UnsupportedOperationException.class,
            () -> fields.put("injected", "value"));
        log.info("API-2 form-data immutability confirmed");
    }

    // =========================================================================
    // Cross-API correctness — same row, correct partitioning
    // =========================================================================

    @Test(groups = "payload",
          description = "Common fields are identical in both API payloads (same TestData row)")
    public void commonFieldsAreIdenticalAcrossApis() throws Exception {
        JsonNode api1 = MAPPER.readTree(Api1JsonPayloadBuilder.build(SAMPLE));
        Map<String, String> api2 = Api2FormDataPayloadBuilder.build(SAMPLE);

        Assert.assertEquals(api1.get("goal_type").asText(),
            api2.get("goal_type"),              "goal_type must be identical");
        Assert.assertEquals(api1.get("txn_options").asText(),
            api2.get("txn_options"),            "txn_options must be identical");
        Assert.assertEquals(api1.get("duration_type").asText(),
            api2.get("duration_type"),          "duration_type must be identical");
        Assert.assertEquals(api1.get("investment_duration").asInt(),
            Integer.parseInt(api2.get("investment_duration")), "investment_duration must be identical");
        Assert.assertEquals(api1.get("monthly_amount").asLong(),
            Long.parseLong(api2.get("monthly_amount")),        "monthly_amount must be identical");
        Assert.assertEquals(api1.get("lumpsum_amount").asLong(),
            Long.parseLong(api2.get("lumpsum_amount")),        "lumpsum_amount must be identical");
        Assert.assertEquals(api1.get("risk_profile_id").asInt(),
            Integer.parseInt(api2.get("risk_profile_id")),     "risk_profile_id must be identical");
        Assert.assertEquals(api1.get("annual_income_range").asInt(),
            Integer.parseInt(api2.get("annual_income_range")), "annual_income_range must be identical");

        log.info("All common fields identical across API-1 and API-2 payloads");
    }

    @Test(groups = "payload",
          description = "API-1 and API-2 use different label_id, user_id, investor_id from their respective Excel columns")
    public void apiSpecificIdsDifferBetweenApis() throws Exception {
        JsonNode api1 = MAPPER.readTree(Api1JsonPayloadBuilder.build(SAMPLE));
        Map<String, String> api2 = Api2FormDataPayloadBuilder.build(SAMPLE);

        Assert.assertNotEquals(api1.get("label_id").asLong(),
            Long.parseLong(api2.get("label_id")),
            "label_id must differ: API-1 uses api1_label_id, API-2 uses api2_label_id");

        Assert.assertNotEquals(api1.get("user_id").asLong(),
            Long.parseLong(api2.get("user_id")),
            "user_id must differ: API-1 uses api1_user_id, API-2 uses api2_user_id");

        Assert.assertNotEquals(api1.get("investor_id").asLong(),
            Long.parseLong(api2.get("investor_id")),
            "investor_id must differ: API-1 uses api1_investor_id, API-2 uses api2_investor_id");

        log.info("API-specific ID separation verified:");
        log.info("  label_id   → API-1:{} API-2:{}",
            api1.get("label_id").asLong(), api2.get("label_id"));
        log.info("  user_id    → API-1:{} API-2:{}",
            api1.get("user_id").asLong(), api2.get("user_id"));
        log.info("  investor_id→ API-1:{} API-2:{}",
            api1.get("investor_id").asLong(), api2.get("investor_id"));
    }

    @Test(groups = "payload",
          description = "API-2 has next_financial_year; API-1 does not")
    public void api2HasNextFinancialYearApi1DoesNot() throws Exception {
        JsonNode api1 = MAPPER.readTree(Api1JsonPayloadBuilder.build(SAMPLE));
        Map<String, String> api2 = Api2FormDataPayloadBuilder.build(SAMPLE);

        Assert.assertFalse(api1.has("next_financial_year"),
            "API-1 JSON must NOT contain next_financial_year");
        Assert.assertTrue(api2.containsKey("next_financial_year"),
            "API-2 form data must contain next_financial_year");

        log.info("next_financial_year only in API-2: {}", api2.get("next_financial_year"));
    }

    @Test(groups = "payload",
          description = "API-1 has aware_type; API-2 does not")
    public void api1HasAwareTypeApi2DoesNot() throws Exception {
        JsonNode api1 = MAPPER.readTree(Api1JsonPayloadBuilder.build(SAMPLE));
        Map<String, String> api2 = Api2FormDataPayloadBuilder.build(SAMPLE);

        Assert.assertTrue(api1.has("aware_type"),
            "API-1 JSON must contain aware_type");
        Assert.assertFalse(api2.containsKey("aware_type"),
            "API-2 form data must NOT contain aware_type");

        log.info("aware_type only in API-1: {}", api1.get("aware_type").asText());
    }

    // =========================================================================
    // Validation guard tests
    // =========================================================================

    @Test(groups = "payload",
          expectedExceptions = IllegalArgumentException.class,
          description = "API-1 builder rejects null TestData")
    public void api1BuilderRejectsNull() {
        Api1JsonPayloadBuilder.build(null);
    }

    @Test(groups = "payload",
          expectedExceptions = IllegalArgumentException.class,
          description = "API-2 builder rejects null TestData")
    public void api2BuilderRejectsNull() {
        Api2FormDataPayloadBuilder.build(null);
    }

    @Test(groups = "payload",
          expectedExceptions = IllegalArgumentException.class,
          description = "API-1 builder rejects TestData with blank goal_type")
    public void api1BuilderRejectsBlankGoalType() {
        TestData bad = cloneSample();
        bad.setGoalType("");
        Api1JsonPayloadBuilder.build(bad);
    }

    @Test(groups = "payload",
          expectedExceptions = IllegalArgumentException.class,
          description = "API-1 builder rejects TestData with zero label_id")
    public void api1BuilderRejectsZeroLabelId() {
        TestData bad = cloneSample();
        bad.setApi1LabelId(0L);
        Api1JsonPayloadBuilder.build(bad);
    }

    @Test(groups = "payload",
          expectedExceptions = IllegalArgumentException.class,
          description = "API-2 builder rejects TestData with zero api2_investor_id")
    public void api2BuilderRejectsZeroInvestorId() {
        TestData bad = cloneSample();
        bad.setApi2InvestorId(0L);
        Api2FormDataPayloadBuilder.build(bad);
    }

    // -------------------------------------------------------------------------
    // Helper: clone the static sample so individual tests can mutate fields
    // -------------------------------------------------------------------------

    private static TestData cloneSample() {
        TestData td = new TestData();
        td.setTestCaseId(SAMPLE.getTestCaseId());
        td.setExecute(SAMPLE.getExecute());
        td.setDescription(SAMPLE.getDescription());
        td.setGoalType(SAMPLE.getGoalType());
        td.setTxnOptions(SAMPLE.getTxnOptions());
        td.setDurationType(SAMPLE.getDurationType());
        td.setInvestmentDuration(SAMPLE.getInvestmentDuration());
        td.setMonthlyAmount(SAMPLE.getMonthlyAmount());
        td.setLumpsumAmount(SAMPLE.getLumpsumAmount());
        td.setRiskProfileId(SAMPLE.getRiskProfileId());
        td.setAnnualIncomeRange(SAMPLE.getAnnualIncomeRange());
        td.setApi1LabelId(SAMPLE.getApi1LabelId());
        td.setApi1UserId(SAMPLE.getApi1UserId());
        td.setApi1AwareType(SAMPLE.getApi1AwareType());
        td.setApi1InvestorId(SAMPLE.getApi1InvestorId());
        td.setApi2LabelId(SAMPLE.getApi2LabelId());
        td.setApi2UserId(SAMPLE.getApi2UserId());
        td.setApi2InvestorId(SAMPLE.getApi2InvestorId());
        td.setApi2NextFinancialYear(SAMPLE.getApi2NextFinancialYear());
        return td;
    }
}
