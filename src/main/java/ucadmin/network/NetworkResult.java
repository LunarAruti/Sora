package ucadmin.network;

import org.json.JSONArray;
import org.json.JSONObject;
import ucadmin.util.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable representation of the final outcome of a network request execution.
 *
 * <p>This model is intended to capture everything the rest of the system needs
 * to know about a completed request, including:</p>
 * <ul>
 *     <li>HTTP status code and response headers</li>
 *     <li>Raw response body as a string</li>
 *     <li>Parsed JSON body (object or array) when applicable</li>
 *     <li>Timing metrics (start/end timestamps, latency)</li>
 *     <li>Logical metadata (service, endpoint, traceId, URL, attempts, redirects)</li>
 *     <li>High-level success flag, as determined by the network logic</li>
 * </ul>
 *
 * <p>This object is suitable for direct serialization into a DBM temp path
 * (for example under a "body" and "network_meta" structure), or for further
 * transformation upstream.</p>
 */
public final class NetworkResult {

    // ------------ logical metadata ------------

    /** Logical service/group name (e.g., "roblox"). May be null. */
    private final String service;

    /** Logical endpoint name within the service (e.g., "GetFriends"). May be null. */
    private final String endpoint;

    /** Trace/correlation id tying this result back to the originating request. May be null. */
    private final String traceId;

    /** Final URL after all redirects have been followed, never null in a successful execution. */
    private final String finalUrl;

    /** Number of redirects followed during this execution (0 if none). */
    private final int redirectCount;

    /** Total number of attempts (initial + retries) that were made for this logical request. */
    private final int attempts;

    // ------------ HTTP layer ------------

    /** HTTP status code returned by the remote endpoint. */
    private final int statusCode;

    /**
     * Response headers as returned by the HTTP client.
     * Keys are stored exactly as received; header names are case-insensitive at the protocol level.
     */
    private final Map<String, List<String>> headers;

    /**
     * Raw response body as a string.
     * For JSON APIs this is typically UTF-8 encoded JSON text.
     */
    private final String body;

    /**
     * Whether this result has been classified as "successful" by the network layer
     * (e.g., acceptable status code, valid JSON, no policy violations).
     * This is a high-level classification, not necessarily the same as a 2xx status.
     */
    private final boolean success;

    // ------------ JSON decoding ------------

    /**
     * ResponseType originally requested on the NetworkRequest (JSON_OBJECT or JSON_ARRAY),
     * or null if not applicable or unknown.
     */
    private final NetworkRequest.ResponseType responseType;

    /**
     * Parsed JSON body as an object, when {@link #responseType} is JSON_OBJECT.
     * Null if not applicable or parsing failed.
     */
    private final JSONObject jsonObject;

    /**
     * Parsed JSON body as an array, when {@link #responseType} is JSON_ARRAY.
     * Null if not applicable or parsing failed.
     */
    private final JSONArray jsonArray;

    /**
     * Indicates whether JSON parsing was attempted and succeeded according to the
     * requested responseType. This is independent of {@link #success}.
     */
    private final boolean jsonDecoded;

    // ------------ timing metrics ------------

    /**
     * Epoch millisecond timestamp when the first attempt for this logical request began.
     * (System.currentTimeMillis at start of attempt 1.)
     */
    private final long startedAtMillis;

    /**
     * Epoch millisecond timestamp when the final attempt completed (success or failure).
     */
    private final long completedAtMillis;

    /**
     * Total approximate latency in milliseconds between {@link #startedAtMillis} and
     * {@link #completedAtMillis}, including any retries and waits.
     */
    private final long latencyMillis;

    // ------------ construction ------------

