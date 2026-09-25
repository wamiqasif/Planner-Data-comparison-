package com.vr.portfolioplanner.report;

import com.vr.portfolioplanner.altfund.AlternateFundAudit;
import com.vr.portfolioplanner.altfund.RuleResult;
import com.vr.portfolioplanner.compare.ComparisonResult;
import com.vr.portfolioplanner.compare.Mismatch;
import com.vr.portfolioplanner.compare.MismatchType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes test-execution results to an Excel (.xlsx) report using Apache POI.
 *
 * <h3>Output file</h3>
 * {@code target/reports/API_Comparison_Result.xlsx}
 *
 * <h3>Sheet 1 — "Summary"</h3>
 * One row per test case with fund-count stats and aggregated mismatch detail.
 * The Description column carries the Excel fixture's scenario description followed
 * by the exact outbound API-1 / API-2 request payloads; the raw API-1 / API-2
 * response bodies are printed in their own dedicated columns.
 *
 * <h3>Sheet 2 — "Mismatch Details"</h3>
 * One row per individual {@link Mismatch} with full fund context:
 * TestCaseID, PlanID, API1_Fund_Name, API2_Fund_Name, Mismatch_Type,
 * Field, API1_Value, API2_Value, Difference, Tolerance, Result, Message.
 *
 * <h3>Sheet 3 — "Overall Summary"</h3>
 * Aggregated totals across all test cases.
 */
public final class ExcelResultWriter {

    private static final Logger log = LoggerFactory.getLogger(ExcelResultWriter.class);

    // ── Sheet 1 column headers ───────────────────────────────────────────────
    private static final String[] SUMMARY_HEADERS = {
        "TestCaseID", "Description", "ExecutionStatus",
        "API1_HTTP_Status", "API2_HTTP_Status",
        "API1_Request_Timestamp", "API2_Request_Timestamp",
        "API1_Response_Time (ms)", "API2_Response_Time (ms)",
        "API1_Response_Body", "API2_Response_Body",
        "API1_Fund_Count", "API2_Fund_Count",
        "Matched_Funds", "Missing_Funds", "Extra_Funds",
        "Comparison_Result", "Mismatch_Count", "Normalization_Diffs",
        "Mismatch_Details"
    };

    private static final int[] SUMMARY_COL_WIDTHS = {
        14, 60, 18, 18, 18, 24, 24, 24, 24, 60, 60, 16, 16, 16, 16, 16, 20, 16, 20, 80
    };

    // ── Sheet 2 column headers ───────────────────────────────────────────────
    private static final String[] DETAIL_HEADERS = {
        "TestCaseID", "PlanID", "API1_Fund_Name", "API2_Fund_Name",
        "Mismatch_Type", "Field", "API1_Value", "API2_Value",
        "Difference", "Tolerance", "Result", "Message"
    };

    private static final int[] DETAIL_COL_WIDTHS = {
        14, 14, 44, 44, 28, 30, 24, 24, 14, 12, 10, 56
    };

    // ── Sheet 3/4 (Alternate Fund Validation / Data Gaps) column headers ────
    private static final String[] ALT_FUND_HEADERS = {
        "TestCaseID", "API2_Plan_ID", "API2_Fund_Name", "API1_Plan_ID", "API1_Fund_Name",
        "Match_Type", "API1_Alternate_Opinion_Type", "API1_Alternate_Is_Analyst_Pick",
        "API1_Alternate_VR_Rating", "API1_Alternate_Tag_Name", "API1_Alternate_Category_Name",
        "Rule_1_Result", "Rule_2_Result", "Rule_3_Result", "Rule_4_Result", "Rule_5_Result",
        "Failed_Condition", "Final_Result"
    };

    private static final int[] ALT_FUND_COL_WIDTHS = {
        14, 14, 40, 14, 40, 22, 14, 14, 14, 20, 30, 34, 34, 34, 34, 34, 60, 24
    };

    private ExcelResultWriter() {}

    // =========================================================================
    // Public API
    // =========================================================================

