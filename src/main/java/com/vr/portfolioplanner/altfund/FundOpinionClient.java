package com.vr.portfolioplanner.altfund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vr.portfolioplanner.config.ConfigReader;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fetches Fund Opinion data (Source 1) for a specific, explicit set of
 * {@code plan_id}s — {@code opinion_type_id}, {@code is_analyst_pick},
 * {@code vr_rating}, {@code tag_name} — used by {@link AlternateFundValidator}
 * to evaluate Rules 1, 4 and 5 against an API-1 alternate fund.
 *
 * <p>Unlike the retired {@code GoodFundRegistry}, this queries only the
 * {@code plan_id}s actually needed for one test case's alternate candidates
 * (one batched call), not the entire opinion list.
 */
public final class FundOpinionClient {

    private static final Logger log = LoggerFactory.getLogger(FundOpinionClient.class);

    /** Fixed business contract for this validation — not environment-specific, so not config. */
    private static final String FIELDS = "plan_id,vr_rating,opinion_type_id,is_analyst_pick,tag_name";

    private static volatile Map<String, FundOpinionSnapshot> testOverride;

    private FundOpinionClient() {}

    /**
     * Fetches opinion snapshots for exactly the given {@code planIds}. Any
     * {@code planId} absent from the live response comes back as
     * {@link FundOpinionSnapshot#notFound(String)} — a real negative fact.
     *
     * @throws FundDataUnavailableException on network error, non-2xx, or parse failure
     */
    public static Map<String, FundOpinionSnapshot> fetch(Collection<String> planIds) {
        if (planIds.isEmpty()) return Map.of();

        Map<String, FundOpinionSnapshot> override = testOverride;
        if (override != null) {
            Map<String, FundOpinionSnapshot> result = new LinkedHashMap<>();
            for (String id : planIds) {
                result.put(id, override.getOrDefault(id, FundOpinionSnapshot.notFound(id)));
            }
            return result;
        }

        ConfigReader cfg = ConfigReader.getInstance();
        String url         = cfg.getFundOpinionBaseUrl() + cfg.getFundOpinionEndpoint();
        String planIdParam = String.join(",", planIds);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, FundOpinionSnapshot> result = new LinkedHashMap<>();

        try {
            int page = 1;
            boolean hasNext = true;
            while (hasNext) {
                Response response = RestAssured.given()
                    .queryParam("plan_id", planIdParam)
                    .queryParam("fields", FIELDS)
                    .queryParam("page", page)
                    .when()
                    .get(url);

                if (response.getStatusCode() != 200) {
                    throw new FundDataUnavailableException(
                        "fund-opinion-data returned HTTP " + response.getStatusCode() + " on page " + page
                        + " for plan_id(s)=" + planIdParam);
                }

                JsonNode root = mapper.readTree(response.getBody().asString());
                for (JsonNode row : root.path("data")) {
                    JsonNode planId = row.path("plan_id");
                    if (planId.isMissingNode() || planId.isNull()) continue;
                    result.put(planId.asText(), FundOpinionSnapshot.found(
                        planId.asText(),
                        intOrNull(row.path("opinion_type_id")),
                        boolOrNull(row.path("is_analyst_pick")),
                        intOrNull(row.path("vr_rating")),
                        textOrNull(row.path("tag_name"))
                    ));
                }

                hasNext = root.path("meta").path("has_next").asBoolean(false);
                page++;
            }
        } catch (FundDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new FundDataUnavailableException(
                "Could not fetch fund-opinion-data for plan_id(s)=" + planIdParam, e);
        }

        for (String id : planIds) {
            result.putIfAbsent(id, FundOpinionSnapshot.notFound(id));
        }
        log.info("Fetched fund-opinion-data for {} plan_id(s) ({} found)",
            planIds.size(), result.values().stream().filter(FundOpinionSnapshot::isFound).count());
        return result;
    }

    private static Integer intOrNull(JsonNode n)  { return (n == null || n.isMissingNode() || n.isNull()) ? null : n.asInt(); }
    private static Boolean boolOrNull(JsonNode n) { return (n == null || n.isMissingNode() || n.isNull()) ? null : n.asBoolean(); }
    private static String  textOrNull(JsonNode n) { return (n == null || n.isMissingNode() || n.isNull()) ? null : n.asText(); }

    /** Test-only seam: bypasses the live network call entirely. */
    public static void primeForTest(Map<String, FundOpinionSnapshot> entries) {
        testOverride = new HashMap<>(entries);
    }

    /** Test-only seam: clears the override so the next call fetches (or is re-primed) fresh. */
    public static void resetForTest() {
        testOverride = null;
    }
}
