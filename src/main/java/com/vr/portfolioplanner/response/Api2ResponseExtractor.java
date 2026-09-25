package com.vr.portfolioplanner.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vr.portfolioplanner.response.model.BreakdownEntry;
import com.vr.portfolioplanner.response.model.ExtractedResponse;
import com.vr.portfolioplanner.response.model.FundEntry;
import com.vr.portfolioplanner.response.model.TransactionLeg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts comparable business fields from an API-2 JSON response body.
 *
 * <h3>Source paths → model fields</h3>
 * <pre>
 *   root.status  (boolean at root level)       → ExtractedResponse.status  ("true"/"false")
 *   data.investor.investor_id                → ExtractedResponse.investorId
 *   funds_data.data[]                        → EITHER a flat list of fund objects (single
 *                                               portfolio) OR a list of portfolio groups whose
 *                                               nested data[] holds the fund objects (when the
 *                                               plan splits into income/growth) — detected per
 *                                               element by presence of plan_data, then mapped
 *                                               below
 *   plan_data.plan_id                        → FundEntry.planId
 *   plan_data.name                           → FundEntry.planName
 *   category_data.category_id               → FundEntry.categoryId
 *   category_data.category_fmt              → FundEntry.categoryFmt
 *   txn_data.amount                          → FundEntry.amount
 *   inv_data[].txn_type_name                → FundEntry.legs[].rawType
 *   inv_data[].amount                        → FundEntry.legs[].amount
 *   breakdown_funds_data.data[]             → List&lt;BreakdownEntry&gt;
 *   breakdown_funds_data.data[].perc_fmt    → BreakdownEntry.percFmt
 * </pre>
 *
 * <p>API-2 has no {@code data.message} — {@link ExtractedResponse#getMessage()} is null.
 */
public final class Api2ResponseExtractor {

    private static final Logger       log    = LoggerFactory.getLogger(Api2ResponseExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String API_LABEL = "API-2";

    private Api2ResponseExtractor() {}

    public static ExtractedResponse extract(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return ExtractedResponse.failed(API_LABEL, "Response body is null or empty");
        }
        try {
            JsonNode root = MAPPER.readTree(jsonBody);
            JsonNode data = root.path("data");
            if (data.isMissingNode()) {
                return ExtractedResponse.failed(API_LABEL, "Missing 'data' node in response");
            }

            // status is at the ROOT level in the real API-2 response, NOT inside "data".
            String status     = textOrNull(root.path("status"));
            String investorId = extractInvestorId(data);

            List<FundEntry>      funds      = extractFunds(data);
            List<BreakdownEntry> breakdowns = extractBreakdowns(data);

            List<String> notComparable = List.of(
                "data.funds_data.data[].inv_data[] — absent at fund level in real API-2 " +
                    "response; leg-type comparison not available at this level",
                "data.message — NOT_COMPARABLE: absent in API-2 (present in API-1 only)",
                "port_builder_id, output_id — EXCLUDED: generated technical identifiers"
            );

            log.debug("API-2 extracted: status={} investor={} funds={} breakdowns={}",
                status, investorId, funds.size(), breakdowns.size());

            return ExtractedResponse.success(API_LABEL, status, null,
                investorId, funds, breakdowns, notComparable);

        } catch (Exception e) {
            log.warn("API-2 extraction failed: {}", e.getMessage());
            return ExtractedResponse.failed(API_LABEL, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Funds
    // -------------------------------------------------------------------------

    private static List<FundEntry> extractFunds(JsonNode data) {
        List<FundEntry> result = new ArrayList<>();
        // funds_data.data[] is shape-inconsistent in the real API-2 response: when the plan
        // splits into income/growth portfolios, each element is a GROUP (title + nested data[]
        // of funds); when there's a single portfolio, each element IS the fund directly (no
        // nested data[]). Detect per-element by presence of plan_data rather than assuming
        // either shape.
        JsonNode items = data.path("funds_data").path("data");
        if (!items.isArray()) return result;

        for (JsonNode item : items) {
            if (!item.path("plan_data").isMissingNode()) {
                addFund(result, item);
                continue;
            }
            JsonNode nested = item.path("data");
            if (nested.isArray()) {
                for (JsonNode fund : nested) {
                    addFund(result, fund);
                }
            }
        }
        return result;
    }

    private static void addFund(List<FundEntry> result, JsonNode item) {
        // inv_data[] does NOT exist at fund level in the real API-2 response.
        // It lives inside breakdown_funds_data.data[].data[] and is not used for
        // fund-level amount comparison. Legs are left empty here.
        result.add(FundEntry.builder()
            .planId(     textOrNull(item.path("plan_data").path("plan_id")))
            .planName(   textOrNull(item.path("plan_data").path("name")))
            .categoryId( textOrNull(item.path("category_data").path("category_id")))
            .categoryFmt(textOrNull(item.path("category_data").path("category_fmt")))
            .amount(     asBigDecimal(item.path("txn_data").path("amount")))
            .legs(       java.util.Collections.emptyList())
            .build());
    }

    // -------------------------------------------------------------------------
    // Breakdown
    // -------------------------------------------------------------------------

    private static List<BreakdownEntry> extractBreakdowns(JsonNode data) {
        List<BreakdownEntry> result = new ArrayList<>();
        JsonNode items = data.path("breakdown_funds_data").path("data");
        if (!items.isArray()) return result;

        for (JsonNode item : items) {
            // Real API uses "name" (e.g. "Equity", "Debt") as the category label/key.
            String catId  = textOrNull(item.path("name"));
            String catFmt = catId;

            result.add(new BreakdownEntry(
                catId, catFmt,
                asBigDecimal(item.path("percentage")),
                textOrNull(item.path("perc_fmt")),
                asBigDecimal(item.path("amount"))
            ));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Investor ID
    // -------------------------------------------------------------------------

    private static String extractInvestorId(JsonNode data) {
        JsonNode investor = data.path("investor");
        if (investor.isMissingNode() || investor.isNull()) return null;
        if (investor.isValueNode()) return investor.asText();
        for (String field : new String[]{"investor_id", "investorId", "id"}) {
            JsonNode v = investor.path(field);
            if (!v.isMissingNode() && !v.isNull()) return v.asText();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String text = node.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    static BigDecimal asBigDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isNumber()) return node.decimalValue();
        String text = node.asText("").trim();
        if (text.isEmpty()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            log.warn("Cannot parse '{}' as BigDecimal", text);
            return null;
        }
    }
}