    /**
     * Constructs a fully-populated network result.
     *
     * @param service           logical service/group name (may be null)
     * @param endpoint          logical endpoint name within the service (may be null)
     * @param traceId           correlation id tying this result to a NetworkRequest (may be null)
     * @param finalUrl          final URL after redirects (must not be null)
     * @param redirectCount     number of redirects followed (0 if none)
     * @param attempts          total number of attempts (>= 1)
     * @param statusCode        HTTP status code returned by the endpoint
     * @param headers           response headers (will be defensively copied; may be null)
     * @param body              raw response body as a string (may be null)
     * @param success           high-level success classification decided by the network logic
     * @param responseType      expected JSON response type (may be null if not JSON-focused)
     * @param jsonObject        parsed JSON object when responseType = JSON_OBJECT (may be null)
     * @param jsonArray         parsed JSON array when responseType = JSON_ARRAY (may be null)
     * @param jsonDecoded       true if JSON parsing was attempted and matched responseType
     * @param startedAtMillis   epoch millis when the first attempt started
     * @param completedAtMillis epoch millis when the final attempt completed
     * @param latencyMillis     total latency in millis between start and completion
     */
    public NetworkResult(
            String service,
            String endpoint,
            String traceId,
            String finalUrl,
            int redirectCount,
            int attempts,
            int statusCode,
            Map<String, List<String>> headers,
            String body,
            boolean success,
            NetworkRequest.ResponseType responseType,
            JSONObject jsonObject,
            JSONArray jsonArray,
            boolean jsonDecoded,
            long startedAtMillis,
            long completedAtMillis,
            long latencyMillis
    ) {
        if (finalUrl == null) {
            throw new IllegalArgumentException("finalUrl must not be null");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be >= 1 (was " + attempts + ")");
        }

        this.service = service;
        this.endpoint = endpoint;
        this.traceId = traceId;
        this.finalUrl = finalUrl;
        this.redirectCount = Math.max(0, redirectCount);
        this.attempts = attempts;
        this.statusCode = statusCode;
        this.headers = (headers == null)
                ? Collections.emptyMap()
                : deepUnmodifiable(headers);
        this.body = body;
        this.success = success;
        this.responseType = responseType;
        this.jsonObject = jsonObject;
        this.jsonArray = jsonArray;
        this.jsonDecoded = jsonDecoded;
        this.startedAtMillis = startedAtMillis;
        this.completedAtMillis = completedAtMillis;
        this.latencyMillis = latencyMillis;

        Logger.log(Logger.TAG.DEBUG,
                "NetworkResult: created service=" + service +
                        " endpoint=" + endpoint +
                        " status=" + statusCode +
                        " success=" + success +
                        " attempts=" + attempts +
                        " latency=" + latencyMillis + "ms");
    }

