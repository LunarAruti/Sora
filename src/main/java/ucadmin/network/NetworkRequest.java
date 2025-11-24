package ucadmin.network;

import org.json.JSONObject;

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
 * Defaults:
 *   - authStrategy = NONE
 *   - timeout      = 20 seconds
 *   - responseType = JSON_OBJECT
 *   - projectionPath = null (return entire JSON body)
 *   - returnAlias  = name
 *   - cachePath    = mem:/database/network/{service}/{returnAlias} (in-memory only)
 *
 * Validation enforced by seal():
 *   - type, requestUrl, path, responseType are required (responseType has a default)
 *   - path must start with '/'
 *   - all {var} present in vars; {*rest} non-empty if used
 *   - GET/DELETE must not have a body; POST/PUT/PATCH must have a JSON body
 *   - auth rules based on strategy (see setters)
 *   - timeout in [100ms, 120000ms]
 *   - projectionPath allowed only with JSON_* response types (null = full body)
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

    // ---------------- Identity ----------------

    /** Logical group (for logs/rate buckets), e.g., "roblox". */
    private final String service;

    /** Endpoint name (for logs/alias), e.g., "GetFriends". */
    private final String name;

    /** Trace correlation id (auto if null on seal). */
    private String traceId;

    // ---------------- Request shape ----------------

    /** Required; HTTP type. */
    private Type type;

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

    /** Rate limiter lane (group), e.g., "roblox.read" (optional; defaults to service). */
    private String rateBucket;

    /** Circuit breaker key (optional; defaults to host(requestUrl)). */
    private String circuitKey;

    /** Safe write retries when upstream supports idempotent POST/PUT/PATCH. */
    private String idempotencyKey;

    // ---------------- Response contract ----------------

    /** Expected body shape (default JSON_OBJECT). */
    private ResponseType responseType = ResponseType.JSON_OBJECT;

    /** Optional DBM-style JSON path projection; null = entire JSON. */
    private String projectionPath;

    /** Friendly label for logs/cache; defaults to name. */
    private String returnAlias;

    // ---------------- Caching hint (mem:/ mount only) ----------------

    /**
     * Default: mem:/database/network/{service}/{returnAlias}
     * This is an in-memory path (not persisted). Future options may snapshot to disk.
     */
    private String cachePath;

    /** Optional TTL (seconds) for the in-memory entry. */
    private Integer cacheTtlSeconds;

    // ---------------- Derived after seal() ----------------

    private boolean sealed;

    /** requestUrl + rendered path + encoded query. */
    private String finalUrl;

    /** Caller headers merged with Content-Type/Auth/Idempotency (ready-to-send). */
    private Map<String, String> renderedHeaders;

    /** Parsed template plan (tokens and var kinds). */
    private TemplatePlan plan;

    // ------------- ctor -------------

    public NetworkRequest(String service, String name) {
        this.service = nonEmpty(service, "service");
        this.name = nonEmpty(name, "name");
    }

    // ------------- fluent setters (all guard ensureNotSealed) -------------

    // identity
    public NetworkRequest setTraceId(String id) { ensureNotSealed(); this.traceId = id; return this; }

    // request shape
    public NetworkRequest setType(Type t) { ensureNotSealed(); this.type = Objects.requireNonNull(t); return this; }
    public NetworkRequest setRequestUrl(String url) { ensureNotSealed(); this.requestUrl = url; return this; }
    public NetworkRequest setPath(String p) { ensureNotSealed(); this.path = p; return this; }
    public NetworkRequest putVar(String key, Object val) { ensureNotSealed(); this.vars.put(key, val); return this; }
    public NetworkRequest putVars(Map<String, ?> m) { ensureNotSealed(); if (m != null) m.forEach(this::putVar); return this; }

    /** Sets a header; disallows transport-managed headers (Host, Content-Length, Accept-Encoding). */
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

    public NetworkRequest setJsonBody(JSONObject body) { ensureNotSealed(); this.jsonBody = body; return this; }

    // auth
    public NetworkRequest setAuthBearer(Supplier<String> tokenProvider) {
        ensureNotSealed();
        this.authStrategy = AuthStrategy.BEARER;
        this.authKeyName = "Authorization";
        this.authProvider = tokenProvider;
        return this;
    }
    public NetworkRequest setAuthApiKey(String headerName, Supplier<String> keyProvider) {
        ensureNotSealed();
        this.authStrategy = AuthStrategy.API_KEY_HEADER;
        this.authKeyName = nonEmpty(headerName, "authKeyName");
        this.authProvider = keyProvider;
        return this;
    }
    public NetworkRequest setAuthCustom(BiConsumer<RequestDraft, Map<String,String>> signer) {
        ensureNotSealed();
        this.authStrategy = AuthStrategy.CUSTOM_SIGNER;
        this.customSigner = signer;
        return this;
    }

    // resilience
    public NetworkRequest setTimeout(Duration d) { ensureNotSealed(); this.timeout = Objects.requireNonNull(d); return this; }
    public NetworkRequest setRateBucket(String b) { ensureNotSealed(); this.rateBucket = b; return this; }
    public NetworkRequest setCircuitKey(String k) { ensureNotSealed(); this.circuitKey = k; return this; }
    public NetworkRequest setIdempotencyKey(String k) { ensureNotSealed(); this.idempotencyKey = k; return this; }

    // response
    public NetworkRequest setResponseType(ResponseType rt) { ensureNotSealed(); this.responseType = Objects.requireNonNull(rt); return this; }
    public NetworkRequest setProjectionPath(String p) { ensureNotSealed(); this.projectionPath = p; return this; }
    public NetworkRequest setReturnAlias(String a) { ensureNotSealed(); this.returnAlias = a; return this; }

    // caching
    public NetworkRequest setCachePath(String path) { ensureNotSealed(); this.cachePath = path; return this; }
    public NetworkRequest setCacheTtlSeconds(Integer ttl) { ensureNotSealed(); this.cacheTtlSeconds = ttl; return this; }

    // ------------- sealing & validation -------------

    /** Validate all required fields, compute finalUrl/renderedHeaders/defaults. */
    public synchronized NetworkRequest seal() {
        if (sealed) return this;

        // identity defaults
        if (returnAlias == null || returnAlias.isBlank()) returnAlias = name;
        if (traceId == null || traceId.isBlank()) traceId = UUID.randomUUID().toString();

        // request shape
        if (type == null) fail("MISSING_TYPE: No request type set.");
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
            this.cachePath = "mem:/database/network/" + service + "/" + returnAlias;
        }

        sealed = true;
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
    public String getRateBucket() { return (rateBucket != null && !rateBucket.isBlank()) ? rateBucket : service; }
    public String getCircuitKey() { return (circuitKey != null && !circuitKey.isBlank()) ? circuitKey : URI.create(requestUrl).getHost(); }
    public String getCachePath() { return cachePath; }
    public Integer getCacheTtlSeconds() { return cacheTtlSeconds; }

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
    private static void fail(String msg) { throw new IllegalStateException(msg); }
    private void ensureSealed() { if (!sealed) fail("REQUEST_NOT_SEALED: Call seal() first."); }
    private void ensureNotSealed() {
        if (sealed) throw new IllegalStateException("REQUEST_IMMUTABLE: This NetworkRequest is sealed.");
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