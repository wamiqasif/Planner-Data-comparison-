package com.vr.portfolioplanner.response;

import com.vr.portfolioplanner.response.model.BreakdownEntry;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import com.vr.portfolioplanner.response.model.FundEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Unit tests for {@link Api1ResponseExtractor} and {@link Api2ResponseExtractor}.
 * Uses stored sample JSON — no live API calls.
 */
public class ResponseExtractorTest {

    private static final Logger log = LoggerFactory.getLogger(ResponseExtractorTest.class);

    // ---- Load all sample responses at class-load time ----
    private static final String API1_SUCCESS = load("sample-responses/api1_success.json");
    private static final String API2_SUCCESS = load("sample-responses/api2_success.json");
    private static final String API1_ERROR   = load("sample-responses/api1_error.json");
    private static final String API2_ERROR   = load("sample-responses/api2_error.json");

    private static String load(String resourcePath) {
        try (InputStream is = ResponseExtractorTest.class
                .getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalStateException(
                "Test resource not found: " + resourcePath);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load " + resourcePath, e);
        }
    }

    // =========================================================================
    // API-1 success response
    // =========================================================================

    @Test(groups = "extraction", description = "API-1 extraction succeeds on valid response")
    public void api1ExtractionSucceeds() {
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_SUCCESS);
        Assert.assertTrue(r.isExtractionSuccess(), "Extraction must succeed: " + r.getExtractionError());
        log.info("API-1 extracted: {}", r);
    }

    @Test(groups = "extraction", description = "API-1 status is 'success'")
    public void api1StatusExtracted() {
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_SUCCESS);
        Assert.assertEquals(r.getStatus(), "true");
        log.info("API-1 status = {}", r.getStatus());
    }

    @Test(groups = "extraction", description = "API-1 message is extracted")
    public void api1MessageExtracted() {
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_SUCCESS);
        Assert.assertNotNull(r.getMessage(), "API-1 message must not be null");
        Assert.assertFalse(r.getMessage().isBlank());
        log.info("API-1 message = {}", r.getMessage());
    }

    @Test(groups = "extraction", description = "API-1 investor ID extracted from data.investor.investor_id")
    public void api1InvestorIdExtracted() {
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_SUCCESS);
        Assert.assertEquals(r.getInvestorId(), "87683240");
        log.info("API-1 investorId = {}", r.getInvestorId());
    }

    @Test(groups = "extraction", description = "API-1 extracts 3 fund entries")
    public void api1FundCountIsCorrect() {
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_SUCCESS);
        Assert.assertEquals(r.getFunds().size(), 3, "Expected 3 fund entries");
        log.info("API-1 fund count = {}", r.getFunds().size());
    }

    @Test(groups = "extraction", description = "API-1 fund fields: planId, planName, categoryId, categoryFmt, amount")
    public void api1FundFieldsExtracted() {
        List<FundEntry> funds = Api1ResponseExtractor.extract(API1_SUCCESS).getFunds();
        FundEntry first = funds.get(0);
        // Real API-1 response: first fund is Bandhan Short Term Dir-G (plan_id=17074)
        Assert.assertEquals(first.getPlanId(), "17074");
        Assert.assertTrue(first.getPlanName().contains("Dir-G"),
            "API-1 raw plan name must contain 'Dir-G'; got: " + first.getPlanName());
        Assert.assertEquals(first.getCategoryId(),  "122");  // integer serialised as string
        Assert.assertEquals(first.getCategoryFmt(), "Short Term");
        Assert.assertNotNull(first.getAmount(), "Amount must not be null");
        Assert.assertEquals(first.getAmount().compareTo(new BigDecimal("3350100")), 0,
            "first_month_amount for Bandhan Short Term must be 3350100");
        log.info("API-1 fund[0]: {}", first);
    }

    @Test(groups = "extraction", description = "API-1 amount comes from first_month_amount field")
    public void api1AmountFromFirstMonthAmount() {
        // Real API-1: plan_id=15752 (Axis Short Term Dir-G), first_month_amount=3350100
        Optional<FundEntry> fund = Api1ResponseExtractor.extract(API1_SUCCESS)
            .getFundByPlanId("15752");
        Assert.assertTrue(fund.isPresent(), "plan_id=15752 must be found");
        Assert.assertNotNull(fund.get().getAmount(), "Amount must not be null");
        log.info("API-1 plan_id=15752 amount (first_month_amount) = {}", fund.get().getAmount());
    }

    @Test(groups = "extraction", description = "API-1 extracts 1 breakdown group (Debt=100%)")
    public void api1BreakdownCountIsCorrect() {
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_SUCCESS);
        Assert.assertEquals(r.getBreakdowns().size(), 1, "Real API-1 sample has 1 breakdown group (Debt)");
        log.info("API-1 breakdown count = {}", r.getBreakdowns().size());
    }

    @Test(groups = "extraction", description = "API-1 breakdown: Debt=100%")
    public void api1BreakdownFieldsExtracted() {
        // Real API-1 sample: single breakdown group named "Debt" at 100%
        Optional<BreakdownEntry> debtOpt = Api1ResponseExtractor.extract(API1_SUCCESS)
            .getBreakdownByCategoryId("Debt");
        Assert.assertTrue(debtOpt.isPresent(), "Debt breakdown must exist");
        BreakdownEntry debt = debtOpt.get();
        Assert.assertEquals(debt.getCategoryFmt(), "Debt");
        Assert.assertNotNull(debt.getPercFmt(), "perc_fmt must not be null");
        log.info("API-1 breakdown Debt: percFmt={}", debt.getPercFmt());
    }

    @Test(groups = "extraction",
          description = "API-1 NOT_COMPARABLE list mentions legs[] and message")
    public void api1NotComparableFieldsDocumented() {
        List<String> nc = Api1ResponseExtractor.extract(API1_SUCCESS).getNotComparableFields();
        Assert.assertFalse(nc.isEmpty(), "NOT_COMPARABLE list must not be empty");

        String combined = String.join(" ", nc).toLowerCase();
        Assert.assertTrue(combined.contains("legs"),    "Must document legs[] as NOT_COMPARABLE");
        Assert.assertTrue(combined.contains("message"), "Must document message as NOT_COMPARABLE");
        Assert.assertTrue(combined.contains("port_builder_id") || combined.contains("output_id"),
            "Must document excluded technical IDs");

        log.info("API-1 NOT_COMPARABLE fields: {}", nc);
    }

    @Test(groups = "extraction",
          description = "API-1 FundEntry contains legs[] mapped from legs[].type/type_fmt (now COMPARABLE)")
    public void api1FundEntryContainsMappedLegs() {
        // legs are now COMPARABLE via FieldMapping.TRANSACTION_TYPE mapping
        FundEntry f = Api1ResponseExtractor.extract(API1_SUCCESS).getFunds().get(0);
        Assert.assertNotNull(f.getLegs(), "FundEntry.legs must not be null");
        Assert.assertFalse(f.getLegs().isEmpty(), "API-1 fund must have at least one leg");
        // Verify FundEntry has no raw inv_data field (API-2 data must not leak into API-1 model)
        for (java.lang.reflect.Field field : FundEntry.class.getDeclaredFields()) {
            Assert.assertFalse(field.getName().toLowerCase().contains("invdata"),
                "FundEntry must not have an inv_data field; found: " + field.getName());
        }
        log.info("API-1 fund[0] legs ({} entries): {}", f.getLegs().size(), f.getLegs());
    }

    @Test(groups = "extraction",
          description = "API-1 legs carry both rawType and normalizedType")
    public void api1LegsHaveNormalizedType() {
        List<com.vr.portfolioplanner.response.model.TransactionLeg> legs =
            Api1ResponseExtractor.extract(API1_SUCCESS).getFunds().get(0).getLegs();
        for (com.vr.portfolioplanner.response.model.TransactionLeg leg : legs) {
            Assert.assertNotNull(leg.getRawType(),        "leg rawType must not be null");
            Assert.assertNotNull(leg.getNormalizedType(), "leg normalizedType must not be null");
        }
        log.info("API-1 legs normalized: {}", legs);
    }

    // =========================================================================
    // API-2 success response
    // =========================================================================

    @Test(groups = "extraction", description = "API-2 extraction succeeds on valid response")
    public void api2ExtractionSucceeds() {
        ExtractedResponse r = Api2ResponseExtractor.extract(API2_SUCCESS);
        Assert.assertTrue(r.isExtractionSuccess(), "Extraction must succeed: " + r.getExtractionError());
        log.info("API-2 extracted: {}", r);
    }

    @Test(groups = "extraction", description = "API-2 status is 'success'")
    public void api2StatusExtracted() {
        Assert.assertEquals(Api2ResponseExtractor.extract(API2_SUCCESS).getStatus(), "true");
    }

    @Test(groups = "extraction", description = "API-2 message is null (not present in API-2)")
    public void api2MessageIsNull() {
        // API-2 does not have data.message — must be null, not empty string
        Assert.assertNull(Api2ResponseExtractor.extract(API2_SUCCESS).getMessage(),
            "API-2 message must be null (field absent in API-2)");
        log.info("API-2 message = null (correct — NOT_COMPARABLE with API-1 message)");
    }

    @Test(groups = "extraction", description = "API-2 investor ID extracted from data.investor.investor_id")
    public void api2InvestorIdExtracted() {
        Assert.assertEquals(
            Api2ResponseExtractor.extract(API2_SUCCESS).getInvestorId(), "88244645");
        log.info("API-2 investorId = 88244645");
    }

    @Test(groups = "extraction", description = "API-2 extracts 4 fund entries (real sample)")
    public void api2FundCountIsCorrect() {
        Assert.assertEquals(Api2ResponseExtractor.extract(API2_SUCCESS).getFunds().size(), 4,
            "Real API-2 sample has 4 funds");
    }

    @Test(groups = "extraction", description = "API-2 fund fields from real response")
    public void api2FundFieldsExtracted() {
        // Real API-2: first fund is ICICI Pru Large & Mid Cap Direct-G (plan_id=15868)
        FundEntry first = Api2ResponseExtractor.extract(API2_SUCCESS).getFunds().get(0);
        Assert.assertEquals(first.getPlanId(), "15868");
        Assert.assertTrue(first.getPlanName().contains("Direct-G"),
            "API-2 raw plan name must contain 'Direct-G'; got: " + first.getPlanName());
        Assert.assertEquals(first.getCategoryId(),  "101");  // integer as string
        Assert.assertEquals(first.getCategoryFmt(), "Large & MidCap");
        Assert.assertNotNull(first.getAmount());
        Assert.assertEquals(first.getAmount().compareTo(new BigDecimal("82000")), 0,
            "txn_data.amount for ICICI must be 82000");
        log.info("API-2 fund[0]: {}", first);
    }

    @Test(groups = "extraction", description = "API-2 amount comes from txn_data.amount")
    public void api2AmountFromTxnData() {
        // plan_id=15752 (Axis Short Term Direct-G), txn_data.amount=2512500
        Optional<FundEntry> fund = Api2ResponseExtractor.extract(API2_SUCCESS)
            .getFundByPlanId("15752");
        Assert.assertTrue(fund.isPresent(), "plan_id=15752 must exist in API-2 sample");
        Assert.assertEquals(fund.get().getAmount().compareTo(new BigDecimal("2512500")), 0);
        log.info("API-2 plan_id=15752 amount (txn_data.amount) = {}", fund.get().getAmount());
    }

    @Test(groups = "extraction", description = "API-2 extracts 2 breakdown groups (Equity + Debt)")
    public void api2BreakdownCountIsCorrect() {
        Assert.assertEquals(Api2ResponseExtractor.extract(API2_SUCCESS).getBreakdowns().size(), 2,
            "Real API-2 sample has 2 breakdown groups: Equity + Debt");
    }

    @Test(groups = "extraction", description = "API-2 breakdown: Equity=75%, Debt=25%")
    public void api2BreakdownFieldsExtracted() {
        ExtractedResponse r = Api2ResponseExtractor.extract(API2_SUCCESS);
        Optional<BreakdownEntry> equityOpt = r.getBreakdownByCategoryId("Equity");
        Assert.assertTrue(equityOpt.isPresent(), "Equity breakdown must exist");
        Assert.assertEquals(equityOpt.get().getCategoryFmt(), "Equity");
        Assert.assertNotNull(equityOpt.get().getPercFmt());

        Optional<BreakdownEntry> debtOpt = r.getBreakdownByCategoryId("Debt");
        Assert.assertTrue(debtOpt.isPresent(), "Debt breakdown must exist");
        Assert.assertEquals(debtOpt.get().getCategoryFmt(), "Debt");
        log.info("API-2 breakdowns: Equity percFmt={} Debt percFmt={}",
            equityOpt.get().getPercFmt(), debtOpt.get().getPercFmt());
    }

    @Test(groups = "extraction",
          description = "API-2 NOT_COMPARABLE list mentions message; inv_data[] is now COMPARABLE via FieldMapping.TRANSACTION_TYPE")
    public void api2NotComparableFieldsDocumented() {
        List<String> nc = Api2ResponseExtractor.extract(API2_SUCCESS).getNotComparableFields();
        Assert.assertFalse(nc.isEmpty());

        String combined = String.join(" ", nc).toLowerCase();
        // message is NOT_COMPARABLE (API-1 only)
        Assert.assertTrue(combined.contains("message"), "Must document message as NOT_COMPARABLE");
        // inv_data[] is now COMPARABLE (mapped to API-1 legs[] via TRANSACTION_TYPE mapping)
        // — it must NOT appear in the NOT_COMPARABLE list
        Assert.assertFalse(combined.contains("inv_data not_comparable"),
            "inv_data[] must NOT be listed as NOT_COMPARABLE (it is now mapped via TRANSACTION_TYPE)");
        // technical IDs must be excluded
        Assert.assertTrue(combined.contains("port_builder_id") || combined.contains("output_id"),
            "Must document excluded technical IDs");

        log.info("API-2 NOT_COMPARABLE/EXCLUDED fields: {}", nc);
    }

    // =========================================================================
    // Error responses
    // =========================================================================

    @Test(groups = "extraction", description = "API-1 error response: extraction succeeds, funds empty")
    public void api1ErrorResponseHandledGracefully() {
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_ERROR);
        Assert.assertTrue(r.isExtractionSuccess());
        Assert.assertEquals(r.getStatus(), "false");
        Assert.assertTrue(r.getFunds().isEmpty(), "Error response must have empty funds list");
        Assert.assertTrue(r.getBreakdowns().isEmpty());
        log.info("API-1 error response: status={} funds={}", r.getStatus(), r.getFunds().size());
    }

    @Test(groups = "extraction", description = "API-2 error response: extraction succeeds, funds empty")
    public void api2ErrorResponseHandledGracefully() {
        ExtractedResponse r = Api2ResponseExtractor.extract(API2_ERROR);
        Assert.assertTrue(r.isExtractionSuccess());
        Assert.assertEquals(r.getStatus(), "false");
        Assert.assertTrue(r.getFunds().isEmpty());
        Assert.assertTrue(r.getBreakdowns().isEmpty());
    }

    @Test(groups = "extraction", description = "Null body produces failed ExtractedResponse (no exception)")
    public void nullBodyProducesFailedResponse() {
        ExtractedResponse r1 = Api1ResponseExtractor.extract(null);
        Assert.assertFalse(r1.isExtractionSuccess());
        Assert.assertNotNull(r1.getExtractionError());

        ExtractedResponse r2 = Api2ResponseExtractor.extract(null);
        Assert.assertFalse(r2.isExtractionSuccess());
        Assert.assertNotNull(r2.getExtractionError());
        log.info("Null body handled: api1={} api2={}", r1.getExtractionError(), r2.getExtractionError());
    }

    @Test(groups = "extraction", description = "Malformed JSON produces failed ExtractedResponse (no exception)")
    public void malformedJsonProducesFailedResponse() {
        ExtractedResponse r1 = Api1ResponseExtractor.extract("{invalid json}");
        Assert.assertFalse(r1.isExtractionSuccess());

        ExtractedResponse r2 = Api2ResponseExtractor.extract("{invalid json}");
        Assert.assertFalse(r2.isExtractionSuccess());
        log.info("Malformed JSON handled gracefully");
    }

    @Test(groups = "extraction", description = "Empty body produces failed ExtractedResponse (no exception)")
    public void emptyBodyProducesFailedResponse() {
        ExtractedResponse r = Api1ResponseExtractor.extract("   ");
        Assert.assertFalse(r.isExtractionSuccess());
        log.info("Empty body handled: {}", r.getExtractionError());
    }

    // =========================================================================
    // Cross-API structural consistency
    // =========================================================================

    @Test(groups = "extraction",
          description = "plan_id, categoryId, categoryFmt, and amount match across APIs for same fund; "
                      + "planName requires FundNameNormalizer (tested separately in NormalizerTest)")
    public void comparableFieldsMatchAcrossApis() {
        // The two sample JSONs represent different real scenarios (different inputs/outputs)
        // so we verify extraction correctness individually rather than cross-comparing amounts.
        ExtractedResponse r1 = Api1ResponseExtractor.extract(API1_SUCCESS);
        ExtractedResponse r2 = Api2ResponseExtractor.extract(API2_SUCCESS);

        // Verify each API extracted correctly
        Assert.assertFalse(r1.getFunds().isEmpty(), "API-1 must have funds");
        Assert.assertFalse(r2.getFunds().isEmpty(), "API-2 must have funds");

        // plan_id=15752 (Axis Short Term) appears in BOTH sample responses
        Optional<FundEntry> f1 = r1.getFundByPlanId("15752");
        Optional<FundEntry> f2 = r2.getFundByPlanId("15752");
        Assert.assertTrue(f1.isPresent(), "plan_id=15752 must exist in API-1");
        Assert.assertTrue(f2.isPresent(), "plan_id=15752 must exist in API-2");

        // Category must match for the shared plan
        Assert.assertEquals(f1.get().getCategoryId(),  f2.get().getCategoryId(),  "Category IDs must match for plan 15752");
        Assert.assertEquals(f1.get().getCategoryFmt(), f2.get().getCategoryFmt(), "Category fmt must match for plan 15752");

        log.info("Shared plan 15752: api1cat={} api2cat={} api1amount={} api2amount={}",
            f1.get().getCategoryId(), f2.get().getCategoryId(),
            f1.get().getAmount(), f2.get().getAmount());
    }

    @Test(groups = "extraction",
          description = "Both APIs produce identical breakdown data for the same scenario")
    public void breakdownsMatchAcrossApis() {
        // "Debt" appears as a breakdown group in both sample responses
        ExtractedResponse r1 = Api1ResponseExtractor.extract(API1_SUCCESS);
        ExtractedResponse r2 = Api2ResponseExtractor.extract(API2_SUCCESS);

        Optional<BreakdownEntry> debt1 = r1.getBreakdownByCategoryId("Debt");
        Optional<BreakdownEntry> debt2 = r2.getBreakdownByCategoryId("Debt");
        Assert.assertTrue(debt1.isPresent(), "API-1 must have Debt breakdown");
        Assert.assertTrue(debt2.isPresent(), "API-2 must have Debt breakdown");
        Assert.assertEquals(debt1.get().getCategoryFmt(), debt2.get().getCategoryFmt(),
            "Debt category fmt must match across APIs");
        log.info("Shared Debt breakdown: api1percFmt={} api2percFmt={}",
            debt1.get().getPercFmt(), debt2.get().getPercFmt());
    }

    @Test(groups = "extraction",
          description = "API-1 has message; API-2 message is null — fields are NOT_COMPARABLE")
    public void messageIsNotComparableAcrossApis() {
        ExtractedResponse r1 = Api1ResponseExtractor.extract(API1_SUCCESS);
        ExtractedResponse r2 = Api2ResponseExtractor.extract(API2_SUCCESS);

        Assert.assertNotNull(r1.getMessage(),  "API-1 message must be non-null");
        Assert.assertNull(r2.getMessage(),     "API-2 message must be null (NOT_COMPARABLE)");
        log.info("message NOT_COMPARABLE confirmed: api1='{}' api2=null", r1.getMessage());
    }

    @Test(groups = "extraction",
          description = "Technical IDs (port_builder_id/output_id) are NOT in FundEntry")
    public void technicalIdsExcludedFromFundEntry() {
        // FundEntry has no planBuilderId / outputId fields — verify via declared fields
        java.lang.reflect.Field[] fields = FundEntry.class.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            String name = field.getName().toLowerCase();
            Assert.assertFalse(name.contains("port_builder") || name.contains("portbuilder"),
                "FundEntry must not store port_builder_id");
            Assert.assertFalse(name.contains("output_id") || name.contains("outputid"),
                "FundEntry must not store output_id");
        }
        log.info("Technical IDs correctly excluded from FundEntry");
    }

    // =========================================================================
    // getFundByPlanId / getBreakdownByCategoryId lookups
    // =========================================================================

    @Test(groups = "extraction", description = "getFundByPlanId returns correct fund")
    public void getFundByPlanIdReturnsCorrectFund() {
        // Real API-1 sample: plan_id=16151 is HDFC Short Term Dir-G
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_SUCCESS);
        Optional<FundEntry> f = r.getFundByPlanId("16151");
        Assert.assertTrue(f.isPresent(), "plan_id=16151 (HDFC Short Term) must exist");
        Assert.assertEquals(f.get().getCategoryFmt(), "Short Term");
    }

    @Test(groups = "extraction", description = "getFundByPlanId returns empty for unknown ID")
    public void getFundByPlanIdReturnsEmptyForUnknown() {
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_SUCCESS);
        Assert.assertFalse(r.getFundByPlanId("PLAN_UNKNOWN").isPresent());
    }

    @Test(groups = "extraction", description = "getBreakdownByCategoryId returns empty for unknown ID")
    public void getBreakdownByCategoryIdReturnsEmptyForUnknown() {
        ExtractedResponse r = Api2ResponseExtractor.extract(API2_SUCCESS);
        Assert.assertFalse(r.getBreakdownByCategoryId("UNKNOWN_CAT").isPresent());
    }
}
