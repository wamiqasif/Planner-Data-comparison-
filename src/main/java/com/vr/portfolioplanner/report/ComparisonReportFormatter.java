package com.vr.portfolioplanner.report;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.vr.portfolioplanner.altfund.AlternateFundAudit;
import com.vr.portfolioplanner.compare.ComparisonResult;
import com.vr.portfolioplanner.compare.Mismatch;
import com.vr.portfolioplanner.compare.MismatchType;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Formats {@link ComparisonResult} objects into human-readable output.
 *
 * <p>Two output targets are supported:
 * <ol>
 *   <li>Console — structured plain-text block with fund context</li>
 *   <li>ExtentReports — HTML nodes organised by comparison category</li>
 * </ol>
 *
 * <p>Security: credential values are never passed to or emitted by this class.
 * Only HTTP status codes, response times, fund names, and comparison field
 * values are reported.
 */
public final class ComparisonReportFormatter {

    private static final Logger log = LoggerFactory.getLogger(ComparisonReportFormatter.class);

    private static final String LINE = "═".repeat(60);
    private static final String DASH = "─".repeat(60);

    private ComparisonReportFormatter() {}

    // =========================================================================
    // Console block
    // =========================================================================

    /**
     * Returns a structured console block for the comparison result.
     * Called in {@link com.vr.portfolioplanner.test.ApiComparisonTest} to produce
     * the detailed log output with fund name and field context.
     */
    public static String formatConsoleBlock(
            String testCaseId,
            ComparisonResult result,
            ExtractedResponse norm1,
            ExtractedResponse norm2,
            int api1Status,
            int api2Status,
            long api1TimeMs,
            long api2TimeMs) {

        StringBuilder sb = new StringBuilder("\n").append(LINE).append("\n");
        sb.append("PORTFOLIO PLANNER COMPARISON RESULT\n").append(LINE).append("\n");
        sb.append(String.format("Test Case        : %s%n", testCaseId));
        sb.append(String.format("Verdict          : %s%n", result.getVerdict()));
        sb.append(String.format("API-1 Status     : %d  (%d ms)%n", api1Status, api1TimeMs));
        sb.append(String.format("API-2 Status     : %d  (%d ms)%n", api2Status, api2TimeMs));

        if (norm1.isExtractionSuccess()) {
            sb.append(String.format("API-1 Funds      : %d%n", norm1.getFunds().size()));
        }
        if (norm2.isExtractionSuccess()) {
            sb.append(String.format("API-2 Funds      : %d%n", norm2.getFunds().size()));
        }
        sb.append(String.format("Failing Mismatch : %d%n", result.getFailingMismatchCount()));
        sb.append(LINE).append("\n\n");

        // Failing mismatches
        List<Mismatch> failing = result.getFailingMismatches();
        for (int i = 0; i < failing.size(); i++) {
            Mismatch m = failing.get(i);
            sb.append(String.format("[%d] %s%n", i + 1, categoryLabel(m.getMismatchType())));
            appendFundContext(sb, m);
            sb.append(String.format("  Field      : %s%n", friendlyField(m.getFieldPath())));
            sb.append(String.format("  API-1      : %s%n", nvl(m.getApi1Value())));
            sb.append(String.format("  API-2      : %s%n", nvl(m.getApi2Value())));
            if (m.getDifference() != null) {
                sb.append(String.format("  Difference : %s%n", m.getDifference()));
            }
            if (m.getTolerance() != null) {
                sb.append(String.format("  Tolerance  : %s%n", m.getTolerance()));
            }
            sb.append(String.format("  Type       : %s%n", m.getMismatchType()));
            sb.append(String.format("  Result     : FAIL%n%n"));
        }

        // Informational (NORMALIZATION_DIFFERENCE)
        List<Mismatch> normDiffs = result.getByType(MismatchType.NORMALIZATION_DIFFERENCE);
        for (Mismatch m : normDiffs) {
            sb.append("[INFO] NORMALIZATION DIFFERENCE\n");
            appendFundContext(sb, m);
            sb.append(String.format("  Field      : %s%n", friendlyField(m.getFieldPath())));
            sb.append(String.format("  API-1 (raw): %s%n", nvl(m.getApi1Value())));
            sb.append(String.format("  API-2 (raw): %s%n", nvl(m.getApi2Value())));
            sb.append("  Result     : INFO / PASS\n\n");
        }

        // Alternate-fund validation (API-2 original vs API-1 alternate candidates)
        List<AlternateFundAudit> audits = result.getAlternateFundAudits();
        for (AlternateFundAudit a : audits) {
            sb.append("[ALT-FUND] ").append(a.getMatchType()).append("\n");
            sb.append(buildAuditConsoleBlock(a));
            sb.append("\n");
        }

        sb.append(LINE).append("\n");
        return sb.toString();
    }

