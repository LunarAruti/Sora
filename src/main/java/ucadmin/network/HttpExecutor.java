package ucadmin.network;

import ucadmin.exceptions.NetworkException;
import ucadmin.exceptions.NetworkException.ErrorType;
import ucadmin.util.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Low-level HTTP execution utility.
 *
 * <p>This class is responsible for taking a sealed {@link NetworkRequest},
 * building a concrete {@link HttpRequest}, executing it using a shared
 * {@link HttpClient}, following redirects (when enabled in the NRO), and
 * returning a raw {@link HttpAttemptResult} containing status, headers, body,
 * and timing information.</p>
 *
 * <p>No retry policy, circuit breaker, rate limiting, or DBM integration
 * is handled here. Those behaviors live at a higher layer in the network
 * module (e.g., the worker/NetworkManager).</p>
 */
final class HttpExecutor {

    /**
     * Default connect timeout used when the NetworkRequest does not specify
     * a more granular connect timeout. Individual connect timeouts may
     * use separate HttpClient instances for better control.
     */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Shared HttpClient for the default connect timeout.
     * Redirects are disabled here; we manage them manually per request
     * using NetworkRequest.followRedirects and maxRedirects.
     */
    private static final HttpClient DEFAULT_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
            .build();

    /**
     * Pool of HttpClient instances keyed by connect timeout duration.
     * This allows per-request connect timeouts without rebuilding clients
     * for every call.
     */
    private static final ConcurrentHashMap<Duration, HttpClient> CLIENTS_BY_CONNECT_TIMEOUT =
            new ConcurrentHashMap<>();

    private HttpExecutor() {
        // utility class; no instances
    }

