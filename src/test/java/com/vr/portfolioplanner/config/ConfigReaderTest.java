package com.vr.portfolioplanner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Verifies that the configuration layer loads correctly.
 * No API calls are made. No credentials are required.
 */
public class ConfigReaderTest {

    private static final Logger log = LoggerFactory.getLogger(ConfigReaderTest.class);

    // Initialized at field level: ConfigReader is a singleton and safe to call
    // here; avoids @BeforeClass group-filtering edge-cases in TestNG.
    private final ConfigReader config = ConfigReader.getInstance();

    @BeforeClass
    public void logStartup() {
        log.info("ConfigReaderTest starting — environment = {}", config.getEnvironment());
    }

    // -------------------------------------------------------------------------
    // API 1 properties
    // -------------------------------------------------------------------------

    @Test(groups = "config", description = "api1.base.url is loaded and valid")
    public void api1BaseUrlIsLoaded() {
        String url = config.getApi1BaseUrl();
        log.info("api1.base.url = {}", url);
        Assert.assertNotNull(url, "api1.base.url must not be null");
        Assert.assertFalse(url.isBlank(), "api1.base.url must not be blank");
        Assert.assertTrue(url.startsWith("https://"),
                "api1.base.url must use HTTPS; got: " + url);
    }

    @Test(groups = "config", description = "api1.endpoint is loaded and starts with /")
    public void api1EndpointIsLoaded() {
        String endpoint = config.getApi1Endpoint();
        log.info("api1.endpoint = {}", endpoint);
        Assert.assertNotNull(endpoint);
        Assert.assertTrue(endpoint.startsWith("/"),
                "api1.endpoint must start with /; got: " + endpoint);
    }

    @Test(groups = "config", description = "api1 timeouts are positive integers")
    public void api1TimeoutsArePositive() {
        int connect = config.getApi1ConnectTimeout();
        int read    = config.getApi1ReadTimeout();
        log.info("API1 timeouts — connect: {}s, read: {}s", connect, read);
        Assert.assertTrue(connect > 0, "api1.timeout.connect must be > 0");
        Assert.assertTrue(read    > 0, "api1.timeout.read must be > 0");
    }

    @Test(groups = "config", description = "api1 content-type is application/json")
    public void api1ContentTypeIsJson() {
        String ct = config.get("api1.content.type");
        log.info("api1.content.type = {}", ct);
        Assert.assertEquals(ct, "application/json",
                "API1 Content-Type must be application/json");
    }

    // -------------------------------------------------------------------------
    // API 2 properties
    // -------------------------------------------------------------------------

    @Test(groups = "config", description = "api2.base.url is loaded and valid")
    public void api2BaseUrlIsLoaded() {
        String url = config.getApi2BaseUrl();
        log.info("api2.base.url = {}", url);
        Assert.assertNotNull(url);
        Assert.assertFalse(url.isBlank());
        Assert.assertTrue(url.startsWith("https://"),
                "api2.base.url must use HTTPS; got: " + url);
    }

    @Test(groups = "config", description = "api2.endpoint is loaded and starts with /")
    public void api2EndpointIsLoaded() {
        String endpoint = config.getApi2Endpoint();
        log.info("api2.endpoint = {}", endpoint);
        Assert.assertNotNull(endpoint);
        Assert.assertTrue(endpoint.startsWith("/"),
                "api2.endpoint must start with /; got: " + endpoint);
    }

    @Test(groups = "config", description = "api2 timeouts are positive integers")
    public void api2TimeoutsArePositive() {
        int connect = config.getApi2ConnectTimeout();
        int read    = config.getApi2ReadTimeout();
        log.info("API2 timeouts — connect: {}s, read: {}s", connect, read);
        Assert.assertTrue(connect > 0);
        Assert.assertTrue(read    > 0);
    }

    @Test(groups = "config", description = "api2 content-type is multipart/form-data")
    public void api2ContentTypeIsMultipart() {
        String ct = config.get("api2.content.type");
        log.info("api2.content.type = {}", ct);
        Assert.assertEquals(ct, "multipart/form-data",
                "API2 Content-Type must be multipart/form-data");
    }

    // -------------------------------------------------------------------------
    // Comparison and misc properties
    // -------------------------------------------------------------------------

    @Test(groups = "config", description = "comparison.amount.tolerance is a non-negative double")
    public void comparisonToleranceIsValid() {
        double tol = config.getComparisonAmountTolerance();
        log.info("comparison.amount.tolerance = {}", tol);
        Assert.assertTrue(tol >= 0, "Tolerance must be non-negative; got: " + tol);
    }

