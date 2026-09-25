package com.vr.portfolioplanner.normalize;

import com.vr.portfolioplanner.response.Api1ResponseExtractor;
import com.vr.portfolioplanner.response.Api2ResponseExtractor;
import com.vr.portfolioplanner.response.model.BreakdownEntry;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import com.vr.portfolioplanner.response.model.FundEntry;
import com.vr.portfolioplanner.response.model.TransactionLeg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Unit tests for the normalization layer.
 * No live API calls. All assertions are against known inputs and outputs.
 */
public class NormalizerTest {

    private static final Logger log = LoggerFactory.getLogger(NormalizerTest.class);

    private static final String API1_SUCCESS = load("sample-responses/api1_success.json");
    private static final String API2_SUCCESS = load("sample-responses/api2_success.json");

    private static String load(String path) {
        try (InputStream is = NormalizerTest.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalStateException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // CurrencyNormalizer
    // =========================================================================

    @Test(groups = "normalize", description = "Strips ₹ symbol and commas")
    public void currency_rupeeSymbolAndCommasStripped() {
        assertBigDecimalEquals(CurrencyNormalizer.parse("₹50,000"), new BigDecimal("50000"));
        log.info("₹50,000 → {}", CurrencyNormalizer.parse("₹50,000"));
    }

    @Test(groups = "normalize", description = "Strips thousands-separator commas only (preserves decimal point)")
    public void currency_commasOnlyStripped() {
        assertBigDecimalEquals(CurrencyNormalizer.parse("50,000.50"), new BigDecimal("50000.50"));
    }

    @Test(groups = "normalize", description = "Indian lakh format: 1,00,000 → 100000")
    public void currency_indianLakhFormat() {
        assertBigDecimalEquals(CurrencyNormalizer.parse("1,00,000"), new BigDecimal("100000"));
    }

    @Test(groups = "normalize", description = "Parses plain integer string without formatting")
    public void currency_plainIntegerString() {
        assertBigDecimalEquals(CurrencyNormalizer.parse("50000"), new BigDecimal("50000"));
    }

    @Test(groups = "normalize", description = "Strips % sign from percentage strings")
    public void currency_percentageStringStripped() {
        assertBigDecimalEquals(CurrencyNormalizer.parse("40.00%"), new BigDecimal("40.00"));
        log.info("40.00% → {}", CurrencyNormalizer.parse("40.00%"));
    }

    @Test(groups = "normalize", description = "parsePercentage helper strips % sign")
    public void currency_parsePercentageHelper() {
        assertBigDecimalEquals(CurrencyNormalizer.parsePercentage("30.00%"), new BigDecimal("30.00"));
        assertBigDecimalEquals(CurrencyNormalizer.parsePercentage("30.00"),  new BigDecimal("30.00"));
    }

    @Test(groups = "normalize", description = "Returns null for null or blank input")
    public void currency_nullAndBlankReturnNull() {
        Assert.assertNull(CurrencyNormalizer.parse((String) null), "null input → null");
        Assert.assertNull(CurrencyNormalizer.parse(""),            "empty string → null");
        Assert.assertNull(CurrencyNormalizer.parse("   "),         "blank string → null");
    }

    @Test(groups = "normalize", description = "Returns null for unparseable input")
    public void currency_unparseableReturnNull() {
        Assert.assertNull(CurrencyNormalizer.parse("not-a-number"));
    }

    @Test(groups = "normalize", description = "BigDecimal pass-through overload is identity")
    public void currency_bigDecimalPassThrough() {
        BigDecimal value = new BigDecimal("12345.67");
        Assert.assertSame(CurrencyNormalizer.parse(value), value);
    }

    @Test(groups = "normalize", description = "long overload wraps as BigDecimal")
    public void currency_longOverload() {
        assertBigDecimalEquals(CurrencyNormalizer.parse(50000L), new BigDecimal("50000"));
    }

    // =========================================================================
    // TransactionTypeNormalizer
    // =========================================================================

    @Test(groups = "normalize", description = "'sip' (lowercase) → SIP")
    public void txnType_sipLowercase() {
        Assert.assertEquals(TransactionTypeNormalizer.normalize("sip"), "SIP");
    }

    @Test(groups = "normalize", description = "'SIP' (uppercase) → SIP")
    public void txnType_sipUppercase() {
        Assert.assertEquals(TransactionTypeNormalizer.normalize("SIP"), "SIP");
    }

    @Test(groups = "normalize", description = "'one_time' (snake_case) → ONE_TIME")
    public void txnType_oneTimeSnakeCase() {
        Assert.assertEquals(TransactionTypeNormalizer.normalize("one_time"), "ONE_TIME");
    }

    @Test(groups = "normalize", description = "'One-time' (API-2 display form) → ONE_TIME")
    public void txnType_oneTimeDisplayForm() {
        Assert.assertEquals(TransactionTypeNormalizer.normalize("One-time"), "ONE_TIME");
    }

    @Test(groups = "normalize", description = "'One-Time' (capitalised variant) → ONE_TIME")
    public void txnType_oneTimeCapitalisedVariant() {
        Assert.assertEquals(TransactionTypeNormalizer.normalize("One-Time"), "ONE_TIME");
    }

    @Test(groups = "normalize", description = "Unknown type → upper-cased pass-through")
    public void txnType_unknownTypeUpperCased() {
        String result = TransactionTypeNormalizer.normalize("quarterly_sip");
        Assert.assertNotNull(result);
        Assert.assertEquals(result, result.toUpperCase().replace('-', '_'),
            "Unknown type must be upper-cased");
        log.info("unknown 'quarterly_sip' → '{}'", result);
    }

    @Test(groups = "normalize", description = "null/blank input → null")
    public void txnType_nullAndBlankReturnNull() {
        Assert.assertNull(TransactionTypeNormalizer.normalize(null));
        Assert.assertNull(TransactionTypeNormalizer.normalize(""));
        Assert.assertNull(TransactionTypeNormalizer.normalize("  "));
    }

    @Test(groups = "normalize", description = "isKnown() returns true only for registered types")
    public void txnType_isKnown() {
        Assert.assertTrue(TransactionTypeNormalizer.isKnown("sip"));
        Assert.assertTrue(TransactionTypeNormalizer.isKnown("SIP"));
        Assert.assertTrue(TransactionTypeNormalizer.isKnown("one_time"));
        Assert.assertTrue(TransactionTypeNormalizer.isKnown("One-time"));
        Assert.assertFalse(TransactionTypeNormalizer.isKnown("unknown"));
        Assert.assertFalse(TransactionTypeNormalizer.isKnown(null));
    }

    @Test(groups = "normalize", description = "API-1 'sip' and API-2 'SIP' both produce the same canonical type")
    public void txnType_api1AndApi2SipAreEquivalent() {
        String api1 = TransactionTypeNormalizer.normalize("sip");
        String api2 = TransactionTypeNormalizer.normalize("SIP");
        Assert.assertEquals(api1, api2,
            "API-1 'sip' and API-2 'SIP' must normalize to the same canonical value");
        log.info("sip → '{}' == SIP → '{}'", api1, api2);
    }

    @Test(groups = "normalize", description = "API-1 'one_time' and API-2 'One-time' both produce ONE_TIME")
    public void txnType_api1AndApi2OneTimeAreEquivalent() {
        String api1 = TransactionTypeNormalizer.normalize("one_time");
        String api2 = TransactionTypeNormalizer.normalize("One-time");
        Assert.assertEquals(api1, api2,
            "API-1 'one_time' and API-2 'One-time' must normalize to the same canonical value");
        log.info("one_time → '{}' == One-time → '{}'", api1, api2);
    }

    // =========================================================================
    // FundNameNormalizer
    // =========================================================================

    @Test(groups = "normalize", description = "Replaces 'Dir-G' with 'Direct-G' mid-name")
    public void fundName_dirGReplaced() {
        String result = FundNameNormalizer.normalize("Alpha Large Cap Fund Dir-G");
        Assert.assertEquals(result, "Alpha Large Cap Fund Direct-G");
        log.info("'Dir-G' normalized: '{}'", result);
    }

    @Test(groups = "normalize", description = "Replaces 'Dir-G' at end of name")
    public void fundName_dirGAtEnd() {
        Assert.assertEquals(
            FundNameNormalizer.normalize("Beta Mid Cap Opportunities Dir-G"),
            "Beta Mid Cap Opportunities Direct-G");
    }

    @Test(groups = "normalize", description = "Does not modify names that already contain 'Direct-G'")
    public void fundName_directGUnchanged() {
        String name = "Alpha Large Cap Fund Direct-G";
        Assert.assertEquals(FundNameNormalizer.normalize(name), name);
    }

    @Test(groups = "normalize", description = "Does not modify names with no known substitution")
    public void fundName_unknownPatternUnchanged() {
        String name = "Ordinary Fund Name";
        Assert.assertEquals(FundNameNormalizer.normalize(name), name);
    }

    @Test(groups = "normalize", description = "Null input returns null")
    public void fundName_nullReturnsNull() {
        Assert.assertNull(FundNameNormalizer.normalize(null));
    }

    @Test(groups = "normalize", description = "matches() confirms API-1 Dir-G equals API-2 Direct-G after normalization")
    public void fundName_matchesApi1VsApi2() {
        String api1Name = "Alpha Large Cap Fund Dir-G";
        String api2Name = "Alpha Large Cap Fund Direct-G";
        Assert.assertTrue(FundNameNormalizer.matches(api1Name, api2Name),
            "After normalization, API-1 'Dir-G' must match API-2 'Direct-G'");
        log.info("FundNameNormalizer.matches('{}', '{}') = true", api1Name, api2Name);
    }

    // =========================================================================
    // FieldMapping registry
    // =========================================================================

    @Test(groups = "normalize", description = "FieldMapping.ALL is non-empty")
    public void fieldMapping_allIsNonEmpty() {
        Assert.assertFalse(FieldMapping.ALL.isEmpty());
        log.info("FieldMapping.ALL has {} entries", FieldMapping.ALL.size());
    }

    @Test(groups = "normalize", description = "All required COMPARABLE mappings are registered")
    public void fieldMapping_comparableMappingsPresent() {
        List<FieldMapping.Entry> comparable = FieldMapping.getComparable();
        List<String> api1Paths = comparable.stream()
            .map(FieldMapping.Entry::api1Path).toList();

        Assert.assertTrue(api1Paths.stream().anyMatch(p -> p.contains("plan_id")),
            "plan_id mapping must be registered as COMPARABLE");
        Assert.assertTrue(api1Paths.stream().anyMatch(p -> p.contains("plan_data.name")),
            "plan name mapping must be registered");
        Assert.assertTrue(api1Paths.stream().anyMatch(p -> p.contains("first_month_amount")),
            "investment amount mapping (first_month_amount) must be registered");
        Assert.assertTrue(api1Paths.stream().anyMatch(p -> p.contains("legs")),
            "transaction type mapping (legs) must be registered");
        Assert.assertTrue(api1Paths.stream().anyMatch(p -> p.contains("perc_fmt")),
            "breakdown percentage mapping (perc_fmt) must be registered");
        log.info("COMPARABLE mappings: {}", comparable.size());
    }

    @Test(groups = "normalize", description = "EXCLUDED fields include port_builder_id and output_id")
    public void fieldMapping_excludedFieldsPresent() {
        List<FieldMapping.Entry> excluded = FieldMapping.getExcluded();
        List<String> api1Paths = excluded.stream().map(FieldMapping.Entry::api1Path).toList();
        Assert.assertTrue(api1Paths.stream().anyMatch(p -> p.contains("port_builder_id")),
            "port_builder_id must be EXCLUDED");
        Assert.assertTrue(api1Paths.stream().anyMatch(p -> p.contains("output_id")),
            "output_id must be EXCLUDED");
        log.info("EXCLUDED mappings: {}", excluded.size());
    }

    @Test(groups = "normalize", description = "NOT_COMPARABLE fields include message and frequency/duration")
    public void fieldMapping_notComparableFieldsPresent() {
        List<FieldMapping.Entry> nc = FieldMapping.getNotComparable();
        List<String> api1Paths = nc.stream().map(FieldMapping.Entry::api1Path).toList();
        Assert.assertTrue(api1Paths.stream().anyMatch(p -> p.contains("message")),
            "message must be NOT_COMPARABLE");
        Assert.assertTrue(api1Paths.stream().anyMatch(p -> p.contains("frequency") || p.contains("duration")),
            "legs frequency/duration must be NOT_COMPARABLE");
        log.info("NOT_COMPARABLE mappings: {}", nc.size());
    }

    @Test(groups = "normalize", description = "investment_amount mapping notes different JSON paths")
    public void fieldMapping_amountMappingNotesDifferentPaths() {
        FieldMapping.Entry amtMapping = FieldMapping.INVESTMENT_AMOUNT;
        Assert.assertEquals(amtMapping.api1Path(), "first_month_amount");
        Assert.assertTrue(amtMapping.api2Path().contains("txn_data"),
            "API-2 path must reference txn_data.amount");
        Assert.assertEquals(amtMapping.status(), ComparabilityStatus.COMPARABLE);
        log.info("Amount mapping: '{}' ↔ '{}'", amtMapping.api1Path(), amtMapping.api2Path());
    }

    // =========================================================================
    // ResponseNormalizer — end-to-end on sample JSON
    // =========================================================================

    @Test(groups = "normalize",
          description = "ResponseNormalizer.normalize() returns non-null for successful extraction")
    public void responseNormalizer_returnsNonNull() {
        ExtractedResponse raw  = Api1ResponseExtractor.extract(API1_SUCCESS);
        ExtractedResponse norm = ResponseNormalizer.normalize(raw);
        Assert.assertNotNull(norm);
        Assert.assertTrue(norm.isExtractionSuccess());
    }

    @Test(groups = "normalize",
          description = "ResponseNormalizer replaces 'Dir-G' with 'Direct-G' in fund names")
    public void responseNormalizer_fundNamesNormalized() {
        ExtractedResponse raw  = Api1ResponseExtractor.extract(API1_SUCCESS);
        ExtractedResponse norm = ResponseNormalizer.normalize(raw);

        for (FundEntry fund : norm.getFunds()) {
            Assert.assertFalse(fund.getPlanName().contains("Dir-G"),
                "Normalized name must not contain 'Dir-G'; got: " + fund.getPlanName());
            Assert.assertTrue(fund.getPlanName().contains("Direct-G"),
                "Normalized name must contain 'Direct-G'; got: " + fund.getPlanName());
            log.info("Normalized fund name: '{}'", fund.getPlanName());
        }
    }

    @Test(groups = "normalize",
          description = "After normalization API-1 and API-2 fund names match for same plan")
    public void responseNormalizer_api1AndApi2FundNamesMatch() {
        // The two sample JSONs represent different scenarios, so most plan_ids differ.
        // plan_id=15752 (Axis Short Term) is the only fund present in BOTH samples.
        // API-1 name: "Axis Short Term Dir-G", API-2 name: "Axis Short Term Direct-G"
        // After FundNameNormalizer: both → "Axis Short Term Direct-G"
        ExtractedResponse norm1 = ResponseNormalizer.normalize(Api1ResponseExtractor.extract(API1_SUCCESS));
        ExtractedResponse norm2 = ResponseNormalizer.normalize(Api2ResponseExtractor.extract(API2_SUCCESS));

        FundEntry f1 = norm1.getFundByPlanId("15752").orElseThrow(
            () -> new AssertionError("plan_id=15752 must exist in API-1 sample"));
        FundEntry f2 = norm2.getFundByPlanId("15752").orElseThrow(
            () -> new AssertionError("plan_id=15752 must exist in API-2 sample"));

        Assert.assertEquals(f1.getPlanName(), f2.getPlanName(),
            "After normalization, Axis Short Term names must match");
        log.info("planId=15752 api1='{}' api2='{}' — match after normalization",
            f1.getPlanName(), f2.getPlanName());
    }

    @Test(groups = "normalize",
          description = "ResponseNormalizer resolves perc_fmt to BigDecimal for all breakdowns")
    public void responseNormalizer_breakdownPercentagesResolved() {
        ExtractedResponse norm = ResponseNormalizer.normalize(Api1ResponseExtractor.extract(API1_SUCCESS));

        for (BreakdownEntry b : norm.getBreakdowns()) {
            Assert.assertNotNull(b.getPercentage(),
                "Normalized breakdown must have non-null percentage for " + b.getCategoryId());
            Assert.assertTrue(b.getPercentage().compareTo(BigDecimal.ZERO) > 0,
                "Percentage must be > 0 for " + b.getCategoryId());
            log.info("Breakdown {} percentage = {}", b.getCategoryId(), b.getPercentage());
        }
    }

    @Test(groups = "normalize",
          description = "TransactionLeg normalizedType from API-1 'sip'/'one_time' is SIP/ONE_TIME")
    public void responseNormalizer_api1LegTypesNormalized() {
        ExtractedResponse r = Api1ResponseExtractor.extract(API1_SUCCESS);
        FundEntry firstFund = r.getFunds().get(0);
        List<TransactionLeg> legs = firstFund.getLegs();

        Assert.assertFalse(legs.isEmpty(), "Must have at least one leg");
        Assert.assertTrue(legs.stream().anyMatch(TransactionLeg::isSip),
            "Must have at least one SIP leg");
        Assert.assertTrue(legs.stream().anyMatch(TransactionLeg::isOneTime),
            "Must have at least one ONE_TIME leg");

        for (TransactionLeg leg : legs) {
            log.info("API-1 leg: raw='{}' → normalized='{}'", leg.getRawType(), leg.getNormalizedType());
        }
    }

    @Test(groups = "normalize",
          description = "API-2 fund-level legs are empty — inv_data is not at funds_data level in real API")
    public void responseNormalizer_api2FundLevelLegsAreEmpty() {
        // The real API-2 response does not expose inv_data[] at the funds_data.data[] level.
        // Only txn_data.amount (fund total) is available there.
        ExtractedResponse r = Api2ResponseExtractor.extract(API2_SUCCESS);
        FundEntry firstFund = r.getFunds().get(0);
        List<TransactionLeg> legs = firstFund.getLegs();

        Assert.assertTrue(legs.isEmpty(),
            "API-2 fund-level legs must be empty (inv_data not available at funds_data level)");
        log.info("API-2 fund-level legs correctly empty: {} legs", legs.size());
    }

    @Test(groups = "normalize",
          description = "API-1 legs carry normalizedType; API-2 has no fund-level legs — comparison is skipped")
    public void responseNormalizer_api1HasLegsApi2DoesNot() {
        ExtractedResponse r1 = Api1ResponseExtractor.extract(API1_SUCCESS);
        ExtractedResponse r2 = Api2ResponseExtractor.extract(API2_SUCCESS);

        // Use plan_id=15752 — exists in both sample responses
        FundEntry f1 = r1.getFundByPlanId("15752").orElseThrow(
            () -> new AssertionError("plan_id=15752 must exist in API-1"));
        FundEntry f2 = r2.getFundByPlanId("15752").orElseThrow(
            () -> new AssertionError("plan_id=15752 must exist in API-2"));

        Assert.assertFalse(f1.getLegs().isEmpty(), "API-1 must have fund-level legs");
        Assert.assertTrue(f2.getLegs().isEmpty(),  "API-2 must have empty fund-level legs");

        // Confirm API-1 legs carry correct normalized types
        List<String> types1 = f1.getLegs().stream()
            .map(TransactionLeg::getNormalizedType).sorted().toList();
        Assert.assertTrue(types1.contains("SIP"),      "API-1 must have SIP leg");
        Assert.assertTrue(types1.contains("ONE_TIME"), "API-1 must have ONE_TIME leg");
        log.info("API-1 plan_id=15752 leg types: {} | API-2: (empty — not available at fund level)", types1);
    }

    @Test(groups = "normalize",
          description = "ResponseNormalizer skips failed extractions without throwing")
    public void responseNormalizer_handlesFailedExtractionGracefully() {
        ExtractedResponse failed = ExtractedResponse.failed("API-1", "test error");
        ExtractedResponse result = ResponseNormalizer.normalize(failed);
        Assert.assertFalse(result.isExtractionSuccess(),
            "Normalizing a failed extraction must still be a failure");
        Assert.assertNotNull(result.getExtractionError());
    }

    // =========================================================================
    // BreakdownEntry.resolvePercentage() — percFmt fallback
    // =========================================================================

    @Test(groups = "normalize",
          description = "resolvePercentage prefers numeric percentage over percFmt")
    public void breakdownEntry_resolvePrefersNumericPercentage() {
        BreakdownEntry b = new BreakdownEntry("CAT1", "Test", new BigDecimal("40.00"), "40.00%", null);
        assertBigDecimalEquals(b.resolvePercentage(), new BigDecimal("40.00"));
    }

    @Test(groups = "normalize",
          description = "resolvePercentage falls back to percFmt when numeric percentage is null")
    public void breakdownEntry_resolveFallsBackToPercFmt() {
        BreakdownEntry b = new BreakdownEntry("CAT1", "Test", null, "30.00%", null);
        assertBigDecimalEquals(b.resolvePercentage(), new BigDecimal("30.00"));
        log.info("perc_fmt fallback: '30.00%' → {}", b.resolvePercentage());
    }

    @Test(groups = "normalize",
          description = "resolvePercentage returns null when both fields are absent")
    public void breakdownEntry_resolveBothAbsentReturnsNull() {
        BreakdownEntry b = new BreakdownEntry("CAT1", "Test", null, null, null);
        Assert.assertNull(b.resolvePercentage());
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static void assertBigDecimalEquals(BigDecimal actual, BigDecimal expected) {
        Assert.assertNotNull(actual, "BigDecimal result must not be null (expected " + expected + ")");
        Assert.assertEquals(actual.compareTo(expected), 0,
            "Expected " + expected + " but got " + actual);
    }
}
