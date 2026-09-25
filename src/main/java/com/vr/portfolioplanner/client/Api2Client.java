package com.vr.portfolioplanner.client;

import com.vr.portfolioplanner.config.ConfigReader;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * HTTP client for API 2 – Investment Plan (Advisor portal).
 *
 * <pre>
 *   POST https://advappapi.valueresearchonline.com/api/v0/investment-plan/
 *   Content-Type: multipart/form-data  (boundary set automatically by REST Assured)
 * </pre>
 *
 * <p>Responsibilities (this class only):
 * <ul>
 *   <li>Configure timeouts from {@link ConfigReader}.</li>
 *   <li>Stream each form field as a multipart part.</li>
 *   <li>Return an {@link ApiResponse} for every outcome — including 4xx, 5xx,
 *       and transport errors.</li>
 * </ul>
 *
 * <p>Not responsible for:
 * <ul>
 *   <li>Building the form-field map (caller's responsibility).</li>
 *   <li>Setting Content-Type explicitly — REST Assured injects the correct
 *       {@code multipart/form-data; boundary=...} header automatically when
 *       {@code .multiPart()} is used.</li>
 *   <li>Parsing or comparing the response body.</li>
 *   <li>Logging form-field values that may be sensitive.</li>
 * </ul>
 */
public final class Api2Client {

    private static final Logger log = LoggerFactory.getLogger(Api2Client.class);

    private final String           fullUrl;
    private final RestAssuredConfig raConfig;

    public Api2Client() {
        ConfigReader cfg = ConfigReader.getInstance();
        this.fullUrl = cfg.getApi2BaseUrl() + cfg.getApi2Endpoint();

        int connectMs = cfg.getApi2ConnectTimeout() * 1000;
        int readMs    = cfg.getApi2ReadTimeout()    * 1000;

        this.raConfig = RestAssured.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .dontReuseHttpClientInstance()
                .setParam("http.connection.timeout", connectMs)
                .setParam("http.socket.timeout",     readMs));

        log.info("Api2Client ready — endpoint={} connectTimeout={}s readTimeout={}s",
            fullUrl, cfg.getApi2ConnectTimeout(), cfg.getApi2ReadTimeout());
    }

    /**
     * Executes a multipart/form-data POST and returns an {@link ApiResponse}.
     *
     * <p>Each entry in {@code formFields} becomes one multipart part.
     * The Content-Type header (including boundary) is set by REST Assured and
     * must NOT appear in the {@code headers} map.
     *
     * <p>Never throws: transport errors are caught and wrapped in an
     * {@link ApiResponse} with statusCode {@code -1}.
     *
     * @param formFields ordered map of form-field names to string values
     *                   (built by caller, not here)
     * @param headers    headers produced by
     *                   {@link com.vr.portfolioplanner.config.HeaderManager}
     *                   — must NOT include Content-Type
     * @return an {@link ApiResponse} for every outcome, including 4xx / 5xx
     */
    public ApiResponse post(Map<String, String> formFields, Map<String, String> headers) {
        log.info("API-2 POST {} [fields={}, headers={}]",
            fullUrl, formFields.keySet(), headers.keySet());

        long startMs = System.currentTimeMillis();
        LocalDateTime requestTimestamp = LocalDateTime.now();
        try {
            RequestSpecification spec = RestAssured.given()
                .config(raConfig)
                .headers(headers);

            // Attach each field as a multipart text part
            for (Map.Entry<String, String> entry : formFields.entrySet()) {
                spec = spec.multiPart(entry.getKey(), entry.getValue());
            }

            Response response = spec.when().post(fullUrl);

            ApiResponse ar = ApiResponse.of("API-2", response, requestTimestamp);
            log.info("API-2 completed — {}", ar);
            return ar;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            log.warn("API-2 transport error after {}ms", elapsed, e);
            return ApiResponse.ofError("API-2", elapsed, e, requestTimestamp);
        }
    }
}
