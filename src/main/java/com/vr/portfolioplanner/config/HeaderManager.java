package com.vr.portfolioplanner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds HTTP header maps for each API.
 *
 * <h3>Credential resolution order (highest priority first)</h3>
 * <ol>
 *   <li>Environment variable — e.g. {@code API2_AUTH_TOKEN} overrides {@code api2.auth.token}</li>
 *   <li>{@code config-local.properties} — developer-local file (git-ignored)</li>
 *   <li>{@code config.properties} — committed template with empty credential stubs</li>
 * </ol>
 *
 * <h3>API 1 — application/json</h3>
 * <p>API-1 authenticates via session cookie only. No Authorization header is sent.
 * <pre>
 *   api1.cookie  →  Cookie: &lt;value&gt;                    (env: API1_COOKIE)
 * </pre>
 *
 * <h3>API 2 — multipart/form-data</h3>
 * <pre>
 *   Non-sensitive (always present when non-blank in config):
 *     api2.header.app.platform  →  app-platform
 *     api2.header.app.type      →  app-type
 *     api2.header.app.version   →  app-version
 *     api2.header.site.code     →  site-code
 *
 *   Credentials (set in config-local.properties or via env var):
 *     api2.authorization  →  Authorization  (env: API2_AUTHORIZATION — include scheme, e.g. "Basic …")
 *     api2.auth.token     →  auth-token     (env: API2_AUTH_TOKEN)
 *     api2.json.api.key   →  json-api-key   (env: API2_JSON_API_KEY)
 *     api2.cookie         →  Cookie         (env: API2_COOKIE)
 * </pre>
 *
 * <p>NOTE: Content-Type for API 2 (multipart/form-data) is intentionally absent —
 * REST Assured sets it automatically with the correct boundary via {@code .multiPart()}.
 *
 * <p>Security: credential values are NEVER logged. Only header names and the
 * configured/not-configured state are written to DEBUG logs.
 */
public final class HeaderManager {

    private static final Logger log = LoggerFactory.getLogger(HeaderManager.class);

    private final ConfigReader config;

    public HeaderManager() {
        this.config = ConfigReader.getInstance();
    }

    // -------------------------------------------------------------------------
    // API 1  —  application/json
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable header map for API 1 POST calls.
     * API-1 uses only cookie-based authentication; no Authorization header is sent.
     */
    public Map<String, String> getApi1Headers() {
        Map<String, String> h = new LinkedHashMap<>();

        h.put("Content-Type", "application/json");
        h.put("Accept",       "application/json");

        // Cookie — from api1.cookie property or API1_COOKIE env var
        addCredentialHeader(h, "Cookie", "api1.cookie");

        log.debug("API1 headers assembled: {} key(s) — {}",
            h.size(), describeKeys(h));
        return Collections.unmodifiableMap(h);
    }

    // -------------------------------------------------------------------------
    // API 2  —  multipart/form-data
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable header map for API 2 POST calls.
     * Content-Type is intentionally absent — set automatically by REST Assured.
     */
    public Map<String, String> getApi2Headers() {
        Map<String, String> h = new LinkedHashMap<>();

        h.put("Accept", "application/json");

        // Non-sensitive app metadata — from config.properties (always present when non-blank)
        addConfigHeader(h, "app-platform", "api2.header.app.platform");
        addConfigHeader(h, "app-type",     "api2.header.app.type");
        addConfigHeader(h, "app-version",  "api2.header.app.version");
        addConfigHeader(h, "site-code",    "api2.header.site.code");

        // Credentials — from config-local.properties or env vars (values never logged)
        addCredentialHeader(h, "Authorization", "api2.authorization");
        addCredentialHeader(h, "auth-token",    "api2.auth.token");
        addCredentialHeader(h, "json-api-key",  "api2.json.api.key");
        addCredentialHeader(h, "Cookie",        "api2.cookie");

        log.debug("API2 headers assembled: {} key(s) — {}", h.size(), describeKeys(h));
        return Collections.unmodifiableMap(h);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Adds a non-sensitive header from a config property.
     * Skips when the resolved value is blank.
     */
    private void addConfigHeader(Map<String, String> h, String headerName, String configKey) {
        String value = config.get(configKey, "");
        if (!value.isBlank()) {
            h.put(headerName, value);
        }
    }

    /**
     * Adds a credential header from a config property (or env-var override).
     * Resolution: env var → config-local.properties → config.properties.
     * Skips when the resolved value is blank.
     * The value is NEVER logged — only the header name and configured/not state.
     */
    private void addCredentialHeader(Map<String, String> h, String headerName, String configKey) {
        String value = config.get(configKey, "");
        if (!value.isBlank()) {
            h.put(headerName, value);
            log.debug("Credential header '{}' configured (property={})", headerName, configKey);
        } else {
            log.debug("Credential header '{}' not configured (property={} is blank/unset)",
                headerName, configKey);
        }
    }

    /** Returns a comma-separated list of header key names only — no values. */
    private static String describeKeys(Map<String, String> h) {
        return String.join(", ", h.keySet());
    }
}
