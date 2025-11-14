package ucadmin.exceptions;

import java.util.Objects;

/**
 * Unified exception hierarchy for UC Admin Network module.
 *
 * All network-related failures funnel through these classes.
 * Each carries full request context and retry hints for engine policy.
 *
 * Retryable = true → client MAY retry according to RetryPolicy.
 */
public class NetworkException extends Exception {
    private static final long serialVersionUID = 1L;

    // ---------- Context ----------
    public final String service;     // e.g. "roblox"
    public final String name;        // e.g. "GetFriends"
    public final String traceId;     // correlation id
    public final String url;         // rendered final URL
    public final String requestType; // GET / POST_JSON / ...
    public final boolean retryable;  // retry hint

    // ---------- Base constructor ----------
    public NetworkException(
            String message,
            String service,
            String name,
            String traceId,
            String url,
            String requestType,
            boolean retryable,
            Throwable cause
    ) {
        super(Objects.requireNonNullElse(message, "Network failure"), cause);
        this.service = service;
        this.name = name;
        this.traceId = traceId;
        this.url = url;
        this.requestType = requestType;
        this.retryable = retryable;
    }

    /** Compact, structured breadcrumb for logs. */
    public String getContext() {
        return "svc=" + safe(service) +
                " op=" + safe(name) +
                " type=" + safe(requestType) +
                " trace=" + safe(traceId) +
                " url=" + safe(url);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + getMessage() + ") [" + getContext() + "]";
    }

    private static String safe(String s) { return s == null ? "<null>" : s; }

    // ========================================================================
    // =============== Derived Exception Classes =============================
    // ========================================================================

    // ---------------------- HTTP LAYER -------------------------------------

    /** Generic HTTP 4xx/5xx exception. */
    public static class NetworkHttpException extends NetworkException {
        private static final long serialVersionUID = 1L;

        public final int status;
        public final String bodyPreview;
        public final Long retryAfterMillis;

        public NetworkHttpException(
                String message,
                int status,
                String bodyPreview,
                Long retryAfterMillis,
                String service,
                String name,
                String traceId,
                String url,
                String requestType,
                boolean retryable,
                Throwable cause
        ) {
            super(message, service, name, traceId, url, requestType, retryable, cause);
            this.status = status;
            this.bodyPreview = bodyPreview;
            this.retryAfterMillis = retryAfterMillis;
        }

        @Override
        public String toString() {
            return super.toString() + " status=" + status +
                    (retryAfterMillis != null ? " retryAfterMs=" + retryAfterMillis : "") +
                    (bodyPreview != null && !bodyPreview.isEmpty()
                            ? " bodyPreview=" + summarize(bodyPreview)
                            : "");
        }

        private static String summarize(String s) {
            return (s.length() > 256) ? s.substring(0, 256) + " …" : s;
        }
    }

    /** 401 / 403 authentication failure. */
    public static final class NetworkAuthException extends NetworkHttpException {
        private static final long serialVersionUID = 1L;
        public NetworkAuthException(int status, String bodyPreview, String service, String name,
                                    String trace, String url, String type, Throwable cause) {
            super("AUTH_FAILED (" + status + ")", status, bodyPreview, null,
                    service, name, trace, url, type, false, cause);
        }
    }

    /** 404 missing resource. */
    public static final class NetworkNotFoundException extends NetworkHttpException {
        private static final long serialVersionUID = 1L;
        public NetworkNotFoundException(String bodyPreview, String service, String name,
                                        String trace, String url, String type, Throwable cause) {
            super("NOT_FOUND (404)", 404, bodyPreview, null,
                    service, name, trace, url, type, false, cause);
        }
    }

    /** 429 rate limited by server. */
    public static final class NetworkRateLimitedException extends NetworkHttpException {
        private static final long serialVersionUID = 1L;
        public NetworkRateLimitedException(String bodyPreview, Long retryAfterMs,
                                           String service, String name, String trace, String url, String type, Throwable cause) {
            super("RATE_LIMITED (429)", 429, bodyPreview, retryAfterMs,
                    service, name, trace, url, type, true, cause);
        }
    }

    /** 5xx server-side failure. */
    public static final class NetworkUpstreamException extends NetworkHttpException {
        private static final long serialVersionUID = 1L;
        public NetworkUpstreamException(int status, String bodyPreview,
                                        String service, String name, String trace, String url, String type, Throwable cause) {
            super("UPSTREAM_ERROR (" + status + ")", status, bodyPreview, null,
                    service, name, trace, url, type, true, cause);
        }
    }

