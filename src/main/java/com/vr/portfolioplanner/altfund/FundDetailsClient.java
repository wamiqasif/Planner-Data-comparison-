package com.vr.portfolioplanner.altfund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vr.portfolioplanner.config.ConfigReader;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches Fund Details data (Source 2) for a single {@code plan_id} —
 * currently only {@code category_name} — used to pair orphaned funds across
 * API-1/API-2 by category and to evaluate Rule 2 against an API-1 alternate.
 * Results are cached per {@code plan_id} for the JVM run since the same fund
 * may be looked up repeatedly (pairing + rule evaluation, or reappearing
 * across multiple test-case rows).
 */
public final class FundDetailsClient {

    private static final Logger log = LoggerFactory.getLogger(FundDetailsClient.class);
    private static final Map<String, FundDetailsSnapshot> CACHE = new ConcurrentHashMap<>();

    private static volatile Map<String, FundDetailsSnapshot> testOverride;
    private static volatile Set<String> testFailingPlanIds = Set.of();

    private FundDetailsClient() {}

    /**
     * Fetches (or returns the cached) details snapshot for {@code planId}.
     *
     * @throws FundDataUnavailableException on network error, non-2xx, parse
     *         failure, or when {@code planId} was primed via
     *         {@link #primeFailureForTest(Set)}
     */
    public static FundDetailsSnapshot fetch(String planId) {
        if (testFailingPlanIds.contains(planId)) {
            throw new FundDataUnavailableException(
                "Simulated Fund Details fetch failure for plan_id=" + planId);
        }

        Map<String, FundDetailsSnapshot> override = testOverride;
        if (override != null) {
            return override.getOrDefault(planId, FundDetailsSnapshot.notFound(planId));
        }

        FundDetailsSnapshot cached = CACHE.get(planId);
        if (cached != null) return cached;

        ConfigReader cfg = ConfigReader.getInstance();
        String url = cfg.getFundDetailsBaseUrl() + cfg.getFundDetailsEndpoint() + planId;
        ObjectMapper mapper = new ObjectMapper();

        try {
            Response response = RestAssured.given().when().get(url);

            if (response.getStatusCode() != 200) {
                throw new FundDataUnavailableException(
                    "Fund Details API returned HTTP " + response.getStatusCode() + " for plan_id=" + planId);
            }

            JsonNode root    = mapper.readTree(response.getBody().asString());
            JsonNode dataArr = root.path("data");
            FundDetailsSnapshot snapshot;
            if (dataArr.isArray() && dataArr.size() > 0) {
                JsonNode categoryName = dataArr.get(0).path("category_name");
                snapshot = FundDetailsSnapshot.found(planId,
                    (categoryName.isMissingNode() || categoryName.isNull()) ? null : categoryName.asText());
            } else {
                snapshot = FundDetailsSnapshot.notFound(planId);
            }

            CACHE.put(planId, snapshot);
            log.info("Fetched Fund Details for plan_id={} → category_name='{}'", planId, snapshot.getCategoryName());
            return snapshot;

        } catch (FundDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new FundDataUnavailableException("Could not fetch Fund Details for plan_id=" + planId, e);
        }
    }

    /** Test-only seam: bypasses the live network call entirely. */
    public static void primeForTest(Map<String, FundDetailsSnapshot> entries) {
        testOverride = new ConcurrentHashMap<>(entries);
    }

    /** Test-only seam: makes {@link #fetch(String)} throw for the given plan_ids, simulating a live fetch failure. */
    public static void primeFailureForTest(Set<String> planIds) {
        testFailingPlanIds = Set.copyOf(planIds);
    }

    /** Test-only seam: clears the override, simulated failures, and cache so the next call fetches (or is re-primed) fresh. */
    public static void resetForTest() {
        testOverride = null;
        testFailingPlanIds = Set.of();
        CACHE.clear();
    }
}
