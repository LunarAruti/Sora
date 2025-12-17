package ucadmin.network;

import org.json.JSONObject;
import ucadmin.util.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Network Request Object (NRO): construct once, fill fields, call seal(), then hand to the client.
 *
 * Template variables in {@code path}:
 *   - {var}   : REQUIRED path segment (will be URL-encoded)
 *   - {?name} : OPTIONAL query parameter (included only if provided in vars)
 *   - {*rest} : REQUIRED "splat" tail (MUST be pre-encoded; appended as-is)
 *
 * Defaults (applied during construction/seal()):
 *   - type           = GET
 *   - authStrategy   = NONE
 *   - timeout        = 20 seconds (wall-clock)
 *   - responseType   = JSON_OBJECT
 *   - projectionPath = null (return entire JSON body)
 *   - returnAlias    = name
 *   - cachePath      = mem/database/network/{service}/{returnAlias} (in-memory only)
 *   - priority       = NORMAL
 *   - retryPolicy    = idempotent-only, 3 attempts, exponential backoff, 429/5xx/timeout/network
 *   - failureMode    = FAIL_FAST
 *   - followRedirects = true, maxRedirects = 5
 *   - treatOtherStatusAsError = true (non-2xx treated as error unless overridden)
 *   - collectMetrics = true
 *
 * Validation enforced by seal():
 *   - requestUrl, path, responseType must be present/valid
 *   - path must start with '/'
 *   - all {var} present in vars; {*rest} non-empty if used
 *   - GET/DELETE must not have a body; POST/PUT/PATCH must have a JSON body
 *   - auth rules based on strategy (see setters)
 *   - timeout in [100ms, 120000ms]
 *   - projectionPath allowed only with JSON_* response types (null = full body)
 *   - retryPolicy and redirect settings validated for sane ranges
 *
 * Immutability:
 *   - After seal(), ALL setters throw IllegalStateException. Use a new instance if you need changes.
 */
public final class NetworkRequest {

    // ---------------- Enums ----------------

    /** HTTP verb + body semantics for this request. */
    public enum Type { GET, POST_JSON, PUT_JSON, PATCH_JSON, DELETE }

    /** Decoder contract the client will enforce on the response. */
    public enum ResponseType { JSON_OBJECT, JSON_ARRAY }

    /** How credentials are attached. */
    public enum AuthStrategy { NONE, BEARER, API_KEY_HEADER, CUSTOM_SIGNER }

    /**
     * Logical priority hint for the network worker.
     * HIGH requests are processed before NORMAL, then LOW/BACKGROUND.
     */
    public enum Priority {
        HIGH,
        NORMAL,
        LOW,
        BACKGROUND
    }

    /**
     * How the retry system is allowed to behave when a request fails.
     */
    public enum RetryMode {
        /** Never retry; exactly one attempt is made. */
        NEVER,
        /**
         * Retry only when the operation is considered idempotent or safe
         * (typically GET/DELETE, or POST/PUT/PATCH with an idempotency key).
         */
        IDEMPOTENT_ONLY,
        /**
         * Caller explicitly allows retries regardless of method semantics.
         * Use with care for non-idempotent operations.
         */
        ALWAYS
    }

    /**
     * What the network layer should do when a request ultimately fails.
     */
    public enum FailureMode {
        /**
         * Throw a NetworkException from the network layer. The cache path
         * may or may not contain a partial/failed result depending on the
         * implementation of the worker.
         */
        FAIL_FAST,
        /**
         * Always write a structured error JSON into the cachePath
         * (e.g. { "ok": false, "error": { ... } }) and avoid throwing.
         * The caller is expected to inspect the result via DBM.
         */
        WRITE_ERROR_JSON
    }

    // ---------------- Retry policy model ----------------

    /**
     * Immutable description of how and when the worker may retry this request.
     * The network worker reads this and decides if/when to schedule additional
     * attempts for timeouts, 5xx, 429s, etc.
     */
    public static final class RetryPolicy {
        private final RetryMode mode;
        private final int maxAttempts;
        private final long initialBackoffMs;
        private final double backoffFactor;
        private final Set<Integer> retryOnStatus;
        private final boolean retryOnTimeout;
        private final boolean retryOnNetworkError;