    /**
     * Executes a single on-the-wire HTTP attempt based on the provided {@link NetworkRequest}.
     *
     * <p>This method:
     * <ul>
     *     <li>Ensures the request is sealed (and thus has finalUrl + headers ready)</li>
     *     <li>Builds a {@link HttpRequest} with method, headers, timeout, and JSON body</li>
     *     <li>Performs manual redirect handling according to {@code followRedirects} and {@code maxRedirects}</li>
     *     <li>Returns a {@link HttpAttemptResult} describing the final response for this attempt</li>
     *     <li>Throws {@link NetworkException} for timeouts, I/O failures, cancellation, or host policy violations</li>
     * </ul>
     *
     * <p>This corresponds to a single attempt in the presence of higher-level retries.
     * If redirects are followed, multiple underlying HTTP requests may be issued, but
     * they are considered part of the same logical attempt.</p>
     *
     * @param request sealed {@link NetworkRequest} describing what to send
     * @return a raw {@link HttpAttemptResult} capturing status, headers, body, and timing
     * @throws NetworkException if the attempt could not be completed at the transport level
     */
    static HttpAttemptResult executeSingleAttempt(NetworkRequest request) throws NetworkException {
        if (request == null) {
            Logger.log(Logger.TAG.ERROR, "[05001] HttpExecutor: null request");
            throw new NetworkException(
                    ErrorType.INVALID_REQUEST,
                    "NetworkRequest is null"
            );
        }

        request.seal();
        final String service = request.getService();
        final String endpoint = request.getName();
        final String traceId = request.getTraceId();

        Logger.log(Logger.TAG.REQUEST,
                "HttpExecutor: BEGIN attempt service=" + service +
                        " endpoint=" + endpoint + " traceId=" + traceId +
                        " url=" + request.getFinalUrl());

        // Starting URL for this attempt (includes path + query).
        String currentUrl = request.getFinalUrl();

        // Redirect budget derived from the NRO.
        int redirectsRemaining = request.isFollowRedirects() ? request.getMaxRedirects() : 0;
        int redirectCount = 0;

        // Monotonic start time (for latency of this attempt).
        long startedNanos = System.nanoTime();

        try {
            while (true) {

                // --- Host policy enforcement via NetworkConfig ---
                try {
                    URI uri = URI.create(currentUrl);
                    String host = uri.getHost();
                    if (!NetworkConfig.isHostAllowed(host)) {
                        Logger.log(Logger.TAG.ERROR, "[05002] HttpExecutor: POLICY BLOCKED host=" + host + " traceId=" + traceId);
                        throw new NetworkException(
                                ErrorType.POLICY_VIOLATION,
                                "Host not allowed by NetworkConfig: " + host,
                                null,
                                service,
                                endpoint,
                                traceId,
                                currentUrl,
                                null
                        );
                    }
                } catch (IllegalArgumentException ex) {
                    Logger.log(Logger.TAG.ERROR, "[05003] HttpExecutor: INVALID URL " + currentUrl + " traceId=" + traceId);
                    // Malformed URL → treat as invalid request.
                    throw new NetworkException(
                            ErrorType.INVALID_REQUEST,
                            "Invalid URL in NetworkRequest: " + currentUrl,
                            ex,
                            service,
                            endpoint,
                            traceId,
                            currentUrl,
                            null
                    );
                }

                HttpClient client = selectClient(request.getConnectTimeout());
                Logger.log(Logger.TAG.DEBUG,
                        "HttpExecutor: selected HttpClient timeout=" +
                                (request.getConnectTimeout() == null ? "default" : request.getConnectTimeout()) +
                                " traceId=" + traceId);

                HttpRequest httpRequest = buildHttpRequest(request, currentUrl);
                Logger.log(Logger.TAG.DEBUG,
                        "HttpExecutor: built request method=" + request.getType() +
                                " url=" + currentUrl + " traceId=" + traceId);

                HttpResponse<String> response;
                try {
                    response = client.send(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                    );
                } catch (HttpTimeoutException e) {
                    Logger.log(Logger.TAG.ERROR, "[05004] HttpExecutor: TIMEOUT url=" + currentUrl + " traceId=" + traceId);
                    throw new NetworkException(
                            ErrorType.TIMEOUT,
                            "Network request timed out.",
                            e,
                            service,
                            endpoint,
                            traceId,
                            currentUrl,
                            null
                    );
                } catch (IOException e) {
                    Logger.log(Logger.TAG.ERROR, "[05005] HttpExecutor: NETWORK_IO url=" + currentUrl + " traceId=" + traceId);
                    throw new NetworkException(
                            ErrorType.NETWORK_IO,
                            "I/O error while executing network request.",
                            e,
                            service,
                            endpoint,
                            traceId,
                            currentUrl,
                            null
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Logger.log(Logger.TAG.ERROR, "[05006] HttpExecutor: CANCELLED url=" + currentUrl + " traceId=" + traceId);
                    throw new NetworkException(
                            ErrorType.CANCELLED,
                            "Network request was interrupted or cancelled.",
                            e,
                            service,
                            endpoint,
                            traceId,
                            currentUrl,
                            null
                    );
                }

                int status = response.statusCode();
                Map<String, List<String>> headers = response.headers().map();
                String body = response.body();

                // Manual redirect handling if allowed and we got a redirect status.
                if (isRedirectStatus(status) && redirectsRemaining > 0) {
                    Logger.log(Logger.TAG.DEBUG,
                            "HttpExecutor: redirect status=" + status +
                                    " location=" + firstHeaderIgnoreCase(headers, "Location") +
                                    " traceId=" + traceId);
                    String location = firstHeaderIgnoreCase(headers, "Location");
                    if (location == null || location.isBlank()) {
                        // Redirect status without a valid Location header → treat as final.
                        Logger.log(Logger.TAG.WARN, "[05007] HttpExecutor: redirect missing Location status=" + status +
                                        " url=" + currentUrl + " traceId=" + traceId);
                        long completedNanos = System.nanoTime();
                        return new HttpAttemptResult(
                                currentUrl,
                                status,
                                headers,
                                body,
                                startedNanos,
                                completedNanos,
                                redirectCount
                        );
                    }

                    String nextUrl = resolveRedirect(currentUrl, location);
                    if (nextUrl == null || nextUrl.isBlank()) {
                        // Invalid redirect target → treat the current response as final.
                        Logger.log(Logger.TAG.WARN, "[05008] HttpExecutor: redirect invalid Location status=" + status +
                                        " url=" + currentUrl + " location=" + location +
                                        " traceId=" + traceId);
                        long completedNanos = System.nanoTime();
                        return new HttpAttemptResult(
                                currentUrl,
                                status,
                                headers,
                                body,
                                startedNanos,
                                completedNanos,
                                redirectCount
                        );
                    }

                    currentUrl = nextUrl;
                    redirectsRemaining--;
                    redirectCount++;
                    Logger.log(Logger.TAG.REQUEST,
                            "HttpExecutor: FOLLOW_REDIRECT → " + nextUrl +
                                    " (remaining=" + redirectsRemaining + ") traceId=" + traceId);
                    continue;
                }

                // Either not a redirect status, or redirects are disabled/exhausted.
                long completedNanos = System.nanoTime();
                Logger.log(Logger.TAG.REQUEST,
                        "HttpExecutor: END attempt status=" + status +
                                " latencyMs=" + ((completedNanos - startedNanos) / 1_000_000L) +
                                " traceId=" + traceId);
                return new HttpAttemptResult(
                        currentUrl,
                        status,
                        headers,
                        body,
                        startedNanos,
                        completedNanos,
                        redirectCount
                );
            }
        } finally {
            // HttpClient manages its own resources; nothing explicit to close here.
        }
    }

    /**
     * Selects an HttpClient instance appropriate for the requested connect timeout.
     * If no specific connectTimeout is provided, a shared default client is used.
     */
    private static HttpClient selectClient(Duration connectTimeout) {
        if (connectTimeout == null) {
            Logger.log(Logger.TAG.DEBUG, "HttpExecutor: using DEFAULT_CLIENT");
            return DEFAULT_CLIENT;
        }

        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            Logger.log(Logger.TAG.ERROR, "[05009] HttpExecutor: invalid connectTimeout=" + connectTimeout);
            throw new NetworkException(
                    ErrorType.INVALID_REQUEST,
                    "connectTimeout must be > 0 (was " + connectTimeout + ")"
            );
        }

        Logger.log(Logger.TAG.DEBUG,
                "HttpExecutor: using client for timeout=" + connectTimeout);

        // Reuse or lazily create a client for this specific connect timeout.
        return CLIENTS_BY_CONNECT_TIMEOUT.computeIfAbsent(
                connectTimeout,
                d -> HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(d)
                        .build()
        );
    }

