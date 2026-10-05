package com.vr.portfolioplanner.config;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supplies the JWT access token API-1 now requires in its {@code Authorization} header.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>{@code POST api1.login.url} (multipart: username, password, device, platform) →
 *       response field {@code token} (the session auth token).</li>
 *   <li>{@code POST api1.token.url} with header {@code Auth-Token: <token>} →
 *       response field {@code access_token} (the JWT).</li>
 * </ol>
 *
 * <p>The JWT is cached process-wide and refreshed shortly before its {@code exp} claim, so
 * concurrent data-provider threads share one login. When login credentials are not configured
 * or either call fails, {@link #getAccessToken()} returns empty (never throws) and API-1 is
 * called without a token, so the row fails visibly with the API's own 401.
 *
 * <p>Security: credentials and tokens are never logged.
 */
public final class Api1TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(Api1TokenProvider.class);

    /** Refresh this many seconds before the JWT actually expires. */
    private static final long REFRESH_MARGIN_SEC = 60;
    /** Used when the JWT has no readable exp claim. */
    private static final long FALLBACK_TTL_SEC = 300;

    private static final Pattern EXP = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

    private static final Api1TokenProvider INSTANCE = new Api1TokenProvider();

    private final ConfigReader config = ConfigReader.getInstance();

    private String cachedToken;
    private long   cachedExpiryEpochSec;

    private Api1TokenProvider() { }

    public static Api1TokenProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Discards the cached JWT and immediately runs a fresh login → access-token flow.
     * Used after API-1 answers 401. Thread-safe: the lock is shared with {@link #getAccessToken()}.
     */
    public synchronized Optional<String> forceRefreshToken() {
        log.info("Forcing API-1 access token refresh");
        cachedToken = null;
        cachedExpiryEpochSec = 0;
        return getAccessToken();
    }

    /** Returns a valid JWT access token, logging in / refreshing as needed. */
    public synchronized Optional<String> getAccessToken() {
        long now = System.currentTimeMillis() / 1000;
        if (cachedToken != null && now < cachedExpiryEpochSec - REFRESH_MARGIN_SEC) {
            return Optional.of(cachedToken);
        }
        cachedToken = null;

        String username = config.get("api1.login.username", "");
        String password = config.get("api1.login.password", "");
        if (username.isBlank() || password.isBlank()) {
            log.warn("API-1 login credentials (api1.login.username / api1.login.password) "
                + "not configured — no access token will be sent");
            return Optional.empty();
        }

        try {
            String authToken = login(username, password);
            String jwt       = createAccessToken(authToken);
            cachedToken          = jwt;
            cachedExpiryEpochSec = expiryOf(jwt, now);
            log.info("API-1 access token obtained (valid for ~{}s)", cachedExpiryEpochSec - now);
            return Optional.of(jwt);
        } catch (Exception e) {
            log.error("API-1 access token acquisition failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String login(String username, String password) {
        var req = RestAssured.given()
            .multiPart("username", username)
            .multiPart("password", password)
            .multiPart("device",   config.get("api1.login.device",   "android"))
            .multiPart("platform", config.get("api1.login.platform", "VROAPP"));
        header(req, "Authorization",   "api1.login.authorization");
        header(req, "json-api-key",    "api1.login.json.api.key");
        header(req, "app-platform",    "api1.login.header.app.platform");
        header(req, "app-type",        "api1.login.header.app.type");
        header(req, "app-version",     "api1.login.header.app.version");
        header(req, "site-code",       "api1.login.header.site.code");

        Response r = req.post(config.get("api1.login.url"));
        String token = r.getStatusCode() == 200 ? r.jsonPath().getString("token") : null;
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("login failed — HTTP " + r.getStatusCode());
        }
        return token;
    }

    private String createAccessToken(String authToken) {
        Response r = RestAssured.given()
            .header("Auth-Token", authToken)
            .post(config.get("api1.token.url"));
        String jwt = r.getStatusCode() == 200 ? r.jsonPath().getString("access_token") : null;
        if (jwt == null || jwt.isBlank()) {
            throw new IllegalStateException("access_token creation failed — HTTP " + r.getStatusCode());
        }
        return jwt;
    }

    private void header(io.restassured.specification.RequestSpecification req,
                        String name, String key) {
        String v = config.get(key, "");
        if (!v.isBlank()) req.header(name, v);
    }

    private static long expiryOf(String jwt, long now) {
        try {
            String payload = new String(
                Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
            Matcher m = EXP.matcher(payload);
            if (m.find()) return Long.parseLong(m.group(1));
        } catch (Exception ignored) {
            // fall through to default TTL
        }
        return now + FALLBACK_TTL_SEC;
    }
}
