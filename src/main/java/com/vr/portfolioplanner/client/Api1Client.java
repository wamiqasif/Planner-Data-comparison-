package com.vr.portfolioplanner.client;

import com.vr.portfolioplanner.config.ConfigReader;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * HTTP client for API 1 – Portfolio Planner.
 *
 * <pre>
 *   POST https://qaappapi.valueresearch.in/api/v1/planner/generate
 *   Content-Type: application/json
 * </pre>
 *
 * <p>Responsibilities (this class only):
 * <ul>
 *   <li>Configure timeouts from {@link ConfigReader}.</li>
 *   <li>Set the Accept / Content-Type for JSON.</li>
 *   <li>Execute the POST and return an {@link ApiResponse} for <em>every</em>
 *       outcome — including 4xx, 5xx, and transport errors.</li>
 * </ul>
 *
 * <p>Not responsible for:
 * <ul>
 *   <li>Building the JSON payload (caller's responsibility).</li>
 *   <li>Parsing or comparing the response body.</li>
 *   <li>Logging request bodies or sensitive headers.</li>
 * </ul>
 */
public final class Api1Client {

    private static final Logger log = LoggerFactory.getLogger(Api1Client.class);

    private final String           fullUrl;
    private final RestAssuredConfig raConfig;

    public Api1Client() {
        ConfigReader cfg = ConfigReader.getInstance();
        this.fullUrl = cfg.getApi1BaseUrl() + cfg.getApi1Endpoint();

        int connectMs = cfg.getApi1ConnectTimeout() * 1000;
        int readMs    = cfg.getApi1ReadTimeout()    * 1000;

        this.raConfig = RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .dontReuseHttpClientInstance()
                .setParam("http.connection.timeout", connectMs)
                .setParam("http.socket.timeout",     readMs));

        log.info("Api1Client ready — endpoint={} connectTimeout={}s readTimeout={}s",
            fullUrl, cfg.getApi1ConnectTimeout(), cfg.getApi1ReadTimeout());
    }

    /**
     * Executes the POST request and returns an {@link ApiResponse}.
     *
     * <p>Never throws: transport errors are caught and wrapped in an
     * {@link ApiResponse} with statusCode {@code -1}.
     *
     * @param jsonBody pre-built JSON request body (built by caller, not here)
     * @param headers  headers produced by {@link com.vr.portfolioplanner.config.HeaderManager}
     * @return an {@link ApiResponse} for every outcome, including 4xx / 5xx
     */
    public ApiResponse post(String jsonBody, Map<String, String> headers) {
        log.info("API-1 POST {} [payload={}B, headers={}]",
            fullUrl, jsonBody.length(), headers.keySet());

        long startMs = System.currentTimeMillis();
        LocalDateTime requestTimestamp = LocalDateTime.now();
        try {
            Response response = RestAssured.given()
                .config(raConfig)
                .headers(headers)
                .contentType("application/json")
                .body(jsonBody)
                .when()
                .post(fullUrl);

            ApiResponse ar = ApiResponse.of("API-1", response, requestTimestamp);
            log.info("API-1 completed — {}", ar);
            return ar;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            log.warn("API-1 transport error after {}ms", elapsed, e);
            return ApiResponse.ofError("API-1", elapsed, e, requestTimestamp);
        }
    }
}