    /** 400 invalid request body or parameters. */
    public static final class NetworkBadRequestException extends NetworkHttpException {
        private static final long serialVersionUID = 1L;
        public NetworkBadRequestException(String bodyPreview, String service, String name,
                                          String trace, String url, String type, Throwable cause) {
            super("BAD_REQUEST (400)", 400, bodyPreview, null,
                    service, name, trace, url, type, false, cause);
        }
    }

    /** 502/503/504 gateway or proxy failure. */
    public static final class NetworkGatewayException extends NetworkHttpException {
        private static final long serialVersionUID = 1L;
        public NetworkGatewayException(int status, String bodyPreview, String service,
                                       String name, String trace, String url, String type, Throwable cause) {
            super("GATEWAY_ERROR (" + status + ")", status, bodyPreview, null,
                    service, name, trace, url, type, true, cause);
        }
    }

    /** 409 conflict or version mismatch. */
    public static final class NetworkConflictException extends NetworkHttpException {
        private static final long serialVersionUID = 1L;
        public NetworkConflictException(String bodyPreview, String service, String name,
                                        String trace, String url, String type, Throwable cause) {
            super("CONFLICT (409)", 409, bodyPreview, null,
                    service, name, trace, url, type, false, cause);
        }
    }

    // ---------------------- CLIENT-SIDE -------------------------------------

    /** Timeout waiting for response (retryable). */
    public static final class NetworkTimeoutException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public NetworkTimeoutException(String msg, String svc, String name, String trace,
                                       String url, String type, Throwable cause) {
            super(msg, svc, name, trace, url, type, true, cause);
        }
    }

    /** Local connection failure (DNS, SSL, socket). */
    public static final class NetworkConnectionException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public NetworkConnectionException(String msg, String svc, String name, String trace,
                                          String url, String type, Throwable cause) {
            super(msg, svc, name, trace, url, type, true, cause);
        }
    }

    /** Failed to parse or interpret JSON. */
    public static final class NetworkDecodeException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public NetworkDecodeException(String msg, String svc, String name, String trace,
                                      String url, String type, Throwable cause) {
            super(msg, svc, name, trace, url, type, false, cause);
        }
    }

    /** Request rejected before send (invalid params). */
    public static final class NetworkValidationException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public NetworkValidationException(String msg, String svc, String name, String trace,
                                          String url, String type, Throwable cause) {
            super(msg, svc, name, trace, url, type, false, cause);
        }
    }

    /** Internal logic failure inside the network engine. */
    public static final class NetworkInternalException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public NetworkInternalException(String msg, String svc, String name, String trace,
                                        String url, String type, Throwable cause) {
            super(msg, svc, name, trace, url, type, false, cause);
        }
    }

    // ---------------------- RESILIENCE --------------------------------------

    /** Local rate limiter token bucket depleted. */
    public static final class RateLimitException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public RateLimitException(String msg, String svc, String name, String trace,
                                  String url, String type, Throwable cause) {
            super(msg != null ? msg : "RATE_LIMITED: local bucket empty",
                    svc, name, trace, url, type, true, cause);
        }
    }

    /** Circuit breaker open (too many consecutive failures). */
    public static final class CircuitOpenException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public CircuitOpenException(String msg, String svc, String name, String trace,
                                    String url, String type, Throwable cause) {
            super(msg != null ? msg : "CIRCUIT_OPEN: breaker active",
                    svc, name, trace, url, type, false, cause);
        }
    }

    /** Retry attempts exceeded per policy. */
    public static final class RetryExhaustedException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public RetryExhaustedException(String msg, String svc, String name, String trace,
                                       String url, String type, Throwable cause) {
            super(msg != null ? msg : "RETRY_EXHAUSTED: maximum attempts reached",
                    svc, name, trace, url, type, false, cause);
        }
    }

    // ---------------------- CACHE -------------------------------------------

    /** Failed to write network result to memory cache. */
    public static final class CacheWriteException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public CacheWriteException(String msg, String svc, String name, String trace,
                                   String url, String type, Throwable cause) {
            super(msg != null ? msg : "CACHE_WRITE_FAILED: could not store JSON",
                    svc, name, trace, url, type, false, cause);
        }
    }

    /** Failed to read memory-cached result. */
    public static final class CacheReadException extends NetworkException {
        private static final long serialVersionUID = 1L;
        public CacheReadException(String msg, String svc, String name, String trace,
                                  String url, String type, Throwable cause) {
            super(msg != null ? msg : "CACHE_READ_FAILED: could not load from memory",
                    svc, name, trace, url, type, false, cause);
        }
    }
}
