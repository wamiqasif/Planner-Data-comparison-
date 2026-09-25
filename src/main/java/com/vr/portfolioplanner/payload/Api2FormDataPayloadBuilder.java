package com.vr.portfolioplanner.payload;

import com.vr.portfolioplanner.model.TestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the multipart/form-data field map for API 2 – Investment Plan.
 *
 * <p>Field order matches the API-2 contract specification:
 * <pre>
 *   annual_income_range, duration_type, goal_type, investment_duration,
 *   investor_id, label_id, monthly_amount, next_financial_year,
 *   risk_profile_id, txn_options, user_id, lumpsum_amount
 * </pre>
 *
 * <p>REGULAR-INCOME rows additionally send {@code needed_annual_amount} and
 * {@code start_regular_income} alongside {@code accumulated_amount}
 * (see {@link Api1JsonPayloadBuilder#usesSpecialAmount}).
 *
 * <p>ID mapping (Excel column → form field name):
 * <ul>
 *   <li>{@code api2_label_id}             → {@code label_id}</li>
 *   <li>{@code api2_user_id}              → {@code user_id}</li>
 *   <li>{@code api2_investor_id}          → {@code investor_id}</li>
 *   <li>{@code api2_next_financial_year}  → {@code next_financial_year}</li>
 * </ul>
 *
 * <p>All numeric values are converted to their integer string representation
 * (e.g. {@code 50000L} → {@code "50000"}), matching what the API expects
 * in a form-data submission.
 *
 * <p>No values are hardcoded. All values come from the supplied {@link TestData}.
 * The returned map is unmodifiable.
 */
public final class Api2FormDataPayloadBuilder {

    private static final Logger log = LoggerFactory.getLogger(Api2FormDataPayloadBuilder.class);

    private Api2FormDataPayloadBuilder() {}

    /**
     * Builds the form-field map for an API 2 multipart POST request.
     * Each map entry becomes one {@code multiPart(key, value)} call in REST Assured.
     *
     * @param td a validated {@link TestData} row (Execute = Y)
     * @return an unmodifiable, ordered map of form-field names to string values
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public static Map<String, String> build(TestData td) {
        validate(td);

        // LinkedHashMap preserves insertion order — the order REST Assured
        // will attach parts to the multipart body.
        Map<String, String> fields = new LinkedHashMap<>();

        // ---- Common fields (shared with API 1) ----
        fields.put("annual_income_range", String.valueOf(td.getAnnualIncomeRange()));
        fields.put("duration_type",       td.getDurationType());
        fields.put("goal_type",           td.getGoalType());
        fields.put("investment_duration", String.valueOf(td.getInvestmentDuration()));

        // ---- API 2 – specific IDs (from api2_* Excel columns) ----
        fields.put("investor_id",         String.valueOf(td.getApi2InvestorId()));
        fields.put("label_id",            String.valueOf(td.getApi2LabelId()));

        // ---- Common fields continued ----
        boolean special = Api1JsonPayloadBuilder.usesSpecialAmount(td.getGoalType());
        if (!special) {
            fields.put("monthly_amount",  String.valueOf(td.getMonthlyAmount()));
        }

        // ---- API 2 – specific field ----
        fields.put("next_financial_year", String.valueOf(td.getApi2NextFinancialYear()));

        // ---- Common fields continued ----
        fields.put("risk_profile_id",     String.valueOf(td.getRiskProfileId()));
        if (special) {
            if ("REGULAR-INCOME".equalsIgnoreCase(td.getGoalType())) {
                fields.put("accumulated_amount", String.valueOf(td.getAccumulatedAmount()));
                fields.put("needed_annual_amount", String.valueOf(td.getNeededAnnualAmount()));
                fields.put("start_regular_income", String.valueOf(td.getStartRegularIncome()));
            } else {
                fields.put("tax_saving_amount",  String.valueOf(td.getTaxSavingAmount()));
            }
        } else {
            fields.put("txn_options",     td.getTxnOptions());
        }

        // ---- API 2 – specific ID continued ----
        fields.put("user_id",             String.valueOf(td.getApi2UserId()));

        // ---- Common field ----
        if (!special) {
            fields.put("lumpsum_amount",  String.valueOf(td.getLumpsumAmount()));
        }

        Map<String, String> unmodifiable = Collections.unmodifiableMap(fields);
        log.debug("API-2 form-data built for {} ({} fields)", td.getTestCaseId(), fields.size());
        return unmodifiable;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private static void validate(TestData td) {
        if (td == null) {
            throw new IllegalArgumentException("TestData must not be null");
        }
        // String fields always required
        requireNonBlank(td.getGoalType(),    "goal_type");
        requireNonBlank(td.getDurationType(),"duration_type");
        // Non-amount numeric fields always required
        requirePositive(td.getInvestmentDuration(), "investment_duration");
        requirePositive(td.getRiskProfileId(),        "risk_profile_id");
        requirePositive(td.getAnnualIncomeRange(),    "annual_income_range");
        // Amount validation: REGULAR-INCOME/TAX-SAVINGS use accumulated_amount /
        // tax_saving_amount instead of txn_options + monthly/lumpsum amount — delegates
        // to the shared helpers in Api1JsonPayloadBuilder.
        if (Api1JsonPayloadBuilder.usesSpecialAmount(td.getGoalType())) {
            Api1JsonPayloadBuilder.validateSpecialAmount(
                td.getGoalType(), td.getAccumulatedAmount(), td.getTaxSavingAmount(),
                td.getNeededAnnualAmount(), td.getStartRegularIncome());
        } else {
            requireNonBlank(td.getTxnOptions(), "txn_options");
            Api1JsonPayloadBuilder.validateAmounts(
                td.getTxnOptions(), td.getMonthlyAmount(), td.getLumpsumAmount());
        }
        // API-2 specific IDs always required
        requirePositive(td.getApi2LabelId(),           "api2_label_id");
        requirePositive(td.getApi2UserId(),            "api2_user_id");
        requirePositive(td.getApi2InvestorId(),        "api2_investor_id");
        requirePositive(td.getApi2NextFinancialYear(), "api2_next_financial_year");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "API-2 payload: required field '" + field + "' is blank or null");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                "API-2 payload: field '" + field + "' must be > 0, got: " + value);
        }
    }
}