        /**
         * Constructs a retry policy.
         *
         * @param mode                High-level mode controlling when retries are permitted.
         * @param maxAttempts         Total attempts allowed (first attempt + retries). Must be >= 1.
         * @param initialBackoffMs    Delay before the first retry in milliseconds (0 for no delay).
         * @param backoffFactor       Multiplier applied to the delay after each attempt (e.g. 2.0 for exponential backoff).
         * @param retryOnStatus       HTTP statuses that are considered retryable (e.g. 429, 500, 502, 503, 504).
         * @param retryOnTimeout      Whether timeouts should be retried.
         * @param retryOnNetworkError Whether generic network I/O errors should be retried.
         */
        public RetryPolicy(
                RetryMode mode,
                int maxAttempts,
                long initialBackoffMs,
                double backoffFactor,
                Set<Integer> retryOnStatus,
                boolean retryOnTimeout,
                boolean retryOnNetworkError
        ) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.maxAttempts = maxAttempts;
            this.initialBackoffMs = initialBackoffMs;
            this.backoffFactor = backoffFactor;
            this.retryOnStatus = (retryOnStatus == null || retryOnStatus.isEmpty())
                    ? Collections.emptySet()
                    : Set.copyOf(retryOnStatus);
            this.retryOnTimeout = retryOnTimeout;
            this.retryOnNetworkError = retryOnNetworkError;
        }

        /**
         * Convenience factory for a reasonable default policy:
         * - mode              = IDEMPOTENT_ONLY
         * - maxAttempts       = 3
         * - initialBackoffMs  = 250
         * - backoffFactor     = 2.0
         * - retryOnStatus     = {429, 500, 502, 503, 504}
         * - retryOnTimeout    = true
         * - retryOnNetworkError = true
         */
        public static RetryPolicy newDefaultIdempotent() {
            return new RetryPolicy(
                    RetryMode.IDEMPOTENT_ONLY,
                    3,
                    250,
                    2.0,
                    Set.of(429, 500, 502, 503, 504),
                    true,
                    true
            );
        }

