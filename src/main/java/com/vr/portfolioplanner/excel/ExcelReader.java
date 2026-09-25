package com.vr.portfolioplanner.excel;

import com.vr.portfolioplanner.model.TestData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads {@link TestData} rows from an Excel (.xlsx) file using Apache POI.
 *
 * <p>Usage:
 * <pre>
 *   // From classpath (typical in tests after Maven copies resources):
 *   ExcelReader reader = ExcelReader.fromClasspath("testdata/PortfolioPlanner_TestData.xlsx");
 *
 *   // From filesystem (useful in fixture bootstrap):
 *   ExcelReader reader = ExcelReader.fromFile(Paths.get("src/test/resources/testdata/...xlsx"));
 *
 *   List&lt;TestData&gt; rows      = reader.getAllRows();          // all rows
 *   List&lt;TestData&gt; active    = reader.getExecutableRows();   // Execute=Y only
 *   Object[][]     provider  = reader.asDataProvider();      // TestNG-ready
 * </pre>
 *
 * <p>Numeric-cell contract: whole-number values stored as Excel NUMERIC
 * (e.g. 50000.0) are returned as their integer string ("50000"), never "50000.0".
 */
public final class ExcelReader {

    private static final Logger log = LoggerFactory.getLogger(ExcelReader.class);

    /** All columns that must be present in the header row. */
    private static final List<String> REQUIRED_COLUMNS = List.of(
        "TestCaseID", "Execute", "Description",
        "goal_type", "txn_options", "duration_type", "investment_duration",
        "monthly_amount", "lumpsum_amount", "accumulated_amount", "tax_saving_amount",
        "needed_annual_amount", "start_regular_income",
        "risk_profile_id", "annual_income_range",
        "api1_label_id", "api1_user_id", "api1_aware_type", "api1_investor_id",
        "api2_label_id", "api2_user_id", "api2_investor_id", "api2_next_financial_year"
    );

    private final List<TestData> rows;

    // ------------------------------------------------------------------
    // Factory methods
    // ------------------------------------------------------------------