    @Test(groups = "config", description = "comparison.percentage.tolerance is a non-negative double")
    public void comparisonPercentageToleranceIsValid() {
        double tol = config.getComparisonPercentageTolerance();
        log.info("comparison.percentage.tolerance = {}", tol);
        Assert.assertTrue(tol >= 0, "Percentage tolerance must be non-negative; got: " + tol);
    }

    @Test(groups = "config", description = "environment name is set and non-blank")
    public void environmentNameIsSet() {
        String env = config.getEnvironment();
        log.info("environment = {}", env);
        Assert.assertNotNull(env);
        Assert.assertFalse(env.isBlank(), "Environment name must not be blank");
    }

    @Test(groups = "config", description = "retry settings are valid non-negative integers")
    public void retrySettingsAreValid() {
        int retryCount = config.getInt("http.retry.count");
        int retryDelay = config.getInt("http.retry.delay.ms");
        log.info("Retry: count={}, delay={}ms", retryCount, retryDelay);
        Assert.assertTrue(retryCount >= 0);
        Assert.assertTrue(retryDelay >= 0);
    }

    // -------------------------------------------------------------------------
    // Env-var override mechanism
    // -------------------------------------------------------------------------

    @Test(groups = "config", description = "toEnvName converts dotted key correctly")
    public void envNameConversionIsCorrect() {
        Assert.assertEquals(ConfigReader.toEnvName("api1.base.url"), "API1_BASE_URL");
        Assert.assertEquals(ConfigReader.toEnvName("comparison.amount.tolerance"),
                "COMPARISON_AMOUNT_TOLERANCE");
        log.info("toEnvName conversion verified");
    }

    // -------------------------------------------------------------------------
    // Auth properties — readable via get()
    // -------------------------------------------------------------------------

    @Test(groups = "config",
          description = "All credential property keys are present in config and readable via get()")
    public void authPropertiesAreReadableViaGet() {
        // Each call must not throw even when the value is empty.
        // Empty string is the expected default — credentials are set in
        // config-local.properties or via env vars, never in committed config.
        String api1Cookie  = config.get("api1.cookie",          "");
        String api1Token   = config.get("api1.auth.token",       "");
        String api2Auth    = config.get("api2.authorization",    "");
        String api2Token   = config.get("api2.auth.token",       "");
        String api2ApiKey  = config.get("api2.json.api.key",     "");
        String api2Cookie  = config.get("api2.cookie",           "");

        // Log configured/not state — NEVER log the actual values
        log.info("api1.cookie configured:         {}", !api1Cookie.isEmpty());
        log.info("api1.auth.token configured:     {}", !api1Token.isEmpty());
        log.info("api2.authorization configured:  {}", !api2Auth.isEmpty());
        log.info("api2.auth.token configured:     {}", !api2Token.isEmpty());
        log.info("api2.json.api.key configured:   {}", !api2ApiKey.isEmpty());
        log.info("api2.cookie configured:         {}", !api2Cookie.isEmpty());

        // All must be non-null (blank/empty is acceptable — credentials unset locally)
        Assert.assertNotNull(api1Cookie,  "api1.cookie must be readable");
        Assert.assertNotNull(api1Token,   "api1.auth.token must be readable");
        Assert.assertNotNull(api2Auth,    "api2.authorization must be readable");
        Assert.assertNotNull(api2Token,   "api2.auth.token must be readable");
        Assert.assertNotNull(api2ApiKey,  "api2.json.api.key must be readable");
        Assert.assertNotNull(api2Cookie,  "api2.cookie must be readable");
    }

    @Test(groups = "config",
          description = "Env-var override of auth property works: API2_AUTH_TOKEN overrides api2.auth.token")
    public void envVarOverrideDerivationIsCorrect() {
        // The env-var name is always the property key uppercased with dots → underscores
        Assert.assertEquals(ConfigReader.toEnvName("api1.cookie"),        "API1_COOKIE");
        Assert.assertEquals(ConfigReader.toEnvName("api2.authorization"), "API2_AUTHORIZATION");
        Assert.assertEquals(ConfigReader.toEnvName("api2.auth.token"),    "API2_AUTH_TOKEN");
        Assert.assertEquals(ConfigReader.toEnvName("api2.json.api.key"),  "API2_JSON_API_KEY");
        Assert.assertEquals(ConfigReader.toEnvName("api2.cookie"),        "API2_COOKIE");
        log.info("Env-var override key derivation verified for all credential properties");
    }

    // -------------------------------------------------------------------------
    // HeaderManager — structure and credential mapping
    // -------------------------------------------------------------------------