    // =========================================================================
    // ExtentReports population
    // =========================================================================

    /**
     * Populates the given {@link ExtentTest} with structured child nodes for
     * each comparison category.
     */
    public static void populateExtentTest(
            ExtentTest extentTest,
            String testCaseId,
            ComparisonResult result,
            ExtractedResponse norm1,
            ExtractedResponse norm2,
            int api1Status,
            int api2Status,
            long api1TimeMs,
            long api2TimeMs) {

        // ── 1. API Execution node ────────────────────────────────────────────
        ExtentTest apiNode = extentTest.createNode("1. API Execution");
        String[][] apiTable = {
            {"", "API-1", "API-2"},
            {"HTTP Status",     String.valueOf(api1Status),  String.valueOf(api2Status)},
            {"Response Time",   api1TimeMs + " ms",          api2TimeMs + " ms"},
            {"Extraction",
             norm1.isExtractionSuccess() ? "OK" : "FAILED",
             norm2.isExtractionSuccess() ? "OK" : "FAILED"},
        };
        apiNode.log(Status.INFO, MarkupHelper.createTable(apiTable));

        // Execution errors
        result.getByType(MismatchType.API_EXECUTION_ERROR).forEach(m ->
            apiNode.fail(buildMismatchBlock(m)));

        if (result.getByType(MismatchType.API_EXECUTION_ERROR).isEmpty()
                && norm1.isExtractionSuccess() && norm2.isExtractionSuccess()) {
            apiNode.pass("Both APIs responded and extraction succeeded");
        }

        // ── 2. Fund Matching node ────────────────────────────────────────────
        ExtentTest fundNode = extentTest.createNode("2. Fund Matching");
        int f1 = norm1.isExtractionSuccess() ? norm1.getFunds().size() : -1;
        int f2 = norm2.isExtractionSuccess() ? norm2.getFunds().size() : -1;

        Set<String> ids1 = norm1.isExtractionSuccess()
            ? norm1.getFunds().stream().map(e -> e.getPlanId())
                   .filter(id -> id != null).collect(Collectors.toSet())
            : Set.of();
        Set<String> ids2 = norm2.isExtractionSuccess()
            ? norm2.getFunds().stream().map(e -> e.getPlanId())
                   .filter(id -> id != null).collect(Collectors.toSet())
            : Set.of();

        long matched = ids1.stream().filter(ids2::contains).count();
        long missing = ids1.stream().filter(id -> !ids2.contains(id)).count();
        long extra   = ids2.stream().filter(id -> !ids1.contains(id)).count();

        String[][] fundTable = {
            {"Metric", "Count"},
            {"API-1 Funds",    f1 >= 0 ? String.valueOf(f1) : "N/A"},
            {"API-2 Funds",    f2 >= 0 ? String.valueOf(f2) : "N/A"},
            {"Matched Funds",  String.valueOf(matched)},
            {"Missing Funds",  String.valueOf(missing)},
            {"Extra Funds",    String.valueOf(extra)},
        };
        fundNode.log(Status.INFO, MarkupHelper.createTable(fundTable));

        result.getByType(MismatchType.COUNT_MISMATCH).forEach(m ->
            fundNode.warning("Fund count mismatch: API-1=" + m.getApi1Value()
                             + " API-2=" + m.getApi2Value()));

        result.getByType(MismatchType.MISSING_FIELD).stream()
            .filter(m -> m.getFieldPath() != null && m.getFieldPath().contains("funds_data"))
            .forEach(m -> fundNode.fail(buildMismatchBlock(m)));

        result.getByType(MismatchType.EXTRA_FIELD).stream()
            .filter(m -> m.getFieldPath() != null && m.getFieldPath().contains("funds_data"))
            .forEach(m -> fundNode.fail(buildMismatchBlock(m)));

        if (missing == 0 && extra == 0 && f1 == f2 && f1 > 0) {
            fundNode.pass("All " + matched + " fund(s) matched by plan_id");
        }

        // ── 3. Field Comparison node ─────────────────────────────────────────
        ExtentTest fieldNode = extentTest.createNode("3. Field Comparison");
        List<Mismatch> fieldMismatches = result.getFailingMismatches().stream()
            .filter(m -> m.getFieldPath() != null
                && (m.getFieldPath().contains("category")
                    || m.getFieldPath().contains("fund_name")
                    || m.getFieldPath().contains("investment_amount")))
            .collect(Collectors.toList());

        if (fieldMismatches.isEmpty()) {
            fieldNode.pass("All fund field comparisons passed");
        } else {
            fieldMismatches.forEach(m -> fieldNode.fail(buildMismatchBlock(m)));
        }

        // ── 4. Breakdown Comparison node ─────────────────────────────────────
        ExtentTest bdNode = extentTest.createNode("4. Breakdown Comparison");
        List<Mismatch> bdMismatches = result.getFailingMismatches().stream()
            .filter(m -> m.getFieldPath() != null
                && m.getFieldPath().contains("breakdown"))
            .collect(Collectors.toList());

        if (bdMismatches.isEmpty()) {
            bdNode.pass("All breakdown comparisons passed");
        } else {
            bdMismatches.forEach(m -> bdNode.fail(buildMismatchBlock(m)));
        }

        // ── 5. Transaction Comparison node ────────────────────────────────────
        ExtentTest txnNode = extentTest.createNode("5. Transaction Comparison");
        List<Mismatch> txnMismatches = result.getFailingMismatches().stream()
            .filter(m -> m.getFieldPath() != null
                && m.getFieldPath().contains("legs"))
            .collect(Collectors.toList());

        if (txnMismatches.isEmpty()) {
            txnNode.pass("Transaction comparison passed (or leg data not available for API-2)");
        } else {
            txnMismatches.forEach(m -> txnNode.fail(buildMismatchBlock(m)));
        }

        // ── 6. Normalization node ─────────────────────────────────────────────
        ExtentTest normNode = extentTest.createNode("6. Normalization");
        List<Mismatch> normDiffs = result.getByType(MismatchType.NORMALIZATION_DIFFERENCE);
        if (normDiffs.isEmpty()) {
            normNode.pass("No normalization differences");
        } else {
            normDiffs.forEach(m -> {
                normNode.log(Status.INFO, MarkupHelper.createLabel(
                    "NORMALIZATION DIFFERENCE (informational)", ExtentColor.BLUE));
                normNode.info(buildNormDiffBlock(m));
            });
        }

        // ── 7. Alternate Fund Validation node ────────────────────────────────
        List<AlternateFundAudit> audits = result.getAlternateFundAudits();
        if (!audits.isEmpty()) {
            ExtentTest altNode = extentTest.createNode("7. Alternate Fund Validation");
            for (AlternateFundAudit a : audits) {
                String block = buildAuditExtentBlock(a);
                if ("PASS".equals(a.getFinalResult())) {
                    altNode.pass(block);
                } else if ("FAIL".equals(a.getFinalResult())) {
                    altNode.fail(block);
                } else {
                    altNode.log(Status.WARNING, block); // REQUIRED_DATA_NOT_AVAILABLE / PAIRING_AMBIGUOUS
                }
            }
        }

        // ── 8. Final Result node ──────────────────────────────────────────────
        ExtentTest finalNode = extentTest.createNode("8. Final Result");
        String[][] summaryTable = {
            {"Metric", "Value"},
            {"Test Case",           testCaseId},
            {"API-1 HTTP Status",   String.valueOf(api1Status)},
            {"API-2 HTTP Status",   String.valueOf(api2Status)},
            {"API-1 Funds",         f1 >= 0 ? String.valueOf(f1) : "N/A"},
            {"API-2 Funds",         f2 >= 0 ? String.valueOf(f2) : "N/A"},
            {"Matched Funds",       String.valueOf(matched)},
            {"Missing Funds",       String.valueOf(missing)},
            {"Extra Funds",         String.valueOf(extra)},
            {"Failing Mismatches",  String.valueOf(result.getFailingMismatchCount())},
            {"Normalization Diffs", String.valueOf(normDiffs.size())},
            {"Final Verdict",       result.getVerdict()},
        };
        finalNode.log(Status.INFO, MarkupHelper.createTable(summaryTable));

        if (result.isPassed()) {
            finalNode.pass(MarkupHelper.createLabel("PASS", ExtentColor.GREEN));
            extentTest.pass(MarkupHelper.createLabel("PASS", ExtentColor.GREEN));
        } else {
            finalNode.fail(MarkupHelper.createLabel("FAIL", ExtentColor.RED));
            extentTest.fail(MarkupHelper.createLabel(
                "FAIL — " + result.getFailingMismatchCount() + " mismatch(es)", ExtentColor.RED));
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /** Builds an HTML details block for one mismatch with full fund context. */
    private static String buildMismatchBlock(Mismatch m) {
        StringBuilder sb = new StringBuilder();
        if (m.getPlanId() != null) {
            sb.append("<b>Plan ID:</b> ").append(m.getPlanId()).append("<br>");
        }
        if (m.getApi1FundName() != null) {
            sb.append("<b>API-1 Fund:</b> ").append(esc(m.getApi1FundName())).append("<br>");
        }
        if (m.getApi2FundName() != null) {
            sb.append("<b>API-2 Fund:</b> ").append(esc(m.getApi2FundName())).append("<br>");
        }
        sb.append("<b>Field:</b> ").append(esc(friendlyField(m.getFieldPath()))).append("<br>");
        sb.append("<b>API-1:</b> ").append(esc(nvl(m.getApi1Value()))).append("<br>");
        sb.append("<b>API-2:</b> ").append(esc(nvl(m.getApi2Value()))).append("<br>");
        if (m.getDifference() != null) {
            sb.append("<b>Difference:</b> ").append(esc(m.getDifference())).append("<br>");
        }
        if (m.getTolerance() != null) {
            sb.append("<b>Tolerance:</b> ").append(esc(m.getTolerance())).append("<br>");
        }
        sb.append("<b>Type:</b> ").append(m.getMismatchType()).append("<br>");
        if (m.getMessage() != null) {
            sb.append("<b>Message:</b> ").append(esc(m.getMessage())).append("<br>");
        }
        return sb.toString();
    }

    /** Plain-text audit block for the console output. */
    private static String buildAuditConsoleBlock(AlternateFundAudit a) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  API-2 Original : %s - %s%n", nvl(a.getApi2PlanId()), nvl(a.getApi2FundName())));
        sb.append(String.format("  API-1 Alternate: %s - %s%n", nvl(a.getApi1PlanId()), nvl(a.getApi1FundName())));
        if (a.getApi1CategoryName() != null) {
            sb.append(String.format("  Category       : %s%n", a.getApi1CategoryName()));
        }
        if (a.getRule1() != null) sb.append(String.format("  Rule 1 (Opinion)          : %s%n", a.getRule1()));
        if (a.getRule2() != null) sb.append(String.format("  Rule 2 (Prohibited)       : %s%n", a.getRule2()));
        if (a.getRule3() != null) sb.append(String.format("  Rule 3 (Index Fund)  : %s%n", a.getRule3()));
        if (a.getRule4() != null) sb.append(String.format("  Rule 4 (Star Rating)      : %s%n", a.getRule4()));
        if (a.getRule5() != null) sb.append(String.format("  Rule 5 (Watchlist)        : %s%n", a.getRule5()));
        if (a.getFailedCondition() != null && !a.getFailedCondition().isEmpty()) {
            sb.append(String.format("  Failed Condition: %s%n", a.getFailedCondition()));
        }
        sb.append(String.format("  Final Result   : %s%n", a.getFinalResult()));
        return sb.toString();
    }

    /** HTML audit block for the Extent report. */
    private static String buildAuditExtentBlock(AlternateFundAudit a) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>Match Type:</b> ").append(a.getMatchType()).append("<br>");
        sb.append("<b>API-2 Original:</b> ").append(esc(nvl(a.getApi2PlanId()))).append(" - ")
          .append(esc(nvl(a.getApi2FundName()))).append("<br>");
        sb.append("<b>API-1 Alternate:</b> ").append(esc(nvl(a.getApi1PlanId()))).append(" - ")
          .append(esc(nvl(a.getApi1FundName()))).append("<br>");
        if (a.getApi1CategoryName() != null) {
            sb.append("<b>Category:</b> ").append(esc(a.getApi1CategoryName())).append("<br>");
        }
        if (a.getRule1() != null) sb.append("<b>Rule 1 (Opinion):</b> ").append(esc(String.valueOf(a.getRule1()))).append("<br>");
        if (a.getRule2() != null) sb.append("<b>Rule 2 (Prohibited):</b> ").append(esc(String.valueOf(a.getRule2()))).append("<br>");
        if (a.getRule3() != null) sb.append("<b>Rule 3 (Index Fund):</b> ").append(esc(String.valueOf(a.getRule3()))).append("<br>");
        if (a.getRule4() != null) sb.append("<b>Rule 4 (Star Rating):</b> ").append(esc(String.valueOf(a.getRule4()))).append("<br>");
        if (a.getRule5() != null) sb.append("<b>Rule 5 (Watchlist):</b> ").append(esc(String.valueOf(a.getRule5()))).append("<br>");
        if (a.getFailedCondition() != null && !a.getFailedCondition().isEmpty()) {
            sb.append("<b>Failed Condition:</b> ").append(esc(a.getFailedCondition())).append("<br>");
        }
        sb.append("<b>Final Result:</b> ").append(a.getFinalResult()).append("<br>");
        return sb.toString();
    }