    public static void write(List<TestCaseResult> results, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        log.info("Writing Excel report: {} ({} row(s))", outputPath.toAbsolutePath(), results.size());

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles styles = new Styles(wb);

            writeSummarySheet(wb, results, styles);
            writeDetailSheet(wb, results, styles);
            writeAlternateFundSheet(wb, results, styles);
            writeDataGapsSheet(wb, results, styles);
            writeOverallSummarySheet(wb, results, styles);

            try (OutputStream os = Files.newOutputStream(outputPath)) {
                wb.write(os);
            }
        }
        log.info("Excel report written: {}", outputPath.toAbsolutePath());
    }

    // =========================================================================
    // Sheet 1 — Summary
    // =========================================================================

    private static void writeSummarySheet(XSSFWorkbook wb, List<TestCaseResult> results, Styles s) {
        Sheet sheet = wb.createSheet("Summary");
        sheet.createFreezePane(0, 1);

        Row hdr = sheet.createRow(0);
        for (int c = 0; c < SUMMARY_HEADERS.length; c++) {
            cell(hdr, c, SUMMARY_HEADERS[c], s.header);
        }

        for (int i = 0; i < results.size(); i++) {
            TestCaseResult r = results.get(i);
            Row row = sheet.createRow(i + 1);

            CellStyle statusSt = resolveStatusStyle(r.getExecutionStatus(), s);
            CellStyle cmpSt    = resolveStatusStyle(r.getComparisonResult(), s);

            String descriptionCell = buildDescriptionCell(r);

            str(row,  0, r.getTestCaseId(),       s.plain);
            str(row,  1, descriptionCell,          s.wrap);
            str(row,  2, r.getExecutionStatus(),   statusSt);
            num(row,  3, r.getApi1HttpStatus(),    s.numeric);
            num(row,  4, r.getApi2HttpStatus(),    s.numeric);
            str(row,  5, r.getApi1RequestTimestamp(), s.plain);
            str(row,  6, r.getApi2RequestTimestamp(), s.plain);
            num(row,  7, r.getApi1ResponseTimeMs(), s.numeric);
            num(row,  8, r.getApi2ResponseTimeMs(), s.numeric);
            str(row,  9, r.getApi1ResponseBody(),  s.wrap);
            str(row, 10, r.getApi2ResponseBody(),  s.wrap);
            fundNum(row, 11, r.getApi1FundCount(),  s);
            fundNum(row, 12, r.getApi2FundCount(),  s);
            num(row, 13, r.getMatchedFundCount(),  s.numeric);
            num(row, 14, r.getMissingFundCount(),  s.numeric);
            num(row, 15, r.getExtraFundCount(),    s.numeric);
            str(row, 16, r.getComparisonResult(),  cmpSt);
            num(row, 17, r.getMismatchCount(),     s.numeric);
            num(row, 18, r.getNormalizationDiffCount(), s.numeric);
            str(row, 19, r.getMismatchDetails(),   s.wrap);

            long maxLines = maxLineCount(descriptionCell, r.getApi1ResponseBody(),
                r.getApi2ResponseBody(), r.getMismatchDetails());
            if (maxLines > 1) {
                row.setHeightInPoints(Math.min(400f, Math.max(20f, maxLines * 14.5f)));
            }
        }

        for (int c = 0; c < SUMMARY_COL_WIDTHS.length; c++) {
            sheet.setColumnWidth(c, SUMMARY_COL_WIDTHS[c] * 256);
        }
    }

    /**
     * Builds the Description cell: the Excel fixture's scenario description
     * followed by the exact outbound API-1 / API-2 request payloads (when present).
     */
    private static String buildDescriptionCell(TestCaseResult r) {
        StringBuilder sb = new StringBuilder(r.getDescription());
        if (!r.getApi1Payload().isEmpty()) {
            sb.append("\n\n--- API-1 Request Payload ---\n").append(r.getApi1Payload());
        }
        if (!r.getApi2Payload().isEmpty()) {
            sb.append("\n\n--- API-2 Request Payload ---\n").append(r.getApi2Payload());
        }
        return sb.toString();
    }

    private static long maxLineCount(String... values) {
        long max = 1;
        for (String v : values) {
            if (v == null || v.isEmpty()) continue;
            long lines = v.chars().filter(c -> c == '\n').count() + 1;
            max = Math.max(max, lines);
        }
        return max;
    }

    // =========================================================================
    // Sheet 2 — Mismatch Details
    // =========================================================================

    private static void writeDetailSheet(XSSFWorkbook wb, List<TestCaseResult> results, Styles s) {
        Sheet sheet = wb.createSheet("Mismatch Details");
        sheet.createFreezePane(0, 1);

        Row hdr = sheet.createRow(0);
        for (int c = 0; c < DETAIL_HEADERS.length; c++) {
            cell(hdr, c, DETAIL_HEADERS[c], s.header);
        }

        int rowIdx = 1;
        for (TestCaseResult r : results) {
            // Failing mismatches
            for (Mismatch m : r.getMismatches()) {
                if (!m.isFailing()) continue;
                Row row = sheet.createRow(rowIdx++);
                writeMismatchRow(row, r.getTestCaseId(), m, "FAIL", s);
            }
            // Normalization differences (informational)
            for (Mismatch m : r.getMismatches()) {
                if (m.getMismatchType() != MismatchType.NORMALIZATION_DIFFERENCE) continue;
                Row row = sheet.createRow(rowIdx++);
                writeMismatchRow(row, r.getTestCaseId(), m, "INFO", s);
            }
        }

        for (int c = 0; c < DETAIL_COL_WIDTHS.length; c++) {
            sheet.setColumnWidth(c, DETAIL_COL_WIDTHS[c] * 256);
        }
    }

    private static void writeMismatchRow(Row row, String testCaseId, Mismatch m,
                                          String result, Styles s) {
        CellStyle resultSt = "FAIL".equals(result) ? s.fail
                           : "INFO".equals(result) ? s.warn
                           : s.pass;

        str(row,  0, testCaseId,                           s.plain);
        str(row,  1, nvl(m.getPlanId()),                   s.plain);
        str(row,  2, nvl(m.getApi1FundName()),             s.plain);
        str(row,  3, nvl(m.getApi2FundName()),             s.plain);
        str(row,  4, m.getMismatchType().name(),           s.plain);
        str(row,  5, ComparisonReportFormatter.friendlyField(m.getFieldPath()), s.plain);
        str(row,  6, nvl(m.getApi1Value()),                s.plain);
        str(row,  7, nvl(m.getApi2Value()),                s.plain);
        str(row,  8, nvl(m.getDifference()),               s.plain);
        str(row,  9, nvl(m.getTolerance()),                s.plain);
        str(row, 10, result,                               resultSt);
        str(row, 11, nvl(m.getMessage()),                  s.plain);
    }

    // =========================================================================
    // Sheet 3 — Alternate Fund Validation
    // =========================================================================

    /**
     * One row per {@link AlternateFundAudit} — every fund slot that was not
     * resolved by a direct {@code plan_id} match (API-2 = original,
     * API-1 = alternate candidate). See {@code AlternateFundValidator}.
     */
    private static void writeAlternateFundSheet(XSSFWorkbook wb, List<TestCaseResult> results, Styles s) {
        Sheet sheet = wb.createSheet("Alternate Fund Validation");
        sheet.createFreezePane(0, 1);

        Row hdr = sheet.createRow(0);
        for (int c = 0; c < ALT_FUND_HEADERS.length; c++) {
            cell(hdr, c, ALT_FUND_HEADERS[c], s.header);
        }

        int rowIdx = 1;
        for (TestCaseResult r : results) {
            for (AlternateFundAudit a : r.getAlternateFundAudits()) {
                writeAuditRow(sheet.createRow(rowIdx++), a, s);
            }
        }

        for (int c = 0; c < ALT_FUND_COL_WIDTHS.length; c++) {
            sheet.setColumnWidth(c, ALT_FUND_COL_WIDTHS[c] * 256);
        }
    }

    // =========================================================================
    // Sheet 4 — Data Gaps
    // =========================================================================

    /**
     * Every {@link AlternateFundAudit} row where a rule or the final result is
     * {@code REQUIRED_DATA_NOT_AVAILABLE} or {@code PAIRING_AMBIGUOUS} — the
     * explicit "what couldn't be determined, and why" report.
     */
    private static void writeDataGapsSheet(XSSFWorkbook wb, List<TestCaseResult> results, Styles s) {
        Sheet sheet = wb.createSheet("Data Gaps");
        sheet.createFreezePane(0, 1);

        Row hdr = sheet.createRow(0);
        for (int c = 0; c < ALT_FUND_HEADERS.length; c++) {
            cell(hdr, c, ALT_FUND_HEADERS[c], s.header);
        }

        int rowIdx = 1;
        for (TestCaseResult r : results) {
            for (AlternateFundAudit a : r.getAlternateFundAudits()) {
                if (a.isDataGap()) writeAuditRow(sheet.createRow(rowIdx++), a, s);
            }
        }

        for (int c = 0; c < ALT_FUND_COL_WIDTHS.length; c++) {
            sheet.setColumnWidth(c, ALT_FUND_COL_WIDTHS[c] * 256);
        }
    }

    private static void writeAuditRow(Row row, AlternateFundAudit a, Styles s) {
        CellStyle resultSt = resolveStatusStyle(a.getFinalResult(), s);

        str(row,  0, a.getTestCaseId(), s.plain);
        str(row,  1, nvl(a.getApi2PlanId()), s.plain);
        str(row,  2, nvl(a.getApi2FundName()), s.plain);
        str(row,  3, nvl(a.getApi1PlanId()), s.plain);
        str(row,  4, nvl(a.getApi1FundName()), s.plain);
        str(row,  5, a.getMatchType() != null ? a.getMatchType().name() : "", s.plain);
        str(row,  6, a.getApi1OpinionTypeId() != null ? a.getApi1OpinionTypeId().toString() : "", s.plain);
        str(row,  7, a.getApi1IsAnalystPick() != null ? a.getApi1IsAnalystPick().toString() : "", s.plain);
        str(row,  8, a.getApi1VrRating() != null ? a.getApi1VrRating().toString() : "", s.plain);
        str(row,  9, nvl(a.getApi1TagName()), s.plain);
        str(row, 10, nvl(a.getApi1CategoryName()), s.plain);
        str(row, 11, ruleCell(a.getRule1()), s.wrap);
        str(row, 12, ruleCell(a.getRule2()), s.wrap);
        str(row, 13, ruleCell(a.getRule3()), s.wrap);
        str(row, 14, ruleCell(a.getRule4()), s.wrap);
        str(row, 15, ruleCell(a.getRule5()), s.wrap);
        str(row, 16, nvl(a.getFailedCondition()), s.wrap);
        str(row, 17, nvl(a.getFinalResult()), resultSt);
    }

    private static String ruleCell(RuleResult r) { return r == null ? "" : r.toString(); }

    // =========================================================================
    // Sheet 5 — Overall Summary
    // =========================================================================

    private static void writeOverallSummarySheet(XSSFWorkbook wb, List<TestCaseResult> results, Styles s) {
        Sheet sheet = wb.createSheet("Overall Summary");

        long totalTC    = results.size();
        long passedTC   = results.stream().filter(r -> "PASS".equals(r.getExecutionStatus())).count();
        long failedTC   = results.stream().filter(r -> "FAIL".equals(r.getExecutionStatus())).count();
        long totalFunds = results.stream().mapToLong(r -> Math.max(0, r.getApi1FundCount())).sum();
        long matched    = results.stream().mapToLong(TestCaseResult::getMatchedFundCount).sum();
        long missing    = results.stream().mapToLong(TestCaseResult::getMissingFundCount).sum();
        long extra      = results.stream().mapToLong(TestCaseResult::getExtraFundCount).sum();
        long mismatches = results.stream().mapToLong(TestCaseResult::getMismatchCount).sum();
        long normDiffs  = results.stream().mapToLong(TestCaseResult::getNormalizationDiffCount).sum();

        List<AlternateFundAudit> allAudits = results.stream()
            .flatMap(r -> r.getAlternateFundAudits().stream()).collect(java.util.stream.Collectors.toList());
        long altMatched   = allAudits.stream().filter(a -> "PASS".equals(a.getFinalResult())).count();
        long altRejected  = allAudits.stream().filter(a -> "FAIL".equals(a.getFinalResult())).count();
        long altAmbiguous = allAudits.stream()
            .filter(a -> a.getMatchType() == com.vr.portfolioplanner.altfund.MatchType.PAIRING_AMBIGUOUS).count();
        long altDataGaps  = allAudits.stream().filter(AlternateFundAudit::isDataGap).count();

        // ── Requests/second throughput (bucketed by second across API-1 + API-2 timestamps) ──
        Map<String, Integer> requestsPerSecond = new TreeMap<>();
        for (TestCaseResult r : results) {
            bucketBySecond(requestsPerSecond, r.getApi1RequestTimestamp());
            bucketBySecond(requestsPerSecond, r.getApi2RequestTimestamp());
        }
        long totalRequests = requestsPerSecond.values().stream().mapToLong(Integer::longValue).sum();
        int  peakReqPerSec = requestsPerSecond.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double avgReqPerSec = requestsPerSecond.isEmpty()
            ? 0.0 : (double) totalRequests / requestsPerSecond.size();

        String[][] data = {
            {"Metric", "Value"},
            {"Total Test Cases",           String.valueOf(totalTC)},
            {"Passed Test Cases",          String.valueOf(passedTC)},
            {"Failed Test Cases",          String.valueOf(failedTC)},
            {"Total Funds Compared (API-1)", String.valueOf(totalFunds)},
            {"Total Matched Funds",        String.valueOf(matched)},
            {"Total Missing Funds",        String.valueOf(missing)},
            {"Total Extra Funds",          String.valueOf(extra)},
            {"Total Field Mismatches",     String.valueOf(mismatches)},
            {"Total Normalization Diffs",  String.valueOf(normDiffs)},
            {"Total Alternate Matches",    String.valueOf(altMatched)},
            {"Total Alternate Rejected",   String.valueOf(altRejected)},
            {"Total Pairing Ambiguous",    String.valueOf(altAmbiguous)},
            {"Total Data Gaps",            String.valueOf(altDataGaps)},
            {"Total API Requests Fired",   String.valueOf(totalRequests)},
            {"Peak Requests/Second",       String.valueOf(peakReqPerSec)},
            {"Average Requests/Second",    String.format("%.2f", avgReqPerSec)},
        };

        Row hdr = sheet.createRow(0);
        cell(hdr, 0, "Metric", s.header);
        cell(hdr, 1, "Value",  s.header);

        for (int i = 1; i < data.length; i++) {
            Row row = sheet.createRow(i);
            str(row, 0, data[i][0], s.plain);
            str(row, 1, data[i][1], s.plain);
        }

        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 20 * 256);
    }

    /**
     * Increments the per-second bucket for a "yyyy-MM-dd HH:mm:ss.SSS" request
     * timestamp, truncated to whole seconds. Blank/null timestamps (rows that
     * errored before a request was dispatched) are ignored.
     */
    private static void bucketBySecond(Map<String, Integer> buckets, String timestamp) {
        if (timestamp == null || timestamp.length() < 19) return;
        String secondKey = timestamp.substring(0, 19); // "yyyy-MM-dd HH:mm:ss"
        buckets.merge(secondKey, 1, Integer::sum);
    }

    // =========================================================================
    // Mismatch detail formatter (public — used by ApiComparisonTest for legacy cell)
    // =========================================================================

    public static String formatMismatchDetails(ComparisonResult result) {
        if (result == null) return "";
        List<Mismatch> failing = result.getFailingMismatches();
        if (failing.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < failing.size(); i++) {
            Mismatch m = failing.get(i);
            if (i > 0) sb.append("\n\n");
            sb.append("Mismatch ").append(i + 1).append(":\n");
            if (m.getPlanId()       != null) sb.append("Plan ID: ").append(m.getPlanId()).append("\n");
            if (m.getApi1FundName() != null) sb.append("API-1 Fund: ").append(m.getApi1FundName()).append("\n");
            if (m.getApi2FundName() != null) sb.append("API-2 Fund: ").append(m.getApi2FundName()).append("\n");
            sb.append("Path: ").append(m.getFieldPath()).append("\n");
            sb.append("API-1: ").append(m.getApi1Value()).append("\n");
            sb.append("API-2: ").append(m.getApi2Value()).append("\n");
            if (m.getDifference() != null) sb.append("Diff: ").append(m.getDifference()).append("\n");
            sb.append("Type: ").append(m.getMismatchType());
        }
        return sb.toString();
    }

    // =========================================================================
    // Cell helpers
    // =========================================================================

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        c.setCellStyle(style);
    }

    private static void str(Row row, int col, String value, CellStyle style) {
        cell(row, col, value, style);
    }

    private static void num(Row row, int col, long value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void fundNum(Row row, int col, int count, Styles s) {
        if (count < 0) {
            str(row, col, "N/A", s.plain);
        } else {
            num(row, col, count, s.numeric);
        }
    }

    private static CellStyle resolveStatusStyle(String value, Styles s) {
        if ("PASS".equalsIgnoreCase(value)) return s.pass;
        if ("FAIL".equalsIgnoreCase(value)) return s.fail;
        if ("ERROR".equalsIgnoreCase(value)) return s.warn;
        if ("REQUIRED_DATA_NOT_AVAILABLE".equalsIgnoreCase(value)) return s.warn;
        if ("PAIRING_AMBIGUOUS".equalsIgnoreCase(value)) return s.warn;
        return s.plain;
    }

    private static String nvl(String v) { return v != null ? v : ""; }

    // =========================================================================
    // Styles
    // =========================================================================

    private static final class Styles {
        final CellStyle header, plain, numeric, wrap, pass, fail, warn;

        Styles(XSSFWorkbook wb) {
            header  = buildHeader(wb);
            plain   = buildPlain(wb);
            numeric = buildNumeric(wb);
            wrap    = buildWrap(wb);
            pass    = buildColoured(wb, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN);
            fail    = buildColoured(wb, IndexedColors.ROSE,        IndexedColors.DARK_RED);
            warn    = buildColoured(wb, IndexedColors.LIGHT_YELLOW, IndexedColors.ORANGE);
        }

        private static CellStyle buildHeader(XSSFWorkbook wb) {
            Font f = wb.createFont(); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex());
            CellStyle s = wb.createCellStyle(); s.setFont(f);
            s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            thin(s); return s;
        }

        private static CellStyle buildPlain(XSSFWorkbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setVerticalAlignment(VerticalAlignment.CENTER); thin(s); return s;
        }

        private static CellStyle buildNumeric(XSSFWorkbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setDataFormat(wb.createDataFormat().getFormat("0"));
            s.setVerticalAlignment(VerticalAlignment.CENTER); thin(s); return s;
        }

        private static CellStyle buildWrap(XSSFWorkbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setWrapText(true); s.setVerticalAlignment(VerticalAlignment.TOP); thin(s); return s;
        }

        private static CellStyle buildColoured(XSSFWorkbook wb, IndexedColors bg, IndexedColors fg) {
            Font f = wb.createFont(); f.setBold(true); f.setColor(fg.getIndex());
            CellStyle s = wb.createCellStyle(); s.setFont(f);
            s.setFillForegroundColor(bg.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setVerticalAlignment(VerticalAlignment.CENTER); thin(s); return s;
        }

        private static void thin(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN); s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN);
        }
    }
}
