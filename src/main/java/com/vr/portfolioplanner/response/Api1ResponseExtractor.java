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
 * Extracts comparable business fields from an API-1 JSON response body.
 *
 * <h3>Source paths → model fields</h3>
 * <pre>
 *   root.status  (boolean at root level)       → ExtractedResponse.status  ("true"/"false")
 *   root.message (string at root level)      → ExtractedResponse.message  [NOT_COMPARABLE]
 *   data.investor.investor_id                → ExtractedResponse.investorId
 *   plan_data.plan_id                        → FundEntry.planId
 *   plan_data.name                           → FundEntry.planName
 *   category_data.category_id               → FundEntry.categoryId
 *   category_data.category_fmt              → FundEntry.categoryFmt
 *   first_month_amount                       → FundEntry.amount
 *   legs[].type / legs[].type_fmt           → FundEntry.legs[].rawType
 *   legs[].amount                            → FundEntry.legs[].amount
 *   (legs[].frequency, legs[].duration excluded — NOT_COMPARABLE)
 *   breakdown_funds_data.data[]             → List&lt;BreakdownEntry&gt;
 *   breakdown_funds_data.data[].perc_fmt    → BreakdownEntry.percFmt
 * </pre>
 */
public final class Api1ResponseExtractor {

    private static final Logger       log    = LoggerFactory.getLogger(Api1ResponseExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String API_LABEL = "API-1";

    private Api1ResponseExtractor() {}

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

            // status and message are at the ROOT level in the real API-1 response,
            // NOT inside the "data" object.
            String status     = textOrNull(root.path("status"));
            String message    = textOrNull(root.path("message"));
            String investorId = extractInvestorId(data);

            List<FundEntry>      funds      = extractFunds(data);
            List<BreakdownEntry> breakdowns = extractBreakdowns(data);

            List<String> notComparable = List.of(
                "data.funds_data.data[].legs[].frequency  /  .duration — NOT_COMPARABLE: no equivalent in API-2 inv_data",
                "data.message — NOT_COMPARABLE: absent in API-2",
                "port_builder_id, output_id — EXCLUDED: generated technical identifiers"
            );

            log.debug("API-1 extracted: status={} investor={} funds={} breakdowns={}",
                status, investorId, funds.size(), breakdowns.size());

            return ExtractedResponse.success(API_LABEL, status, message,
                investorId, funds, breakdowns, notComparable);

        } catch (Exception e) {
            log.warn("API-1 extraction failed: {}", e.getMessage());
            return ExtractedResponse.failed(API_LABEL, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Funds
    // -------------------------------------------------------------------------

    private static List<FundEntry> extractFunds(JsonNode data) {
        List<FundEntry> result = new ArrayList<>();
        JsonNode items = data.path("funds_data").path("data");
        if (!items.isArray()) return result;

        for (JsonNode item : items) {
            result.add(FundEntry.builder()
                .planId(     textOrNull(item.path("plan_data").path("plan_id")))
                .planName(   textOrNull(item.path("plan_data").path("name")))
                .categoryId( textOrNull(item.path("category_data").path("category_id")))
                .categoryFmt(textOrNull(item.path("category_data").path("category_fmt")))
                .amount(     asBigDecimal(item.path("first_month_amount")))
                .legs(       extractLegs(item))
                .build());
        }
        return result;
    }

    /**
     * Extracts legs from API-1 {@code legs[]} array.
     * Uses {@code type} as the primary raw-type field; falls back to {@code type_fmt}.
     * {@code frequency} and {@code duration} are intentionally excluded — NOT_COMPARABLE.
     */
    private static List<TransactionLeg> extractLegs(JsonNode fundItem) {
        List<TransactionLeg> legs = new ArrayList<>();
        JsonNode legsNode = fundItem.path("legs");
        if (!legsNode.isArray()) return legs;

        for (JsonNode leg : legsNode) {
            String rawType = textOrNull(leg.path("type"));
            if (rawType == null) rawType = textOrNull(leg.path("type_fmt"));
            legs.add(new TransactionLeg(rawType, asBigDecimal(leg.path("amount"))));
        }
        return legs;
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
            // There is no category_id at the breakdown level.
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