    private static String buildNormDiffBlock(Mismatch m) {
        StringBuilder sb = new StringBuilder();
        if (m.getPlanId()       != null) sb.append("<b>Plan ID:</b> ").append(m.getPlanId()).append("<br>");
        if (m.getApi1FundName() != null) sb.append("<b>API-1 Fund:</b> ").append(esc(m.getApi1FundName())).append("<br>");
        if (m.getApi2FundName() != null) sb.append("<b>API-2 Fund:</b> ").append(esc(m.getApi2FundName())).append("<br>");
        sb.append("<b>API-1 (raw):</b> ").append(esc(nvl(m.getApi1Value()))).append("<br>");
        sb.append("<b>API-2 (raw):</b> ").append(esc(nvl(m.getApi2Value()))).append("<br>");
        sb.append("<b>Result:</b> INFO / PASS<br>");
        return sb.toString();
    }

    private static void appendFundContext(StringBuilder sb, Mismatch m) {
        if (m.getPlanId()       != null) sb.append(String.format("  Plan ID    : %s%n", m.getPlanId()));
        if (m.getApi1FundName() != null) sb.append(String.format("  Fund API-1 : %s%n", m.getApi1FundName()));
        if (m.getApi2FundName() != null) sb.append(String.format("  Fund API-2 : %s%n", m.getApi2FundName()));
    }

