package com.vr.portfolioplanner.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates the canonical test-data Excel fixture at
 * {@code src/test/resources/testdata/PortfolioPlanner_TestData.xlsx}.
 *
 * Call {@link #generateIfAbsent()} in a {@code @BeforeClass} to ensure the
 * file exists before tests read from it. Subsequent test runs will reuse the
 * existing file (QA engineers can modify it by hand without losing changes).
 *
 * To force regeneration (e.g. when new columns are added), delete the file
 * and re-run, or call {@link #generate(Path)} directly.
 */
public final class ExcelFixtureBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ExcelFixtureBootstrap.class);

    /** Relative path from the Maven project root. */
    private static final String RELATIVE_PATH =
        "src/test/resources/testdata/PortfolioPlanner_TestData.xlsx";

    // Column names in declaration order — must match ExcelReader.REQUIRED_COLUMNS
    private static final String[] HEADERS = {
        "TestCaseID", "Execute", "Description",
        "goal_type", "txn_options", "duration_type", "investment_duration",
        "monthly_amount", "lumpsum_amount", "accumulated_amount", "tax_saving_amount",
        "needed_annual_amount", "start_regular_income",
        "risk_profile_id", "annual_income_range",
        "api1_label_id", "api1_user_id", "api1_aware_type", "api1_investor_id",
        "api2_label_id", "api2_user_id", "api2_investor_id", "api2_next_financial_year"
    };

    // -----------------------------------------------------------------------
    // Static API-specific IDs (same for all scenarios per ODS)
    // -----------------------------------------------------------------------
    private static final long API1_LABEL_ID    = 89660240L;
    private static final long API1_USER_ID     = 123L;
    private static final long API1_INVESTOR_ID = 87683240L;
    private static final long API2_LABEL_ID    = 90222276L;
    private static final long API2_USER_ID     = 52264629L;
    private static final long API2_INVESTOR_ID = 88244645L;
    private static final long API2_NEXT_FY     = 2028L;

    // -----------------------------------------------------------------------
    // All test scenarios — derived from ODS parameter space (Step 2)
    // Columns: TestCaseID, Execute, Description,
    //          goal_type, txn_options, duration_type, investment_duration,
    //          monthly_amount, lumpsum_amount, accumulated_amount, tax_saving_amount,
    //          needed_annual_amount, start_regular_income,
    //          risk_profile_id, annual_income_range,
    //          api1_label_id, api1_user_id, api1_aware_type, api1_investor_id,
    //          api2_label_id, api2_user_id, api2_investor_id, api2_next_financial_year
    //
    // REGULAR-INCOME / TAX-SAVINGS rows leave txn_options blank ("") and
    // monthly_amount/lumpsum_amount at 0 — they use accumulated_amount /
    // tax_saving_amount instead (see Api1JsonPayloadBuilder.usesSpecialAmount).
    // needed_annual_amount and start_regular_income are only populated for
    // REGULAR-INCOME rows; every other goal_type leaves them at 0.
    // -----------------------------------------------------------------------
    private static final Object[][] ALL_ROWS = {
        { "TC_001", "Y", "GROWTH + SIP+Lumpsum, 15yr, risk=2",
          "GROWTH", "SIPLUMPSUM", "y", 15L, 50000L, 10000000L, 0L, 0L, 0L, 0L, 2L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },

        { "TC_002", "Y", "GROWTH + SIP only, 15yr, risk=2",
          "GROWTH", "SIP", "y", 15L, 50000L, 0L, 0L, 0L, 0L, 0L, 2L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },

        { "TC_003", "Y", "GROWTH + Lumpsum only, 15yr, risk=2",
          "GROWTH", "LUMPSUM", "y", 15L, 0L, 10000000L, 0L, 0L, 0L, 0L, 2L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },

        { "TC_004", "Y", "REGULAR-INCOME + accumulated_amount, 10yr, risk=2",
          "REGULAR-INCOME", "", "y", 10L, 0L, 0L, 100000L, 0L, 500000L, 2028L, 2L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },

        { "TC_005", "Y", "TAX-SAVINGS + tax_saving_amount, 5yr, risk=2",
          "TAX-SAVINGS", "", "y", 5L, 0L, 0L, 0L, 500L, 0L, 0L, 2L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },

        { "TC_006", "Y", "GROWTH + SIP+Lumpsum, 60 months, risk=2",
          "GROWTH", "SIPLUMPSUM", "m", 60L, 50000L, 10000000L, 0L, 0L, 0L, 0L, 2L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },

        { "TC_007", "Y", "GROWTH + SIP+Lumpsum, 15yr, risk=1",
          "GROWTH", "SIPLUMPSUM", "y", 15L, 50000L, 10000000L, 0L, 0L, 0L, 0L, 1L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },

        { "TC_008", "Y", "GROWTH + SIP+Lumpsum, 15yr, risk=3",
          "GROWTH", "SIPLUMPSUM", "y", 15L, 50000L, 10000000L, 0L, 0L, 0L, 0L, 3L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },

        { "TC_009", "Y", "GROWTH + SIP+Lumpsum, 1yr (min), risk=2",
          "GROWTH", "SIPLUMPSUM", "y", 1L, 50000L, 10000000L, 0L, 0L, 0L, 0L, 2L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },

        { "TC_010", "Y", "GROWTH + SIP+Lumpsum, 30yr (max), risk=2",
          "GROWTH", "SIPLUMPSUM", "y", 30L, 50000L, 10000000L, 0L, 0L, 0L, 0L, 2L, 2L,
          API1_LABEL_ID, API1_USER_ID, "FRESH", API1_INVESTOR_ID,
          API2_LABEL_ID, API2_USER_ID, API2_INVESTOR_ID, API2_NEXT_FY },
    };

    private ExcelFixtureBootstrap() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the canonical fixture path (project-root-relative).
     * Maven runs tests with the project root as the working directory.
     */
    public static Path getFixturePath() {
        return resolveProjectRoot().resolve(RELATIVE_PATH);
    }

    /**
     * Uses the existing fixture if present (source-of-truth Excel provided externally),
     * or generates a minimal bootstrap fixture if none exists.
     *
     * The provided 6,750-row Excel must never be overwritten by the bootstrap generator.
     */
    public static void generateIfAbsent() throws IOException {
        Path target = getFixturePath();
        if (Files.exists(target)) {
            log.info("Using existing test data fixture: {} ({} bytes)",
                target, Files.size(target));
        } else {
            log.info("No fixture found — generating bootstrap fixture at {}", target);
            generate(target);
        }
    }

    /**
     * Unconditionally writes (or overwrites) the fixture at {@code outputPath}.
     */
    public static void generate(Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());

        try (Workbook wb = buildWorkbook();
             OutputStream os = Files.newOutputStream(outputPath)) {
            wb.write(os);
        }

        log.info("Excel fixture written: {}", outputPath.toAbsolutePath());
    }

    // -----------------------------------------------------------------------
    // Workbook construction
    // -----------------------------------------------------------------------

    private static Workbook buildWorkbook() {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("TestData");

        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle dataStyle   = createDataStyle(wb);

        writeHeaderRow(sheet, headerStyle);
        for (int i = 0; i < ALL_ROWS.length; i++) {
            writeDataRow(sheet, i + 1, ALL_ROWS[i], dataStyle);
        }

        // Freeze header row and auto-size all columns
        sheet.createFreezePane(0, 1);
        for (int col = 0; col < HEADERS.length; col++) {
            sheet.autoSizeColumn(col);
            // Add a small padding so text isn't clipped
            sheet.setColumnWidth(col, sheet.getColumnWidth(col) + 512);
        }

        return wb;
    }

    private static void writeHeaderRow(Sheet sheet, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int col = 0; col < HEADERS.length; col++) {
            Cell cell = row.createCell(col);
            cell.setCellValue(HEADERS[col]);
            cell.setCellStyle(style);
        }
    }

    private static void writeDataRow(Sheet sheet, int rowNum, Object[] data,
                                     CellStyle style) {
        Row row = sheet.createRow(rowNum);
        for (int col = 0; col < data.length; col++) {
            Cell cell = row.createCell(col);
            cell.setCellStyle(style);
            Object value = data[col];
            if (value instanceof Long l)        cell.setCellValue(l.doubleValue());
            else if (value instanceof Integer i) cell.setCellValue(i.doubleValue());
            else if (value instanceof Double d)  cell.setCellValue(d);
            else if (value instanceof Boolean b) cell.setCellValue(b);
            else                                 cell.setCellValue(String.valueOf(value));
        }
    }

    // -----------------------------------------------------------------------
    // Styles
    // -----------------------------------------------------------------------

    private static CellStyle createHeaderStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        // Suppress decimal places for numeric cells in the sheet view
        style.setDataFormat(wb.createDataFormat().getFormat("0"));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // -----------------------------------------------------------------------
    // Project-root detection
    // -----------------------------------------------------------------------

    /**
     * Resolves the Maven project root by walking up from the current working
     * directory until a directory containing {@code pom.xml} is found.
     * Maven sets the working directory to the project root during test execution,
     * so this usually resolves immediately.
     */
    private static Path resolveProjectRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path cursor = cwd;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        log.warn("Could not find pom.xml above '{}'; defaulting to cwd", cwd);
        return cwd;
    }
}