        public RetryMode getMode() { return mode; }
        public int getMaxAttempts() { return maxAttempts; }
        public long getInitialBackoffMs() { return initialBackoffMs; }
        public double getBackoffFactor() { return backoffFactor; }
        public Set<Integer> getRetryOnStatus() { return retryOnStatus; }
        public boolean isRetryOnTimeout() { return retryOnTimeout; }
        public boolean isRetryOnNetworkError() { return retryOnNetworkError; }
    }

    private static final RetryPolicy DEFAULT_RETRY_POLICY = RetryPolicy.newDefaultIdempotent();

    // ---------------- Identity ----------------

    /** Logical group (for logs/rate buckets), e.g., "roblox". */
    private final String service;

    /** Endpoint name (for logs/alias), e.g., "GetFriends". */
    private final String name;

    /** Trace correlation id (auto if null on seal). */
    private String traceId;

    // ---------------- Request shape ----------------

    /** HTTP type; defaults to GET when not explicitly set. */
    private Type type = Type.GET;

    /** Absolute base URL, e.g., "https://apis.roblox.com". */
    private String requestUrl;

    /**
     * Path template starting with '/'.
     * Supports: {var} (required path), {?q} (optional query), {*rest} (pre-encoded tail).
     */
    private String path;

    /** Unified variables used by the template (both path + optional query vars). */
    private final Map<String, Object> vars = new LinkedHashMap<>();

    /** Caller-provided headers; merged with Content-Type/Auth/Idempotency during seal(). */
    private final Map<String, String> headers = new LinkedHashMap<>();

    /** Only supported request body type; required for POST/PUT/PATCH JSON. */
    private JSONObject jsonBody;

    // ---------------- Auth ----------------

    /** Auth strategy (default NONE). */
    private AuthStrategy authStrategy = AuthStrategy.NONE;

    /** Header name for API_KEY_HEADER (e.g., "X-API-Key"). For BEARER we force "Authorization". */
    private String authKeyName;

    /** Supplier for token/key; called at execution time. */
    private Supplier<String> authProvider;

    /** Advanced: signer called after URL/body render; may add signatures to headers. */
    private BiConsumer<RequestDraft, Map<String, String>> customSigner;

    // ---------------- Resilience hints ----------------

    /** Per-request wall timeout (default 20s). */
    private Duration timeout = Duration.ofSeconds(20);

    /** Optional more granular timeouts (fallback to {@link #timeout} when null). */
    private Duration connectTimeout;
    private Duration readTimeout;

    /** Rate limiter lane (group), e.g., "roblox.read" (optional; defaults to service). */
    private String rateBucket;

    /** Circuit breaker key (optional; defaults to host(requestUrl)). */
    private String circuitKey;

    /** Safe write retries when upstream supports idempotent POST/PUT/PATCH. */
    private String idempotencyKey;

    /** Overall priority hint for scheduling in the network worker. */
    private Priority priority = Priority.NORMAL;

    /** How the worker may retry this request (null → default applied on seal). */
    private RetryPolicy retryPolicy;

    /** Behavior when the request ultimately fails after all attempts. */
    private FailureMode failureMode = FailureMode.FAIL_FAST;

    // ---------------- Response contract ----------------

    /** Expected body shape (default JSON_OBJECT). */
    private ResponseType responseType = ResponseType.JSON_OBJECT;

    /** Optional DBM-style JSON path projection; null = entire JSON. */
    private String projectionPath;

    /**
     * Which HTTP status codes are considered "successful" for this request.
     * If null, the network layer may treat any 2xx as acceptable by default.
     */
    private Set<Integer> acceptableStatusCodes;

    /**
     * When true, any status code not in {@link #acceptableStatusCodes} (or
     * outside the default 2xx range when that set is null) is treated as an error.
     */
    private boolean treatOtherStatusAsError = true;

    /** Friendly label for logs/cache; defaults to name. */
    private String returnAlias;

    // ---------------- Caching hint (mem/ mount only) ----------------

    /**
     * Default: mem/database/network/{service}/{returnAlias}
     * This is an in-memory path (not persisted). Future options may snapshot to disk.
     */
    private String cachePath;

    /** Optional TTL (seconds) for the in-memory entry. */
    private Integer cacheTtlSeconds;

    /** Optional maximum response size in bytes (null = no explicit per-request cap). */
    private Long maxResponseBytes;

    /** Optional whitelist of allowed Content-Types (null = no per-request restriction). */
    private Set<String> allowedContentTypes;

    /** Whether redirects are followed for this request (default true). */
    private boolean followRedirects = true;

    /** Maximum number of redirects to follow when {@link #followRedirects} is true. */
    private int maxRedirects = 5;

    /** Whether to collect and store metrics like latency, attempts, etc. */
    private boolean collectMetrics = true;

    /**
     * Optional key for request deduplication/coalescing.
     * When set, the worker may merge in-flight identical requests keyed by this value.
     */
    private String dedupeKey;

    // ---------------- Derived after seal() ----------------

    private boolean sealed;

    /** requestUrl + rendered path + encoded query. */
    private String finalUrl;

    /** Caller headers merged with Content-Type/Auth/Idempotency (ready-to-send). */
    private Map<String, String> renderedHeaders;

    /** Parsed template plan (tokens and var kinds). */
    private TemplatePlan plan;

    // ------------- ctor -------------

    /**
     * Creates a new NetworkRequest tied to a logical service and endpoint name.
     *
     * @param service Logical service/group name, used in logs and as a default rate limiter bucket.
     * @param name    Human-readable endpoint name, used in logs and as the default returnAlias.
     */
    public NetworkRequest(String service, String name) {
        this.service = nonEmpty(service, "service");
        this.name = nonEmpty(name, "name");
        Logger.log(Logger.TAG.REQUEST,
                "NRO<init>: created request object for service=" + service + ", name=" + name);
    }


    // ------------- fluent setters (all guard ensureNotSealed) -------------

    // identity

    /**
     * Sets an explicit trace/correlation id to tag this request in logs and metrics.
     * If not provided, a random UUID is generated at seal() time.
     */
    public NetworkRequest setTraceId(String id) {
        ensureNotSealed();
        this.traceId = id;
        return this;
    }

    // request shape

    /**
     * Sets the HTTP method/type (GET, POST_JSON, etc.).
     * Defaults to GET when not called.
     */
    public NetworkRequest setType(Type t) {
        ensureNotSealed();
        this.type = Objects.requireNonNull(t);
        return this;
    }

    /**
     * Sets the absolute base URL for the request, e.g. "https://apis.example.com".
     * Must include a scheme and host; validated on seal().
     */
    public NetworkRequest setRequestUrl(String url) {
        ensureNotSealed();
        this.requestUrl = url;
        return this;
    }

    /**
     * Sets the path template for this request.
     * Must start with '/' and may contain {var}, {?q}, and {*rest} placeholders.
     */
    public NetworkRequest setPath(String p) {
        ensureNotSealed();
        this.path = p;
        return this;
    }

    /**
     * Binds a single template variable used in the path or optional query parameters.
     * The same var map is used for {var}, {?q}, and {*rest}.
     */
    public NetworkRequest putVar(String key, Object val) {
        ensureNotSealed();
        this.vars.put(key, val);
        return this;
    }

    /**
     * Binds multiple template variables at once.
     * Null map is ignored; existing keys are overwritten.
     */
    public NetworkRequest putVars(Map<String, ?> m) {
        ensureNotSealed();
        if (m != null) m.forEach(this::putVar);
        return this;
    }

    /**
     * Sets a custom header on this request.
     * Transport-managed headers like Host, Content-Length, Accept-Encoding are rejected.
     */
    public NetworkRequest header(String k, String v) {
        ensureNotSealed();
        if (k == null || k.isBlank()) throw new IllegalArgumentException("Header name is empty");
        String kl = k.trim().toLowerCase(Locale.ROOT);
        if (kl.equals("host") || kl.equals("content-length") || kl.equals("accept-encoding")) {
            throw new IllegalArgumentException("Header '" + k + "' is managed by the HTTP client and cannot be set");
        }
        this.headers.put(k, v);
        return this;
    }

    /**
     * Sets the JSON body for POST/PUT/PATCH JSON requests.
     * For GET/DELETE, providing a body is forbidden and will fail at seal().
     */
    public NetworkRequest setJsonBody(JSONObject body) {
        ensureNotSealed();
        this.jsonBody = body;
        return this;
    }

    // auth

    /**
     * Configures Bearer token authentication.
     * A token provider is invoked at execution time to obtain the current token.
     */
    public NetworkRequest setAuthBearer(Supplier<String> tokenProvider) {
        ensureNotSealed();
        this.authStrategy = AuthStrategy.BEARER;
        this.authKeyName = "Authorization";
        this.authProvider = tokenProvider;
        return this;
    }

    /**
     * Configures an API-key style header, such as "X-API-Key".
     * The key provider is invoked at execution time.
     */
    public NetworkRequest setAuthApiKey(String headerName, Supplier<String> keyProvider) {
        ensureNotSealed();
        this.authStrategy = AuthStrategy.API_KEY_HEADER;
        this.authKeyName = nonEmpty(headerName, "authKeyName");
        this.authProvider = keyProvider;
        return this;
    }

    /**
     * Configures a custom signer callback, invoked after URL/body are rendered.
     * The signer may add additional headers (such as HMAC signatures).
     */
    public NetworkRequest setAuthCustom(BiConsumer<RequestDraft, Map<String, String>> signer) {
        ensureNotSealed();
        this.authStrategy = AuthStrategy.CUSTOM_SIGNER;
        this.customSigner = signer;
        return this;
    }

    // resilience

    /**
     * Sets the wall-clock timeout for this request.
     * Must be between 100ms and 120s inclusive.
     */
    public NetworkRequest setTimeout(Duration d) {
        ensureNotSealed();
        this.timeout = Objects.requireNonNull(d);
        return this;
    }

    /**
     * Sets a more granular connection timeout.
     * If not set, the network implementation falls back to {@link #getTimeout()}.
     */
    public NetworkRequest setConnectTimeout(Duration d) {
        ensureNotSealed();
        this.connectTimeout = d;
        return this;
    }

    /**
     * Sets a more granular read timeout.
     * If not set, the network implementation falls back to {@link #getTimeout()}.
     */
    public NetworkRequest setReadTimeout(Duration d) {
        ensureNotSealed();
        this.readTimeout = d;
        return this;
    }

    /**
     * Assigns this request to a specific rate limiter bucket.
     * If not set, the service name is used as the default bucket.
     */
    public NetworkRequest setRateBucket(String b) {
        ensureNotSealed();
        this.rateBucket = b;
        return this;
    }

    /**
     * Sets the logical circuit breaker key.
     * If not set, the host component of the base URL is used.
     */
    public NetworkRequest setCircuitKey(String k) {
        ensureNotSealed();
        this.circuitKey = k;
        return this;
    }

    /**
     * Sets a unique idempotency key for this request.
     * Upstream services that honor idempotency keys can safely deduplicate retries.
     */
    public NetworkRequest setIdempotencyKey(String k) {
        ensureNotSealed();
        this.idempotencyKey = k;
        return this;
    }

    /**
     * Sets the priority hint for this request (HIGH/NORMAL/LOW/BACKGROUND).
     * A higher priority may be scheduled sooner by the network worker.
     */
    public NetworkRequest setPriority(Priority p) {
        ensureNotSealed();
        this.priority = Objects.requireNonNull(p);
        return this;
    }

    /**
     * Sets the retry policy used when the request fails due to timeouts, 5xx/429, etc.
     * When not set, a reasonable default idempotent-only policy is applied.
     */
    public NetworkRequest setRetryPolicy(RetryPolicy policy) {
        ensureNotSealed();
        this.retryPolicy = policy;
        return this;
    }

    /**
     * Sets how the network layer should behave after a terminal failure:
     * throw immediately (FAIL_FAST) or always write an error JSON (WRITE_ERROR_JSON).
     */
    public NetworkRequest setFailureMode(FailureMode mode) {
        ensureNotSealed();
        this.failureMode = Objects.requireNonNull(mode);
        return this;
    }

    // response

    /**
     * Declares the expected JSON response shape for this request.
     * JSON_OBJECT assumes a top-level object; JSON_ARRAY assumes a top-level array.
     */
    public NetworkRequest setResponseType(ResponseType rt) {
        ensureNotSealed();
        this.responseType = Objects.requireNonNull(rt);
        return this;
    }

    /**
     * Sets an optional DBM-style projection path that will be applied to the JSON body.
     * Null or blank means the entire JSON response is stored.
     */
    public NetworkRequest setProjectionPath(String p) {
        ensureNotSealed();
        this.projectionPath = p;
        return this;
    }

    /**
     * Declares which HTTP status codes should be treated as "successful" for this request.
     * If left null, the implementation may treat the entire 2xx range as success.
     */
    public NetworkRequest setAcceptableStatusCodes(Set<Integer> codes) {
        ensureNotSealed();
        if (codes == null || codes.isEmpty()) {
            this.acceptableStatusCodes = null;
        } else {
            this.acceptableStatusCodes = Set.copyOf(codes);
        }
        return this;
    }

    /**
     * Controls whether any status code outside of {@link #acceptableStatusCodes} (or
     * outside a default 2xx range when that is null) should be treated as an error.
     */
    public NetworkRequest setTreatOtherStatusAsError(boolean treatAsError) {
        ensureNotSealed();
        this.treatOtherStatusAsError = treatAsError;
        return this;
    }

    /**
     * Sets a friendly alias used in logs and as part of the default cache path.
     * If not set, the endpoint {@link #name} is used as the alias.
     * This does not affect the URL or HTTP behavior, only how results are labeled.
     */
    public NetworkRequest setReturnAlias(String a) {
        ensureNotSealed();
        this.returnAlias = a;
        return this;
    }

    // caching / policy

    /**
     * Overrides the default cache path where the response will be written via DBM.
     * By default, mem/database/network/{service}/{returnAlias} is used.
     */
    public NetworkRequest setCachePath(String path) {
        ensureNotSealed();
        this.cachePath = path;
        return this;
    }

    /**
     * Sets an optional TTL (in seconds) for the cached entry.
     * Null means no per-request TTL; a global cache policy may still apply.
     */
    public NetworkRequest setCacheTtlSeconds(Integer ttl) {
        ensureNotSealed();
        this.cacheTtlSeconds = ttl;
        return this;
    }

    /**
     * Sets a per-request maximum response size in bytes.
     * If the response exceeds this size, the network layer may abort or treat it as an error.
     */
    public NetworkRequest setMaxResponseBytes(Long maxBytes) {
        ensureNotSealed();
        this.maxResponseBytes = maxBytes;
        return this;
    }

    /**
     * Configures a whitelist of allowed Content-Types for this response, e.g. "application/json".
     * If null or empty, no per-request content-type restriction is applied.
     */
    public NetworkRequest setAllowedContentTypes(Set<String> types) {
        ensureNotSealed();
        if (types == null || types.isEmpty()) {
            this.allowedContentTypes = null;
        } else {
            Set<String> cleaned = new HashSet<>();
            for (String t : types) {
                if (t != null && !t.isBlank()) {
                    cleaned.add(t.trim().toLowerCase(Locale.ROOT));
                }
            }
            this.allowedContentTypes = cleaned.isEmpty() ? null : Set.copyOf(cleaned);
        }
        return this;
    }

    /**
     * Controls whether HTTP redirects are followed for this request.
     * Defaults to true.
     */
    public NetworkRequest setFollowRedirects(boolean follow) {
        ensureNotSealed();
        this.followRedirects = follow;
        return this;
    }

    /**
     * Sets the maximum number of redirects the network layer may follow.
     * Must be >= 0. Ignored when followRedirects is false.
     */
    public NetworkRequest setMaxRedirects(int max) {
        ensureNotSealed();
        this.maxRedirects = max;
        return this;
    }

    /**
     * Enables or disables metrics collection for this request.
     * When true (default), the network layer may record latency, attempts, and other info
     * alongside the response payload.
     */
    public NetworkRequest setCollectMetrics(boolean collect) {
        ensureNotSealed();
        this.collectMetrics = collect;
        return this;
    }

    /**
     * Sets a deduplication key used to coalesce identical in-flight requests.
     * When multiple requests share the same key, the worker may reuse a single network call.
     */
    public NetworkRequest setDedupeKey(String key) {
        ensureNotSealed();
        this.dedupeKey = key;
        return this;
    }

    // ------------- sealing & validation -------------

    /** Validate all required fields, compute finalUrl/renderedHeaders/defaults. */
    public synchronized NetworkRequest seal() {
        Logger.log(Logger.TAG.REQUEST,
                "NRO.seal(): sealing request " + service + "/" + name +
                        " traceId=" + (traceId == null ? "<null>" : traceId));

        if (sealed) {
            Logger.log(Logger.TAG.DEBUG,
                    "NRO.seal(): request already sealed " + service + "/" + name);
            return this;
        }

        // identity defaults
        if (returnAlias == null || returnAlias.isBlank()) returnAlias = name;
        if (traceId == null || traceId.isBlank()) traceId = UUID.randomUUID().toString();

        // request shape
        if (type == null) type = Type.GET; // defensive, should already be defaulted
        if (requestUrl == null || !isAbsoluteUrl(requestUrl))
            fail("INVALID_REQUEST_URL: Must include scheme and host: " + requestUrl);
        if (path == null || path.isBlank() || !path.startsWith("/"))
            fail("MISSING_PATH: Path template must start with '/'.");

        // parse template tokens
        this.plan = TemplatePlan.parse(path);

        // variable checks
        for (String req : plan.requiredPathVars) {
            if (!vars.containsKey(req) || vars.get(req) == null) {
                fail("UNBOUND_VAR: Missing value for {" + req + "} in path '" + path + "'");
            }
        }
        if (plan.splatVar != null) {
            Object v = vars.get(plan.splatVar);
            if (v == null || v.toString().isBlank())
                fail("UNBOUND_SPLAT: Missing or empty value for {*" + plan.splatVar + "}");
            // splat must be pre-encoded; we don't enforce encoding here.
        }

        // body rules (JSON-only model)
        switch (type) {
            case POST_JSON, PUT_JSON, PATCH_JSON -> {
                if (jsonBody == null) fail("BODY_REQUIRED: " + type + " requires a JSON body.");
            }
            case GET, DELETE -> {
                if (jsonBody != null) fail("BODY_FORBIDDEN: " + type + " cannot include a body.");
            }
        }

        // auth rules
        switch (authStrategy) {
            case NONE -> {}
            case BEARER -> {
                if (authProvider == null) fail("AUTH_PROVIDER_REQUIRED: BEARER requires token provider.");
                this.authKeyName = "Authorization"; // force
            }
            case API_KEY_HEADER -> {
                if (authProvider == null) fail("AUTH_PROVIDER_REQUIRED: API_KEY_HEADER requires key provider.");
                if (authKeyName == null || authKeyName.isBlank())
                    fail("AUTH_KEY_NAME_REQUIRED: Provide header name for API_KEY_HEADER.");
            }
            case CUSTOM_SIGNER -> {
                if (customSigner == null) fail("CUSTOM_SIGNER_REQUIRED: Provide a signer callback.");
            }
        }

        // resilience
        long ms = timeout.toMillis();
        if (ms < 100 || ms > 120_000)
            fail("TIMEOUT_OUT_OF_RANGE: " + ms + "ms (allowed 100..120000)");

        if (maxRedirects < 0) {
            fail("MAX_REDIRECTS_INVALID: must be >= 0 (was " + maxRedirects + ")");
        }

        if (retryPolicy == null) {
            this.retryPolicy = DEFAULT_RETRY_POLICY;
        }
        validateRetryPolicy(this.retryPolicy);

        // response contract
        if (responseType == null) fail("INVALID_RESPONSE_TYPE: Response type must be set.");
        if (projectionPath != null && !projectionPath.isBlank()) {
            // light sanity check; full DBM-path validation can be plugged later
            if (projectionPath.contains(".."))
                fail("INVALID_PROJECTION: Suspicious path '" + projectionPath + "'");
        }

        // compute URL + headers
        this.finalUrl = renderFinalUrl();
        this.renderedHeaders = renderHeaders();

        // default cache path (in-memory only)
        if (cachePath == null || cachePath.isBlank()) {
            // Note: simple default; different var combos under same alias may collide.
            // We'll add param fingerprinting when we wire the mem cache.
            String fp = Integer.toHexString(vars.hashCode() ^
                    path.hashCode() ^
                    type.hashCode() ^
                    (authStrategy != null ? authStrategy.hashCode() : 0));

            this.cachePath = "database/network/" + service + "/" + returnAlias + "-" + fp;
        }

        sealed = true;
        Logger.log(Logger.TAG.REQUEST,
                "NRO.seal(): SUCCESS finalUrl=" + finalUrl + " cachePath=" + cachePath);
        return this;
    }

    public boolean isSealed() { return sealed; }

    // ------------- rendering helpers -------------

    /** Build URL = requestUrl + rendered path + encoded query for all {?vars} present. */
    private String renderFinalUrl() {
        StringBuilder pathOut = new StringBuilder();
        for (TemplateToken t : plan.tokens) {
            switch (t.kind) {
                case LITERAL -> pathOut.append(t.value);
                case REQ_VAR -> pathOut.append(encodePath(vars.get(t.value)));
                case SPLAT   -> pathOut.append(String.valueOf(vars.get(t.value))); // pre-encoded
                case OPT_QUERY -> { /* query handled below */ }
            }
        }
        StringBuilder q = new StringBuilder();
        boolean first = true;
        for (String qName : plan.optionalQueryVars) {
            Object val = vars.get(qName);
            if (val == null) continue;
            if (first) { q.append('?'); first = false; } else q.append('&');
            q.append(URLEncoder.encode(qName, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(String.valueOf(val), StandardCharsets.UTF_8));
        }
        Logger.log(Logger.TAG.DEBUG,
                "NRO.renderFinalUrl(): computed finalUrl=" + pathOut);
        return stripTrailingSlash(requestUrl) + pathOut + q;
    }

    private Map<String, String> renderHeaders() {
        Map<String, String> out = new LinkedHashMap<>(this.headers);

        // content-type for JSON bodies
        if (type == Type.POST_JSON || type == Type.PUT_JSON || type == Type.PATCH_JSON) {
            out.putIfAbsent("Content-Type", "application/json; charset=utf-8");
        }

        // auth
        switch (authStrategy) {
            case NONE -> {}
            case BEARER -> {
                String tok = Objects.requireNonNull(authProvider.get(), "Bearer provider returned null");
                out.put("Authorization", "Bearer " + tok);
            }
            case API_KEY_HEADER -> {
                String key = Objects.requireNonNull(authProvider.get(), "API key provider returned null");
                out.put(authKeyName, key);
            }
            case CUSTOM_SIGNER -> {
                RequestDraft draft = new RequestDraft(type, finalUrl, jsonBody);
                Map<String, String> mutable = new LinkedHashMap<>(out);
                customSigner.accept(draft, mutable);
                out.clear(); out.putAll(mutable);
            }
        }

        // idempotency (safe POST/PATCH/PUT retries if upstream supports it)
        if (idempotencyKey != null && !idempotencyKey.isBlank() &&
                (type == Type.POST_JSON || type == Type.PUT_JSON || type == Type.PATCH_JSON)) {
            out.put("Idempotency-Key", idempotencyKey);
        }
        Logger.log(Logger.TAG.DEBUG,
                "NRO.renderHeaders(): rendered headers for " + service + "/" + name +
                        " -> count=" + out.size());
        return out;
    }

    // ------------- public getters used by the client -------------

    public String getService() { return service; }
    public String getName() { return name; }
    public String getTraceId() { return traceId; }
    public Type getType() { return type; }
    public String getFinalUrl() { ensureSealed(); return finalUrl; }
    public Map<String, String> getRenderedHeaders() { ensureSealed(); return renderedHeaders; }
    public JSONObject getJsonBody() { return jsonBody; }
    public ResponseType getResponseType() { return responseType; }
    public String getProjectionPath() { return projectionPath; }
    public String getReturnAlias() { return returnAlias; }
    public Duration getTimeout() { return timeout; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public String getRateBucket() { return (rateBucket != null && !rateBucket.isBlank()) ? rateBucket : service; }
    public String getCircuitKey() { return (circuitKey != null && !circuitKey.isBlank()) ? circuitKey : URI.create(requestUrl).getHost(); }
    public String getCachePath() { return cachePath; }
    public Integer getCacheTtlSeconds() { return cacheTtlSeconds; }
    public Priority getPriority() { return priority; }
    public RetryPolicy getRetryPolicy() { return retryPolicy; }
    public FailureMode getFailureMode() { return failureMode; }
    public Set<Integer> getAcceptableStatusCodes() { return acceptableStatusCodes; }
    public boolean isTreatOtherStatusAsError() { return treatOtherStatusAsError; }
    public Long getMaxResponseBytes() { return maxResponseBytes; }
    public Set<String> getAllowedContentTypes() { return allowedContentTypes; }
    public boolean isFollowRedirects() { return followRedirects; }
    public int getMaxRedirects() { return maxRedirects; }
    public boolean isCollectMetrics() { return collectMetrics; }
    public String getDedupeKey() { return dedupeKey; }

    // ------------- dev ergonomics -------------

    /** Redacted cURL for debugging (never prints secrets). */
    public String toCurl() {
        ensureSealed();
        StringBuilder sb = new StringBuilder("curl -i ");
        sb.append("-X ").append(type.name().replace('_','-')).append(' ');
        for (var e : renderedHeaders.entrySet()) {
            String k = e.getKey();
            String v = redactIfSensitive(k, e.getValue());
            sb.append("-H ").append('"').append(k).append(": ").append(v).append('"').append(' ');
        }
        if (jsonBody != null) {
            String body = jsonBody.toString();
            // optional small truncation for massive payloads
            if (body.length() > 4096) body = body.substring(0, 4096) + " …";
            sb.append("--data-raw '").append(body).append("' ");
        }
        sb.append('"').append(finalUrl).append('"');
        return sb.toString();
    }

    // ------------- internals -------------

    private static String nonEmpty(String s, String field) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Missing " + field);
        return s;
    }
    private static void fail(String msg) {
        Logger.log(Logger.TAG.ERROR, "NRO validation failed: " + msg);
        throw new IllegalStateException(msg);
    }
    private void ensureSealed() { if (!sealed) fail("REQUEST_NOT_SEALED: Call seal() first."); }
    private void ensureNotSealed() {
        if (sealed) {
            Logger.log(Logger.TAG.ERROR,
                    "NRO: attempted mutation after seal() for " + service + "/" + name);
            throw new IllegalStateException("REQUEST_IMMUTABLE: This NetworkRequest is sealed.");
        }
    }

    private static boolean isAbsoluteUrl(String u) {
        try { URI uri = URI.create(u); return uri.getScheme()!=null && uri.getHost()!=null; }
        catch (Exception e) { return false; }
    }
    private static String stripTrailingSlash(String base) {
        if (base.endsWith("/") && base.length() > 1) return base.substring(0, base.length() - 1);
        return base;
    }
    private static String encodePath(Object v) {
        String s = String.valueOf(Objects.requireNonNull(v, "Path var null"));
        // Encode path *segment*; '+' -> '%20' for spaces to avoid interpretation as query.
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static String redactIfSensitive(String k, String v) {
        String kl = k.toLowerCase(Locale.ROOT);
        if (kl.equals("authorization") || kl.equals("x-api-key") || kl.equals("cookie") || kl.equals("proxy-authorization")) {
            return "REDACTED";
        }
        return v;
    }

    private void validateRetryPolicy(RetryPolicy p) {
        Logger.log(Logger.TAG.DEBUG,
                "NRO.validateRetryPolicy(): validating policy for " + service + "/" + name);
        if (p == null) fail("RETRY_POLICY_NULL: retry policy must not be null after defaults.");
        if (p.getMaxAttempts() < 1) {
            fail("RETRY_POLICY_INVALID: maxAttempts must be >= 1 (was " + p.getMaxAttempts() + ")");
        }
        if (p.getInitialBackoffMs() < 0) {
            fail("RETRY_POLICY_INVALID: initialBackoffMs must be >= 0 (was " + p.getInitialBackoffMs() + ")");
        }
        if (p.getBackoffFactor() <= 0.0) {
            fail("RETRY_POLICY_INVALID: backoffFactor must be > 0 (was " + p.getBackoffFactor() + ")");
        }
        if (p.getMode() == RetryMode.NEVER && p.getMaxAttempts() != 1) {
            fail("RETRY_POLICY_INVALID: RetryMode.NEVER requires maxAttempts == 1 (was " + p.getMaxAttempts() + ")");
        }
    }

    // ----- tiny template engine -----

    private static final class TemplatePlan {
        final List<TemplateToken> tokens = new ArrayList<>();
        final Set<String> requiredPathVars = new LinkedHashSet<>();
        final Set<String> optionalQueryVars = new LinkedHashSet<>();
        String splatVar;

        static TemplatePlan parse(String path) {
            TemplatePlan p = new TemplatePlan();
            for (int i = 0; i < path.length(); ) {
                char c = path.charAt(i);
                if (c == '{') {
                    int j = path.indexOf('}', i + 1);
                    if (j < 0) throw new IllegalStateException("Unclosed '{' in path: " + path);
                    String token = path.substring(i + 1, j);
                    if (token.startsWith("?")) {
                        String name = token.substring(1);
                        if (name.isBlank()) throw new IllegalStateException("Empty optional var in path: " + path);
                        p.optionalQueryVars.add(name);
                        p.tokens.add(new TemplateToken(TokenKind.OPT_QUERY, name));
                    } else if (token.startsWith("*")) {
                        String name = token.substring(1);
                        if (p.splatVar != null) throw new IllegalStateException("Multiple splats not allowed: " + path);
                        p.splatVar = name;
                        p.tokens.add(new TemplateToken(TokenKind.SPLAT, name));
                    } else {
                        if (token.isBlank()) throw new IllegalStateException("Empty required var in path: " + path);
                        p.requiredPathVars.add(token);
                        p.tokens.add(new TemplateToken(TokenKind.REQ_VAR, token));
                    }
                    i = j + 1;
                } else {
                    int j = path.indexOf('{', i);
                    if (j < 0) j = path.length();
                    p.tokens.add(new TemplateToken(TokenKind.LITERAL, path.substring(i, j)));
                    i = j;
                }
            }
            return p;
        }
    }

    private enum TokenKind { LITERAL, REQ_VAR, OPT_QUERY, SPLAT }
    private record TemplateToken(TokenKind kind, String value) {}

    /** Minimal draft passed to custom signer. */
    public record RequestDraft(Type type, String url, JSONObject jsonBody) { }
}