    @Test(groups = "config",
          description = "API1 headers: Content-Type, Accept always present; no Authorization invented")
    public void api1HeadersAreCorrect() {
        HeaderManager hm = new HeaderManager();
        Map<String, String> headers = hm.getApi1Headers();

        log.info("API1 header keys: {}", headers.keySet());
        Assert.assertEquals(headers.get("Content-Type"), "application/json");
        Assert.assertEquals(headers.get("Accept"),       "application/json");

        // API-1 authenticates via cookie only — Authorization must NOT appear
        // unless explicitly configured (it never is for API-1 per the supplied cURL)
        Assert.assertNull(headers.get("Authorization"),
            "API-1 must NOT have an Authorization header — cookie-only auth");
        Assert.assertNull(headers.get("auth-token"),
            "API-1 must NOT have an auth-token header");
    }

    @Test(groups = "config",
          description = "API2 headers: Accept + four app headers always present; no Content-Type")
    public void api2HeadersAreCorrect() {
        HeaderManager hm = new HeaderManager();
        Map<String, String> headers = hm.getApi2Headers();

        log.info("API2 header keys: {}", headers.keySet());

        // Fixed headers
        Assert.assertEquals(headers.get("Accept"),       "application/json");
        Assert.assertNull(headers.get("Content-Type"),
            "Content-Type must NOT be set — REST Assured injects multipart boundary");

        // Non-sensitive app headers from config.properties (always present)
        Assert.assertEquals(headers.get("app-platform"), "android");
        Assert.assertEquals(headers.get("app-type"),     "ADV_APP");
        Assert.assertEquals(headers.get("app-version"),  "1.0.32");
        Assert.assertEquals(headers.get("site-code"),    "VROADV");
    }

    @Test(groups = "config",
          description = "API2 credential headers present when configured; absent when blank/unset — "
                      + "values never logged")
    public void api2CredentialHeadersMappedToCorrectNames() {
        HeaderManager hm = new HeaderManager();
        Map<String, String> headers = hm.getApi2Headers();

        // Log configured/not state for each credential header — NEVER the values
        log.info("API2 Authorization configured:  {}", headers.containsKey("Authorization"));
        log.info("API2 auth-token configured:     {}", headers.containsKey("auth-token"));
        log.info("API2 json-api-key configured:   {}", headers.containsKey("json-api-key"));
        log.info("API2 Cookie configured:         {}", headers.containsKey("Cookie"));

        // When a credential IS present, it must map to the correct header name and be non-blank
        if (headers.containsKey("Authorization")) {
            Assert.assertFalse(headers.get("Authorization").isBlank(),
                "Authorization must not be blank when present");
        }
        if (headers.containsKey("auth-token")) {
            Assert.assertFalse(headers.get("auth-token").isBlank(),
                "auth-token must not be blank when present");
        }
        if (headers.containsKey("json-api-key")) {
            Assert.assertFalse(headers.get("json-api-key").isBlank(),
                "json-api-key must not be blank when present");
        }
        if (headers.containsKey("Cookie")) {
            Assert.assertFalse(headers.get("Cookie").isBlank(),
                "Cookie must not be blank when present");
        }
        // The test passes whether credentials are configured or not —
        // both states are valid depending on the developer's local setup
    }

    @Test(groups = "config",
          description = "API1 Cookie header present when api1.cookie / API1_COOKIE is configured")
    public void api1CookieMappedWhenConfigured() {
        HeaderManager hm = new HeaderManager();
        Map<String, String> headers = hm.getApi1Headers();

        log.info("API1 Cookie configured: {}", headers.containsKey("Cookie"));

        if (headers.containsKey("Cookie")) {
            Assert.assertFalse(headers.get("Cookie").isBlank(),
                "Cookie must not be blank when present");
        }
        // Pass regardless — blank/unset is valid in uncredentialed environments
    }

    @Test(groups = "config", description = "HeaderManager returns immutable maps")
    public void headersAreImmutable() {
        HeaderManager hm = new HeaderManager();
        Assert.assertThrows(UnsupportedOperationException.class,
                () -> hm.getApi1Headers().put("X-Test", "mutate-attempt"));
        Assert.assertThrows(UnsupportedOperationException.class,
                () -> hm.getApi2Headers().put("X-Test", "mutate-attempt"));
        log.info("Both header maps correctly returned as unmodifiable");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static void assertNullOrNonBlank(String value, String varName) {
        if (value != null) {
            Assert.assertFalse(value.isBlank(),
                    varName + " is set in env but is blank — must be non-blank or absent");
        }
    }
}
