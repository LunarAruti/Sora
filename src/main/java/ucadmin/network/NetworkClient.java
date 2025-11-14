package ucadmin.network;

import org.json.JSONArray;
import org.json.JSONObject;
import ucadmin.exceptions.NetworkException;
import static ucadmin.exceptions.NetworkException.*;
import ucadmin.util.Logger;
import ucadmin.util.Logger.TAG;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Internal execution engine for UC Admin Network requests.
 */
public final class NetworkClient {

    private static final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private NetworkClient() {}

    public static boolean execute(NetworkRequest req) throws NetworkException {
        RetryPolicy retry = RetryPolicy.of(req);
        RateLimiter limiter = RateLimiter.forBucket(req.getRateBucket());
        CircuitBreaker breaker = CircuitBreaker.forKey(req.getCircuitKey());

        // Rate limit pre-check
        try {
            limiter.acquire();
        } catch (Exception e) {
            throw new RateLimitException(
                    "LOCAL_RATE_LIMIT: bucket depleted",
                    req.getService(), req.getName(), req.getTraceId(),
                    req.getFinalUrl(), req.getType().name(), e);
        }

        // Circuit breaker check
        if (breaker.isOpen()) {
            throw new CircuitOpenException(
                    "CIRCUIT_OPEN: breaker blocking requests",
                    req.getService(), req.getName(), req.getTraceId(),
                    req.getFinalUrl(), req.getType().name(), null);
        }

        int attempt = 0;
        while (true) {
            attempt++;
            long start = System.nanoTime();
            try {
                boolean stored = performHttp(req, start);
                breaker.onSuccess();
                return stored;
            } catch (NetworkRateLimitedException | NetworkUpstreamException | NetworkTimeoutException e) {
                breaker.onFailure();
                if (!retry.shouldRetry(e, attempt))
                    throw new RetryExhaustedException(
                            "RETRIES_EXHAUSTED: " + e.getMessage(),
                            req.getService(), req.getName(), req.getTraceId(),
                            req.getFinalUrl(), req.getType().name(), e);
                retry.backoff(attempt);
            } catch (NetworkException e) {
                breaker.onFailure();
                throw e;
            } catch (Exception e) {
                breaker.onFailure();
                throw new NetworkConnectionException(
                        "NETWORK_FAILURE: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                        req.getService(), req.getName(), req.getTraceId(),
                        req.getFinalUrl(), req.getType().name(), e);
            }
        }
    }

    private static boolean performHttp(NetworkRequest req, long start) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(req.getFinalUrl()))
                .timeout(req.getTimeout());

        switch (req.getType()) {
            case GET -> builder.GET();
            case DELETE -> builder.DELETE();
            case POST_JSON, PUT_JSON, PATCH_JSON -> {
                String body = req.getJsonBody().toString();
                builder.method(req.getType().name().replace('_', '-'),
                        HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
        }

        for (Map.Entry<String, String> e : req.getRenderedHeaders().entrySet()) {
            builder.header(e.getKey(), e.getValue());
        }

        Logger.log(TAG.REQUEST, "→ " + req.getService() + ":" + req.getName() +
                " " + req.getType() + " " + URI.create(req.getFinalUrl()).getPath() +
                " trace=" + req.getTraceId());

        HttpResponse<byte[]> resp;
        try {
            resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException e) {
            throw new NetworkTimeoutException(
                    "TIMEOUT after " + req.getTimeout().toMillis() + "ms",
                    req.getService(), req.getName(), req.getTraceId(),
                    req.getFinalUrl(), req.getType().name(), e);
        }

        int code = resp.statusCode();
        long ms = (System.nanoTime() - start) / 1_000_000L;
        int bytes = (resp.body() == null ? 0 : resp.body().length);

        Logger.log(TAG.REQUEST, "← " + req.getService() + ":" + req.getName() +
                " code=" + code + " ms=" + ms + " bytes=" + bytes +
                " trace=" + req.getTraceId());

        if (code < 200 || code >= 300) {
            String preview = NetUtils.preview(resp.body());
            Long retryAfter = NetUtils.parseRetryAfter(resp);
            throw NetUtils.httpToException(code, preview, retryAfter, req, null);
        }

        String text = new String(resp.body(), StandardCharsets.UTF_8);
        Object parsed;
        try {
            switch (req.getResponseType()) {
                case JSON_OBJECT -> {
                    JSONObject obj = new JSONObject(text);
                    parsed = (req.getProjectionPath() == null || req.getProjectionPath().isBlank())
                            ? obj
                            : DbmPath.project(obj, req.getProjectionPath());
                }
                case JSON_ARRAY -> {
                    JSONArray arr = new JSONArray(text);
                    parsed = (req.getProjectionPath() == null || req.getProjectionPath().isBlank())
                            ? arr
                            : DbmPath.project(arr, req.getProjectionPath());
                }
                default -> throw new NetworkDecodeException(
                        "UNSUPPORTED_RESPONSE_TYPE: " + req.getResponseType(),
                        req.getService(), req.getName(), req.getTraceId(),
                        req.getFinalUrl(), req.getType().name(), null);
            }
        } catch (org.json.JSONException je) {
            throw new NetworkDecodeException(
                    "JSON_DECODE_FAILED: " + je.getMessage(),
                    req.getService(), req.getName(), req.getTraceId(),
                    req.getFinalUrl(), req.getType().name(), je);
        }

        try {
            boolean stored = MemoryCacheManager.put(req.getCachePath(), parsed);
            Logger.log(TAG.REQUEST, "✔ stored " + req.getCachePath());
            return stored;
        } catch (Exception e) {
            throw new CacheWriteException(
                    "CACHE_WRITE_FAILED: " + e.getMessage(),
                    req.getService(), req.getName(), req.getTraceId(),
                    req.getFinalUrl(), req.getType().name(), e);
        }
    }
}