    /**
     * Builds a concrete {@link HttpRequest} for the given URL, using the method,
     * headers, body, and timeout from the {@link NetworkRequest}.
     */
    private static HttpRequest buildHttpRequest(NetworkRequest request, String url) {
        Logger.log(Logger.TAG.DEBUG,
                "HttpExecutor: building HttpRequest for url=" + url);

        Objects.requireNonNull(url, "url must not be null");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        // Effective per-request timeout: prefer readTimeout if present, otherwise use wall timeout.
        Duration effectiveTimeout = (request.getReadTimeout() != null)
                ? request.getReadTimeout()
                : request.getTimeout();

        if (effectiveTimeout != null) {
            builder = builder.timeout(effectiveTimeout);
        }

        // HTTP method + JSON body for *_JSON types.
        NetworkRequest.Type type = request.getType();
        switch (type) {
            case GET -> builder = builder.GET();
            case DELETE -> builder = builder.DELETE();
            case POST_JSON, PUT_JSON, PATCH_JSON -> {
                String json = (request.getJsonBody() != null)
                        ? request.getJsonBody().toString()
                        : "";
                HttpRequest.BodyPublisher pub =
                        HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
                String method = mapTypeToHttpMethod(type);
                builder = builder.method(method, pub);
            }
            default -> throw new NetworkException(
                    ErrorType.INVALID_REQUEST,
                    "Unsupported HTTP type: " + type
            );
        }

        // Headers (already merged/auth'd by NetworkRequest.renderHeaders()).
        for (Map.Entry<String, String> e : request.getRenderedHeaders().entrySet()) {
            builder = builder.header(e.getKey(), e.getValue());
        }

        return builder.build();
    }