    /**
     * Loads from a classpath resource.
     * The path is relative to the classpath root, e.g.
     * {@code "testdata/PortfolioPlanner_TestData.xlsx"}.
     */
    public static ExcelReader fromClasspath(String resourcePath) throws IOException {
        InputStream is = ExcelReader.class.getClassLoader()
                .getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IllegalArgumentException(
                "Classpath resource not found: '" + resourcePath + "'. "
                + "Ensure the file exists in src/test/resources/ and Maven has copied it.");
        }
        try (InputStream stream = is) {
            return new ExcelReader(stream, resourcePath);
        }
    }

    /**
     * Loads from an absolute or project-relative filesystem path.
     */
    public static ExcelReader fromFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException(
                "Excel file not found: " + filePath.toAbsolutePath());
        }
        try (InputStream is = Files.newInputStream(filePath)) {
            return new ExcelReader(is, filePath.toString());
        }
    }

    // ------------------------------------------------------------------
    // Constructor (private — use factory methods)
    // ------------------------------------------------------------------

    private ExcelReader(InputStream inputStream, String sourceName) throws IOException {
        this.rows = parse(inputStream, sourceName);
    }

    // ------------------------------------------------------------------
    // Public accessors
    // ------------------------------------------------------------------

    /** All data rows (regardless of Execute flag). Never null; may be empty. */
    public List<TestData> getAllRows() {
        return rows;
    }

    /** Rows where Execute column equals "Y" (case-insensitive). */
    public List<TestData> getExecutableRows() {
        return rows.stream()
                   .filter(TestData::isExecutable)
                   .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns a TestNG {@code @DataProvider}-compatible array.
     * Each element is {@code Object[] { TestData }}.
     * Only Execute=Y rows are included.
     */
    public Object[][] asDataProvider() {
        return getExecutableRows().stream()
                                  .map(td -> new Object[]{td})
                                  .toArray(Object[][]::new);
    }

    /** Row count (all rows, not just Execute=Y). */
    public int totalRowCount() {
        return rows.size();
    }

    /** Row count of Execute=Y rows. */
    public int executableRowCount() {
        return (int) rows.stream().filter(TestData::isExecutable).count();
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    private List<TestData> parse(InputStream inputStream, String sourceName)
            throws IOException {

        List<TestData> result = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalStateException(
                    "Workbook '" + sourceName + "' has no sheets.");
            }
            log.info("Reading sheet '{}' from '{}'", sheet.getSheetName(), sourceName);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalStateException(
                    "Sheet '" + sheet.getSheetName() + "' has no header row (row 0).");
            }

            Map<String, Integer> colIndex = buildColumnIndex(headerRow);
            validateRequiredColumns(colIndex, sourceName);

            int lastRow = sheet.getLastRowNum();
            int skippedBlank = 0;

            for (int rowNum = 1; rowNum <= lastRow; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isRowBlank(row, colIndex)) {
                    skippedBlank++;
                    continue;
                }
                result.add(buildTestData(row, colIndex, rowNum));
            }

            log.info("Parsed {} data row(s); {} blank row(s) skipped; {} executable",
                result.size(), skippedBlank,
                result.stream().filter(TestData::isExecutable).count());
        }

        return Collections.unmodifiableList(result);
    }

    // ------------------------------------------------------------------
    // Column index
    // ------------------------------------------------------------------

    private Map<String, Integer> buildColumnIndex(Row headerRow) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String name = cellToString(cell).trim();
            if (!name.isEmpty()) {
                index.put(name, cell.getColumnIndex());
            }
        }
        log.debug("Header columns found: {}", index.keySet());
        return index;
    }

    private void validateRequiredColumns(Map<String, Integer> colIndex, String source) {
        List<String> missing = REQUIRED_COLUMNS.stream()
            .filter(col -> !colIndex.containsKey(col))
            .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Excel file '" + source + "' is missing required column(s): " + missing
                + ". Found: " + colIndex.keySet());
        }
    }

    // ------------------------------------------------------------------
    // Row → TestData
    // ------------------------------------------------------------------

    private TestData buildTestData(Row row, Map<String, Integer> idx, int rowNum) {
        TestData td = new TestData();
        td.setTestCaseId(        str(row, idx, "TestCaseID",              rowNum));
        td.setExecute(           str(row, idx, "Execute",                 rowNum));
        td.setDescription(       str(row, idx, "Description",             rowNum));
        td.setGoalType(          str(row, idx, "goal_type",               rowNum));
        td.setTxnOptions(        str(row, idx, "txn_options",             rowNum));
        td.setDurationType(      str(row, idx, "duration_type",           rowNum));
        td.setInvestmentDuration(getInt(row, idx, "investment_duration",  rowNum));
        td.setMonthlyAmount(     getLong(row, idx, "monthly_amount",      rowNum));
        td.setLumpsumAmount(     getLong(row, idx, "lumpsum_amount",      rowNum));
        td.setAccumulatedAmount( getLong(row, idx, "accumulated_amount", rowNum));
        td.setTaxSavingAmount(   getLong(row, idx, "tax_saving_amount",  rowNum));
        td.setNeededAnnualAmount(getLong(row, idx, "needed_annual_amount", rowNum));
        td.setStartRegularIncome(getLong(row, idx, "start_regular_income", rowNum));
        td.setRiskProfileId(     getInt(row, idx, "risk_profile_id",      rowNum));
        td.setAnnualIncomeRange( getInt(row, idx, "annual_income_range",  rowNum));
        td.setApi1LabelId(       getLong(row, idx, "api1_label_id",       rowNum));
        td.setApi1UserId(        getLong(row, idx, "api1_user_id",        rowNum));
        td.setApi1AwareType(     str(row, idx, "api1_aware_type",         rowNum));
        td.setApi1InvestorId(    getLong(row, idx, "api1_investor_id",    rowNum));
        td.setApi2LabelId(       getLong(row, idx, "api2_label_id",       rowNum));
        td.setApi2UserId(        getLong(row, idx, "api2_user_id",        rowNum));
        td.setApi2InvestorId(    getLong(row, idx, "api2_investor_id",    rowNum));
        td.setApi2NextFinancialYear(getInt(row, idx, "api2_next_financial_year", rowNum));
        return td;
    }

    // ------------------------------------------------------------------
    // Typed cell helpers
    // ------------------------------------------------------------------

    private String str(Row row, Map<String, Integer> idx, String col, int rowNum) {
        return cellToString(cell(row, idx, col));
    }

    private long getLong(Row row, Map<String, Integer> idx, String col, int rowNum) {
        String val = str(row, idx, col, rowNum);
        if (val.isEmpty()) return 0L;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Column '" + col + "' at Excel row " + (rowNum + 1)
                + " must be a whole number, got: '" + val + "'");
        }
    }

    private int getInt(Row row, Map<String, Integer> idx, String col, int rowNum) {
        long v = getLong(row, idx, col, rowNum);
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
            throw new IllegalArgumentException(
                "Column '" + col + "' value " + v + " overflows int at row " + (rowNum + 1));
        }
        return (int) v;
    }

    // ------------------------------------------------------------------
    // Low-level cell access
    // ------------------------------------------------------------------

    private Cell cell(Row row, Map<String, Integer> idx, String col) {
        Integer colIdx = idx.get(col);
        if (colIdx == null) return null;
        return row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private boolean isRowBlank(Row row, Map<String, Integer> idx) {
        for (int colIdx : idx.values()) {
            Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && !cellToString(cell).isEmpty()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Cell → String (numeric cells preserve integer representation)
    // ------------------------------------------------------------------

    static String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> formatDouble(cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case FORMULA -> formulaResult(cell);
            case BLANK   -> "";
            default      -> "";
        };
    }

    private static String formulaResult(Cell cell) {
        return switch (cell.getCachedFormulaResultType()) {
            case NUMERIC -> formatDouble(cell.getNumericCellValue());
            case STRING  -> cell.getStringCellValue().trim();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    /**
     * Returns integer string for whole-number doubles (e.g. 50000.0 → "50000"),
     * full decimal string otherwise. Guards against very large doubles where
     * (long) cast would lose precision (>1e15).
     */
    static String formatDouble(double d) {
        if (!Double.isInfinite(d) && !Double.isNaN(d)
                && d == Math.floor(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }
}