    /** Converts a JSON field-path to a short human-readable label. */
    static String friendlyField(String fieldPath) {
        if (fieldPath == null) return "";
        // Extract the trailing segment after the last dot
        int lastDot = fieldPath.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fieldPath.length() - 1) {
            return fieldPath.substring(lastDot + 1);
        }
        return fieldPath;
    }

    static String categoryLabel(MismatchType type) {
        return switch (type) {
            case BUSINESS_VALUE_MISMATCH  -> "FUND FIELD MISMATCH (numeric)";
            case VALUE_MISMATCH           -> "FUND FIELD MISMATCH (value)";
            case TYPE_MISMATCH            -> "FUND FIELD MISMATCH (type)";
            case COUNT_MISMATCH           -> "FUND COUNT MISMATCH";
            case MISSING_FIELD            -> "MISSING FUND / FIELD";
            case EXTRA_FIELD              -> "EXTRA FUND / FIELD";
            case STATUS_MISMATCH          -> "HTTP STATUS MISMATCH";
            case API_EXECUTION_ERROR      -> "API EXECUTION ERROR";
            case NORMALIZATION_DIFFERENCE -> "NORMALIZATION DIFFERENCE";
            case ALTERNATE_FUND_MATCH             -> "ALTERNATE FUND MATCH";
            case ALTERNATE_FUND_REJECTED          -> "ALTERNATE FUND REJECTED";
            case ALTERNATE_FUND_PAIRING_AMBIGUOUS -> "ALTERNATE FUND PAIRING AMBIGUOUS";
            case ALTERNATE_FUND_DATA_UNAVAILABLE  -> "ALTERNATE FUND DATA UNAVAILABLE";
        };
    }

    private static String nvl(String s) { return s != null ? s : "(null)"; }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