    private static String mapTypeToHttpMethod(NetworkRequest.Type type) {
        return switch (type) {
            case GET -> "GET";
            case DELETE -> "DELETE";
            case POST_JSON -> "POST";
            case PUT_JSON -> "PUT";
            case PATCH_JSON -> "PATCH";
        };
    }

    private static boolean isRedirectStatus(int status) {
        // Common redirect codes: 301, 302, 303, 307, 308
        return status == 301 || status == 302 || status == 303
                || status == 307 || status == 308;
    }

    private static String firstHeaderIgnoreCase(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty() || name == null) return null;
        String target = name.toLowerCase();
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            String key = e.getKey();
            if (key != null && key.toLowerCase().equals(target)) {
                List<String> vals = e.getValue();
                if (vals != null && !vals.isEmpty()) {
                    return vals.get(0);
                }
            }
        }
        return null;
    }

    /**
     * Resolves a redirect Location header relative to the current URL.
     * Handles both absolute and relative redirect targets.
     */
    private static String resolveRedirect(String currentUrl, String location) {
        try {
            URI base = URI.create(currentUrl);
            URI loc = URI.create(location);
            if (!loc.isAbsolute()) {
                loc = base.resolve(loc);
            }
            return loc.toString();
        } catch (Exception e) {
            // Invalid redirect target; let the caller treat this as a final response.
            return null;
        }
    }

    /**
     * Raw result of a single HTTP attempt.
     *
     * <p>This is intentionally minimal: it represents a single "fire and receive"
     * execution, including manual redirects performed as part of that attempt.
     * Higher layers (retry logic, NetworkResult, DBM) build on top of this.</p>
     */
    static final class HttpAttemptResult {

        /** Final URL for this attempt after any redirects. */
        private final String finalUrl;

        /** HTTP status code returned by the remote. */
        private final int statusCode;

        /** Response headers. */
        private final Map<String, List<String>> headers;

        /** Raw response body as a string. */
        private final String body;

        /** Monotonic start time in nanoseconds. */
        private final long startedAtNanos;

        /** Monotonic completion time in nanoseconds. */
        private final long completedAtNanos;

        /** Number of redirects followed in this attempt. */
        private final int redirectCount;

        HttpAttemptResult(
                String finalUrl,
                int statusCode,
                Map<String, List<String>> headers,
                String body,
                long startedAtNanos,
                long completedAtNanos,
                int redirectCount
        ) {
            this.finalUrl = finalUrl;
            this.statusCode = statusCode;
            // defensive copy so nobody can mutate headers after construction
            this.headers = (headers == null)
                    ? java.util.Collections.emptyMap()
                    : deepUnmodifiable(headers);
            this.body = body;
            this.startedAtNanos = startedAtNanos;
            this.completedAtNanos = completedAtNanos;
            this.redirectCount = redirectCount;
        }

        private static Map<String, List<String>> deepUnmodifiable(Map<String, List<String>> src) {
            java.util.Map<String, java.util.List<String>> copy = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, java.util.List<String>> e : src.entrySet()) {
                java.util.List<String> list = e.getValue();
                if (list == null) {
                    copy.put(e.getKey(), java.util.Collections.emptyList());
                } else {
                    copy.put(e.getKey(),
                            java.util.Collections.unmodifiableList(java.util.List.copyOf(list)));
                }
            }
            return java.util.Collections.unmodifiableMap(copy);
        }

        public String getFinalUrl() { return finalUrl; }
        public int getStatusCode() { return statusCode; }
        public Map<String, List<String>> getHeaders() { return headers; }
        public String getBody() { return body; }
        public long getStartedAtNanos() { return startedAtNanos; }
        public long getCompletedAtNanos() { return completedAtNanos; }
        public int getRedirectCount() { return redirectCount; }

        /**
         * Returns the latency for this attempt in milliseconds.
         */
        public long getLatencyMillis() {
            return (completedAtNanos - startedAtNanos) / 1_000_000L;
        }
    }
}

