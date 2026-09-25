package com.vr.portfolioplanner.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vr.portfolioplanner.model.TestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the JSON request body for API 1 – Portfolio Planner.
 *
 * <p>Field order matches the API-1 contract specification:
 * <pre>
 *   goal_type, txn_options, duration_type, investment_duration,
 *   monthly_amount, lumpsum_amount, label_id, user_id,
 *   risk_profile_id, annual_income_range, aware_type, investor_id
 * </pre>
 *
 * <p>ID mapping (Excel column → JSON key):
 * <ul>
 *   <li>{@code api1_label_id}    → {@code label_id}</li>
 *   <li>{@code api1_user_id}     → {@code user_id}</li>
 *   <li>{@code api1_aware_type}  → {@code aware_type}</li>
 *   <li>{@code api1_investor_id} → {@code investor_id}</li>
 * </ul>
 *
 * <p>No values are hardcoded. All values come from the supplied {@link TestData}.
 *
 * <h3>REGULAR-INCOME / TAX-SAVINGS goal types</h3>
 * <p>These goal types do not use {@code txn_options} / {@code monthly_amount} /
 * {@code lumpsum_amount} — they send {@code accumulated_amount} or
 * {@code tax_saving_amount} instead. See {@link #usesSpecialAmount(String)}.
 * REGULAR-INCOME rows additionally send {@code needed_annual_amount} and
 * {@code start_regular_income}.
 */
public final class Api1JsonPayloadBuilder {

    private static final Logger log = LoggerFactory.getLogger(Api1JsonPayloadBuilder.class);

    private static final String GOAL_REGULAR_INCOME = "REGULAR-INCOME";
    private static final String GOAL_TAX_SAVINGS    = "TAX-SAVINGS";

    // Shared, thread-safe instance — ObjectMapper is safe for concurrent use.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Api1JsonPayloadBuilder() {}

    /**
     * Builds the JSON body for an API 1 POST request.
     *
     * @param td a validated {@link TestData} row (Execute = Y)
     * @return a compact JSON string ready to be sent as the request body
     * @throws IllegalArgumentException if required fields are missing or invalid
     * @throws IllegalStateException    if Jackson serialisation fails
     *                                  (should never happen with primitive/string values)
     */
    public static String build(TestData td) {
        validate(td);

        // LinkedHashMap preserves insertion order → stable JSON field order
        Map<String, Object> payload = new LinkedHashMap<>();

        // ---- Common fields (shared with API 2) ----
        payload.put("goal_type",           td.getGoalType());
        if (usesSpecialAmount(td.getGoalType())) {
            if (GOAL_REGULAR_INCOME.equalsIgnoreCase(td.getGoalType())) {
                payload.put("accumulated_amount", td.getAccumulatedAmount());
                payload.put("needed_annual_amount", td.getNeededAnnualAmount());
                payload.put("start_regular_income", td.getStartRegularIncome());
            } else {
                payload.put("tax_saving_amount", td.getTaxSavingAmount());
            }
        } else {
            payload.put("txn_options",     td.getTxnOptions());
        }
        payload.put("duration_type",       td.getDurationType());
        payload.put("investment_duration", td.getInvestmentDuration());
        if (!usesSpecialAmount(td.getGoalType())) {
            payload.put("monthly_amount",  td.getMonthlyAmount());
            payload.put("lumpsum_amount",  td.getLumpsumAmount());
        }

        // ---- API 1 – specific IDs (from api1_* Excel columns) ----
        payload.put("label_id",            td.getApi1LabelId());
        payload.put("user_id",             td.getApi1UserId());

        // ---- Common fields continued ----
        payload.put("risk_profile_id",     td.getRiskProfileId());
        payload.put("annual_income_range", td.getAnnualIncomeRange());

        // ---- API 1 – specific fields continued ----
        payload.put("aware_type",          td.getApi1AwareType());
        payload.put("investor_id",         td.getApi1InvestorId());

        try {
            String json = MAPPER.writeValueAsString(payload);
            log.debug("API-1 payload built for {} ({} bytes)", td.getTestCaseId(), json.length());
            return json;
        } catch (JsonProcessingException e) {
            // Cannot happen: map contains only String, int, long — all serialisable.
            throw new IllegalStateException(
                "Unexpected serialisation failure for test case " + td.getTestCaseId(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private static void validate(TestData td) {
        if (td == null) {
            throw new IllegalArgumentException("TestData must not be null");
        }
        // String fields always required
        requireNonBlank(td.getGoalType(),     "goal_type");
        requireNonBlank(td.getDurationType(), "duration_type");
        requireNonBlank(td.getApi1AwareType(),"api1_aware_type");
        // Non-amount numeric fields always required
        requirePositive(td.getInvestmentDuration(), "investment_duration");
        requirePositive(td.getRiskProfileId(),        "risk_profile_id");
        requirePositive(td.getAnnualIncomeRange(),    "annual_income_range");
        // Amount validation: REGULAR-INCOME/TAX-SAVINGS use accumulated_amount /
        // tax_saving_amount instead of txn_options + monthly/lumpsum amount (ODS rule)
        if (usesSpecialAmount(td.getGoalType())) {
            validateSpecialAmount(td.getGoalType(), td.getAccumulatedAmount(), td.getTaxSavingAmount(),
                td.getNeededAnnualAmount(), td.getStartRegularIncome());
        } else {
            requireNonBlank(td.getTxnOptions(), "txn_options");
            validateAmounts(td.getTxnOptions(), td.getMonthlyAmount(), td.getLumpsumAmount());
        }
        // API-1 specific IDs always required
        requirePositive(td.getApi1LabelId(),    "api1_label_id");
        requirePositive(td.getApi1UserId(),     "api1_user_id");
        requirePositive(td.getApi1InvestorId(), "api1_investor_id");
    }

    /**
     * Validates amounts conditionally based on transaction type:
     * <ul>
     *   <li>SIP         — monthly_amount required; lumpsum_amount may be 0</li>
     *   <li>LUMPSUM     — lumpsum_amount required; monthly_amount may be 0</li>
     *   <li>SIPLUMPSUM  — both required</li>
     * </ul>
     */
    static void validateAmounts(String txnOptions, long monthlyAmount, long lumpsumAmount) {
        boolean needMonthly = "SIP".equalsIgnoreCase(txnOptions)
                           || "SIPLUMPSUM".equalsIgnoreCase(txnOptions);
        boolean needLumpsum = "LUMPSUM".equalsIgnoreCase(txnOptions)
                           || "SIPLUMPSUM".equalsIgnoreCase(txnOptions);
        if (needMonthly && monthlyAmount <= 0) {
            throw new IllegalArgumentException(
                "API-1 payload: monthly_amount must be > 0 for txn_options=" + txnOptions
                + ", got: " + monthlyAmount);
        }
        if (needLumpsum && lumpsumAmount <= 0) {
            throw new IllegalArgumentException(
                "API-1 payload: lumpsum_amount must be > 0 for txn_options=" + txnOptions
                + ", got: " + lumpsumAmount);
        }
    }

    /**
     * True when {@code goalType} sends {@code accumulated_amount} or
     * {@code tax_saving_amount} instead of {@code txn_options} + monthly/lumpsum amount.
     */
    static boolean usesSpecialAmount(String goalType) {
        return GOAL_REGULAR_INCOME.equalsIgnoreCase(goalType)
            || GOAL_TAX_SAVINGS.equalsIgnoreCase(goalType);
    }

    /**
     * Validates the goal-specific amount for REGULAR-INCOME ({@code accumulated_amount},
     * {@code needed_annual_amount}, {@code start_regular_income}) and TAX-SAVINGS
     * ({@code tax_saving_amount}) rows.
     */
    static void validateSpecialAmount(String goalType, long accumulatedAmount, long taxSavingAmount,
                                       long neededAnnualAmount, long startRegularIncome) {
        if (GOAL_REGULAR_INCOME.equalsIgnoreCase(goalType) && accumulatedAmount <= 0) {
            throw new IllegalArgumentException(
                "API-1 payload: accumulated_amount must be > 0 for goal_type=" + goalType
                + ", got: " + accumulatedAmount);
        }
        if (GOAL_REGULAR_INCOME.equalsIgnoreCase(goalType) && neededAnnualAmount <= 0) {
            throw new IllegalArgumentException(
                "API-1 payload: needed_annual_amount must be > 0 for goal_type=" + goalType
                + ", got: " + neededAnnualAmount);
        }
        if (GOAL_REGULAR_INCOME.equalsIgnoreCase(goalType) && startRegularIncome <= 0) {
            throw new IllegalArgumentException(
                "API-1 payload: start_regular_income must be > 0 for goal_type=" + goalType
                + ", got: " + startRegularIncome);
        }
        if (GOAL_TAX_SAVINGS.equalsIgnoreCase(goalType) && taxSavingAmount <= 0) {
            throw new IllegalArgumentException(
                "API-1 payload: tax_saving_amount must be > 0 for goal_type=" + goalType
                + ", got: " + taxSavingAmount);
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "API-1 payload: required field '" + field + "' is blank or null");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                "API-1 payload: field '" + field + "' must be > 0, got: " + value);
        }
    }
}
