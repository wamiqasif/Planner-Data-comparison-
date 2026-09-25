package com.vr.portfolioplanner.client;

import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable wrapper around an HTTP response (or a network-error record).
 *
 * <p>Two factory methods cover the two outcomes:
 * <ul>
 *   <li>{@link #of(String, Response)} – any completed HTTP exchange, including
 *       4xx / 5xx; the body is always captured.</li>
 *   <li>{@link #ofError(String, long, Exception)} – a transport-level failure
 *       (connection refused, timeout, DNS error); statusCode is set to -1.</li>
 * </ul>
 *
 * <p>No business logic lives here. Comparison, validation, and reporting are
 * done by higher-level components that consume this object.
 */
public final class ApiResponse {

    private static final Logger log = LoggerFactory.getLogger(ApiResponse.class);

    private final String  apiLabel;
    private final int     statusCode;
    private final String  responseBody;
    private final long    responseTimeMs;
    private final Map<String, String> responseHeaders;
    private final boolean networkError;
    private final String  errorMessage;
    private final LocalDateTime requestTimestamp;

    private ApiResponse(String apiLabel, int statusCode, String responseBody,
                        long responseTimeMs, Map<String, String> responseHeaders,
                        boolean networkError, String errorMessage,
                        LocalDateTime requestTimestamp) {
        this.apiLabel        = apiLabel;
        this.statusCode      = statusCode;
        this.responseBody    = responseBody;
        this.responseTimeMs  = responseTimeMs;
        this.responseHeaders = Collections.unmodifiableMap(responseHeaders);
        this.networkError    = networkError;
        this.errorMessage    = errorMessage;
        this.requestTimestamp = requestTimestamp;
    }

    // -------------------------------------------------------------------------
    // Factory: successful HTTP exchange (any status code – 2xx, 4xx, 5xx)
    // -------------------------------------------------------------------------

    /**
     * Wraps a completed REST Assured {@link Response}.
     * The full body is captured regardless of status code.
     *
     * @param apiLabel human-readable name for logging ("API-1", "API-2")
     * @param response the REST Assured response object
     * @param requestTimestamp wall-clock time at which the request was dispatched
     */
    public static ApiResponse of(String apiLabel, Response response, LocalDateTime requestTimestamp) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.getHeaders().forEach(h -> headers.put(h.getName(), h.getValue()));

        int    status  = response.getStatusCode();
        long   timeMs  = response.getTime();
        String body    = response.getBody().asString();

        log.debug("{} → HTTP {} in {}ms (body {} bytes)", apiLabel, status, timeMs, body.length());

        return new ApiResponse(apiLabel, status, body, timeMs, headers, false, null, requestTimestamp);
    }

    // -------------------------------------------------------------------------
    // Factory: transport / network error
    // -------------------------------------------------------------------------

    /**
     * Wraps a transport-level failure (socket timeout, connection refused, etc.).
     * The statusCode is set to -1 to distinguish from any real HTTP response.
     * The errorMessage carries the exception type and message; no stack trace
     * is included to avoid noise in logs — the caller should log the full
     * exception at DEBUG/WARN level.
     *
     * @param apiLabel  human-readable name for logging
     * @param elapsedMs wall-clock time elapsed before the error was thrown
     * @param e         the exception thrown by the HTTP client
     * @param requestTimestamp wall-clock time at which the request was dispatched
     */
    public static ApiResponse ofError(String apiLabel, long elapsedMs, Exception e, LocalDateTime requestTimestamp) {
        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
        log.warn("{} transport error after {}ms — {}", apiLabel, elapsedMs, msg);
        return new ApiResponse(apiLabel, -1, "", elapsedMs,
                               Collections.emptyMap(), true, msg, requestTimestamp);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Identifies which API produced this response ("API-1", "API-2"). */
    public String getApiLabel() { return apiLabel; }

    /**
     * HTTP status code, or {@code -1} if a transport error prevented
     * the server from sending any response.
     */
    public int getStatusCode() { return statusCode; }

    /**
     * Raw response body as a string.
     * Non-empty even for 4xx / 5xx responses; empty only on transport error.
     */
    public String getResponseBody() { return responseBody; }

    /** Wall-clock elapsed time from request dispatch to full body receipt (ms). */
    public long getResponseTimeMs() { return responseTimeMs; }

    /** Wall-clock time at which this request was dispatched. */
    public LocalDateTime getRequestTimestamp() { return requestTimestamp; }

    /** Response headers as an unmodifiable map (first value wins on duplicates). */
    public Map<String, String> getResponseHeaders() { return responseHeaders; }

    /** {@code true} when a transport-level error prevented any HTTP response. */
    public boolean isNetworkError() { return networkError; }

    /**
     * Error message when {@link #isNetworkError()} is {@code true};
     * {@code null} otherwise.
     */
    public String getErrorMessage() { return errorMessage; }

    // Convenience status predicates
    public boolean isSuccess()     { return !networkError && statusCode >= 200 && statusCode < 300; }
    public boolean isClientError() { return !networkError && statusCode >= 400 && statusCode < 500; }
    public boolean isServerError() { return !networkError && statusCode >= 500; }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        if (networkError) {
            return String.format("ApiResponse{%s  status=ERROR  time=%dms  error='%s'}",
                apiLabel, responseTimeMs, errorMessage);
        }
        return String.format("ApiResponse{%s  status=%d  time=%dms  bodyLen=%d}",
            apiLabel, statusCode, responseTimeMs, responseBody.length());
    }
}