    private static Map<String, List<String>> deepUnmodifiable(Map<String, List<String>> src) {
        Map<String, List<String>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            List<String> list = e.getValue();
            if (list == null) {
                copy.put(e.getKey(), Collections.emptyList());
            } else {
                copy.put(e.getKey(), Collections.unmodifiableList(List.copyOf(list)));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    // ------------ getters ------------

    public String getService() { return service; }

    public String getEndpoint() { return endpoint; }

    public String getTraceId() { return traceId; }

    public String getFinalUrl() { return finalUrl; }

    public int getRedirectCount() { return redirectCount; }

    public int getAttempts() { return attempts; }

    public int getStatusCode() { return statusCode; }

    public Map<String, List<String>> getHeaders() { return headers; }

    public String getBody() { return body; }

    public boolean isSuccess() { return success; }

    public NetworkRequest.ResponseType getResponseType() { return responseType; }

    public JSONObject getJsonObject() { return jsonObject; }

    public JSONArray getJsonArray() { return jsonArray; }

    public boolean isJsonDecoded() { return jsonDecoded; }

    public long getStartedAtMillis() { return startedAtMillis; }

    public long getCompletedAtMillis() { return completedAtMillis; }

    public long getLatencyMillis() { return latencyMillis; }

    // ------------ convenience helpers ------------

    /**
     * Returns a simple immutable map summarizing the "network_meta" part that
     * is typically useful to serialize alongside the body for diagnostics.
     *
     * <p>Example shape:</p>
     * <pre>
     * {
     *   "service": "roblox",
     *   "endpoint": "GetFriends",
     *   "trace_id": "...",
     *   "final_url": "...",
     *   "status_code": 200,
     *   "attempts": 1,
     *   "redirects": 0,
     *   "started_at_ms": 1762227000000,
     *   "completed_at_ms": 1762227000384,
     *   "latency_ms": 384,
     *   "success": true
     * }
     * </pre>
     */
    public Map<String, Object> toMetaMap() {
        Logger.log(Logger.TAG.DEBUG,
                "NetworkResult.toMetaMap: service=" + service +
                        " endpoint=" + endpoint +
                        " status=" + statusCode +
                        " attempts=" + attempts);

        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("service", service);
        meta.put("endpoint", endpoint);
        meta.put("trace_id", traceId);
        meta.put("final_url", finalUrl);
        meta.put("status_code", statusCode);
        meta.put("attempts", attempts);
        meta.put("redirects", redirectCount);
        meta.put("started_at_ms", startedAtMillis);
        meta.put("completed_at_ms", completedAtMillis);
        meta.put("latency_ms", latencyMillis);
        meta.put("success", success);
        return Collections.unmodifiableMap(meta);
    }

    /**
     * Builds the JSON value that should be written into DBM for this result.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>If {@code collectMetrics == false}:
     *         <ul>
     *             <li>If a JSON object was decoded, returns that {@link JSONObject}.</li>
     *             <li>Else if a JSON array was decoded, returns that {@link JSONArray}.</li>
     *             <li>Else if a raw body is present, returns {@code {"raw_body": "..."} }.</li>
     *             <li>Else returns an empty {@link JSONObject}.</li>
     *         </ul>
     *     </li>
     *     <li>If {@code collectMetrics == true}:
     *         <ul>
     *             <li>Returns a {@link JSONObject} with:
     *                 <ul>
     *                     <li>{@code "body"} = decoded JSON object/array, if available;</li>
     *                     <li>or {@code "raw_body"} if only raw text is available;</li>
     *                     <li>{@code "network_meta"} = a {@link JSONObject} built from {@link #toMetaMap()}.</li>
     *                 </ul>
     *             </li>
     *         </ul>
     *     </li>
     * </ul>
     *
     * <p>The returned value is suitable to pass directly as the {@code value}
     * parameter to {@code DatabaseManager.writeJSONPathTemp(cachePath, null, value, true)}.</p>
     *
     * @param collectMetrics whether to include the {@code network_meta} envelope
     * @return JSONObject or JSONArray representing the DBM root value
     */
    public Object toDbmValue(boolean collectMetrics) {
        Logger.log(Logger.TAG.DEBUG,
                "NetworkResult.toDbmValue: collectMetrics=" + collectMetrics +
                        " service=" + service +
                        " endpoint=" + endpoint);

        if (!collectMetrics) {
            if (jsonObject != null) {
                Logger.log(Logger.TAG.DEBUG,
                        "NetworkResult.toDbmValue: returning bare JSON_OBJECT");
                return jsonObject;
            }
            if (jsonArray != null) {
                Logger.log(Logger.TAG.DEBUG,
                        "NetworkResult.toDbmValue: returning bare JSON_ARRAY");
                return jsonArray;
            }
            if (body != null) {
                Logger.log(Logger.TAG.DEBUG,
                        "NetworkResult.toDbmValue: returning raw_body wrapper");
                JSONObject wrapper = new JSONObject();
                wrapper.put("raw_body", body);
                return wrapper;
            }
            Logger.log(Logger.TAG.DEBUG,
                    "NetworkResult.toDbmValue: returning empty JSON");
            return new JSONObject();
        }

        // Envelope mode
        Logger.log(Logger.TAG.DEBUG,
                "NetworkResult.toDbmValue: building envelope with meta");

        JSONObject out = new JSONObject();

        if (jsonObject != null) {
            out.put("body", jsonObject);
        } else if (jsonArray != null) {
            out.put("body", jsonArray);
        } else if (body != null) {
            out.put("raw_body", body);
        }

        JSONObject metaJson = new JSONObject(toMetaMap());
        out.put("network_meta", metaJson);

        return out;
    }
}