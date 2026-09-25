package com.vr.portfolioplanner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton configuration reader.
 *
 * <h3>Loading order (highest priority wins at {@link #get} call time)</h3>
 * <ol>
 *   <li>Environment variable — e.g. {@code API2_AUTH_TOKEN} overrides {@code api2.auth.token}</li>
 *   <li>{@code config-<env>.properties} — loaded when the {@code ENV} env var / {@code -Denv}
 *       system property is set (e.g. {@code ENV=uat} loads {@code config-uat.properties})</li>
 *   <li>{@code config-local.properties} — developer-local credentials; always loaded when
 *       present on the classpath; <strong>git-ignored</strong></li>
 *   <li>{@code config.properties} — committed base configuration with empty credential stubs</li>
 * </ol>
 *
 * <h3>Credential properties</h3>
 * <pre>
 *   api1.cookie          API1 session cookie
 *   api1.auth.token      Reserved; not currently used for API-1
 *   api2.authorization   Full Authorization header value (e.g. "Basic …")
 *   api2.auth.token      auth-token header value
 *   api2.json.api.key    json-api-key header value
 *   api2.cookie          API2 session cookie
 * </pre>
 * These can be set in {@code config-local.properties} for local development
 * or via the corresponding environment variables in CI.
 */
public final class ConfigReader {

    private static final Logger log = LoggerFactory.getLogger(ConfigReader.class);

    private static final String BASE_CONFIG = "config.properties";

    // Singleton — initialised once, thread-safe via class-loading
    private static final ConfigReader INSTANCE = new ConfigReader();

    private final Properties properties = new Properties();

    private ConfigReader() {
        loadBase();
        loadLocal();     // config-local.properties (git-ignored developer credentials)
        loadOverlay();   // config-<env>.properties (when ENV= is set)
    }

    public static ConfigReader getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Non-sensitive property access
    // -------------------------------------------------------------------------

    /**
     * Returns a config value. Resolution order: env var → properties file.
     *
     * @throws IllegalStateException if the key is absent in both sources
     */
    public String get(String key) {
        String envValue = fromEnv(key);
        if (envValue != null) {
            log.debug("Config key '{}' resolved from environment variable", key);
            return envValue;
        }
        String propValue = properties.getProperty(key);
        if (propValue == null) {
            throw new IllegalStateException(
                "Required config key not found: '" + key + "'. "
                + "Check config.properties or set env var " + toEnvName(key));
        }
        return propValue.trim();
    }

    /**
     * Returns a config value, falling back to {@code defaultValue} if absent.
     */
    public String get(String key, String defaultValue) {
        try {
            return get(key);
        } catch (IllegalStateException e) {
            log.debug("Config key '{}' not found, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public long getLong(String key) {
        return Long.parseLong(get(key));
    }

    public double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    // -------------------------------------------------------------------------
    // Sensitive value access  —  environment variables ONLY
    // -------------------------------------------------------------------------

    /**
     * Returns the value of an OS environment variable directly.
     * Returns {@code null} when the variable is not set or is blank.
     *
     * <p>Prefer {@link #get(String, String)} for credential properties such as
     * {@code api2.auth.token} — it already checks the env var automatically
     * (via {@link #toEnvName}) before falling back to {@code config-local.properties}
     * and then {@code config.properties}.  Use this method only when you specifically
     * need to inspect the raw OS environment.
     */
    public String getSensitiveEnv(String envVarName) {
        String value = System.getenv(envVarName);
        if (value == null || value.isBlank()) {
            log.debug("Sensitive env var '{}' is not set", envVarName);
            return null;
        }
        return value;
    }

    /**
     * Returns {@code true} if the sensitive env var is present and non-blank.
     */
    public boolean hasSensitiveEnv(String envVarName) {
        return getSensitiveEnv(envVarName) != null;
    }

    // -------------------------------------------------------------------------
    // Derived / convenience accessors
    // -------------------------------------------------------------------------

    public String getApi1BaseUrl()   { return get("api1.base.url"); }
    public String getApi1Endpoint()  { return get("api1.endpoint"); }
    public int    getApi1ConnectTimeout() { return getInt("api1.timeout.connect"); }
    public int    getApi1ReadTimeout()    { return getInt("api1.timeout.read"); }

    public String getApi2BaseUrl()   { return get("api2.base.url"); }
    public String getApi2Endpoint()  { return get("api2.endpoint"); }
    public int    getApi2ConnectTimeout() { return getInt("api2.timeout.connect"); }
    public int    getApi2ReadTimeout()    { return getInt("api2.timeout.read"); }

    public double getComparisonAmountTolerance() {
        return getDouble("comparison.amount.tolerance");
    }

    public double getComparisonPercentageTolerance() {
        return getDouble("comparison.percentage.tolerance");
    }

    public String getFundOpinionBaseUrl()  { return get("fund.opinion.base.url"); }
    public String getFundOpinionEndpoint() { return get("fund.opinion.endpoint"); }

    public String getFundDetailsBaseUrl()  { return get("fund.details.base.url"); }
    public String getFundDetailsEndpoint() { return get("fund.details.endpoint"); }

    public String getEnvironment() {
        return get("environment", "qa");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Loads {@code config-local.properties} from the classpath if present.
     * This file is git-ignored and intended for developer-local credentials.
     * It overrides {@code config.properties} but is overridden by env vars and
     * the environment-specific overlay.
     */
    private void loadLocal() {
        String localFile = "config-local.properties";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(localFile)) {
            if (is == null) {
                log.debug("No local config found ({}); using base config only", localFile);
                return;
            }
            Properties local = new Properties();
            local.load(is);
            properties.putAll(local);
            log.info("Loaded local config: {} ({} key(s))", localFile, local.size());
        } catch (IOException e) {
            log.warn("Could not load local config {}: {}", localFile, e.getMessage());
        }
    }

    private void loadBase() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(BASE_CONFIG)) {
            if (is == null) {
                throw new IllegalStateException(
                    BASE_CONFIG + " not found on classpath. "
                    + "Ensure src/main/resources/" + BASE_CONFIG + " exists.");
            }
            properties.load(is);
            log.info("Loaded base config: {}", BASE_CONFIG);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + BASE_CONFIG, e);
        }
    }

    private void loadOverlay() {
        // Check env var ENV, then system property -Denv, then config value
        String env = System.getenv("ENV");
        if (env == null || env.isBlank()) env = System.getProperty("env");
        if (env == null || env.isBlank()) env = properties.getProperty("environment");
        if (env == null || env.isBlank() || "qa".equalsIgnoreCase(env)) return;

        String overlayFile = "config-" + env.toLowerCase() + ".properties";
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(overlayFile)) {
            if (is == null) {
                log.debug("No overlay config found for environment '{}' ({})", env, overlayFile);
                return;
            }
            Properties overlay = new Properties();
            overlay.load(is);
            properties.putAll(overlay);
            log.info("Applied environment overlay: {} ({} keys)", overlayFile, overlay.size());
        } catch (IOException e) {
            log.warn("Could not load overlay config {}: {}", overlayFile, e.getMessage());
        }
    }

    /** Converts a dotted property key to an UPPER_SNAKE_CASE env-var name. */
    static String toEnvName(String key) {
        return key.toUpperCase().replace('.', '_');
    }

    /** Reads the env-var equivalent of a dotted key; returns null if not set. */
    private static String fromEnv(String key) {
        String value = System.getenv(toEnvName(key));
        if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
}
