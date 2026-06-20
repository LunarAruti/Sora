package sora.exceptions;

import sora.util.Logger;

/**
 * Custom runtime exception for network-related failures.
 *
 * <p>This exception is designed to carry rich context about a failed network
 * operation, including:
 * <ul>
 *     <li>Logical service and endpoint names</li>
 *     <li>Trace/correlation id</li>
 *     <li>Final resolved URL</li>
 *     <li>HTTP status code (if any)</li>
 *     <li>Error classification (timeout, policy, I/O, etc.)</li>
 *     <li>Number of attempts made</li>
 * </ul>
 *
 * <p>Every constructor automatically logs a summary of the failure to
 * {@code sora/LOGGER.txt} via {@link Logger} using the ERROR tag.
 * The log entry includes the error type, service/endpoint, status code,
 * attempts, URL, trace id, and the message/cause for quick debugging.
 */
public class NetworkException extends RuntimeException {

    /**
     * High-level classification for network failures.
     * Used for logging, metrics, and error handling decisions.
     */
    public enum ErrorType {
        /** The request configuration was invalid before sending (bad URL, missing fields, etc.). */
        INVALID_REQUEST,
        /** A network or HTTP policy was violated (forbidden host, method, size, content-type, etc.). */
        POLICY_VIOLATION,
        /** The circuit breaker for this host/service is open; the call was rejected. */
        CIRCUIT_OPEN,
        /** The request exceeded its configured timeout (connect/read/wall-clock). */
        TIMEOUT,
        /** A low-level I/O failure occurred (connection dropped, DNS failure, etc.). */
        NETWORK_IO,
        /** The remote endpoint returned an unacceptable HTTP status code. */
        REMOTE_STATUS,
        /** The response body could not be decoded as expected (e.g. invalid JSON). */
        DECODE_ERROR,
        /** The request was cancelled or aborted by the system before completion. */
        CANCELLED,
        /** A generic catch-all for failures that do not match more specific categories. */
        UNKNOWN
    }

    /** Classification of the error (never null). */
    private final ErrorType errorType;

    /** Logical service/group name (e.g. "roblox"), may be null if unknown. */
    private final String service;

    /** Logical endpoint name within the service (e.g. "GetFriends"), may be null. */
    private final String endpoint;

    /** Trace/correlation id, used to tie logs/metrics together, may be null. */
    private final String traceId;

    /** Final resolved URL (after template + query rendering), may be null if not available. */
    private final String url;

    /** HTTP status code if the remote responded, null if not reached or unknown. */
    private final Integer statusCode;

    /** Number of attempts made (including the initial one), may be null if not tracked. */
    private final Integer attempts;

    /**
     * Constructs a new NetworkException with the specified detail message and
     * a generic {@link ErrorType#UNKNOWN}. No additional context is recorded.
     *
     * @param message the detail message describing the error
     */
    public NetworkException(String message) {
        this(ErrorType.UNKNOWN, message, null,
                null, null, null, null, null);
    }

    /**
     * Constructs a new NetworkException with the specified detail message,
     * cause, and a generic {@link ErrorType#UNKNOWN}.
     *
     * @param message the detail message describing the error
     * @param cause   the underlying cause of this exception
     */
    public NetworkException(String message, Throwable cause) {
        this(ErrorType.UNKNOWN, message, cause,
                null, null, null, null, null);
    }

    /**
     * Constructs a new NetworkException with a specific error type and message.
     * No additional context fields are populated.
     *
     * @param errorType classification of the network failure
     * @param message   human-readable description of the error
     */
    public NetworkException(ErrorType errorType, String message) {
        this(errorType, message, null,
                null, null, null, null, null);
    }

    /**
     * Constructs a new NetworkException with a specific error type, message, and cause.
     * No additional context fields are populated.
     *
     * @param errorType classification of the network failure
     * @param message   human-readable description of the error
     * @param cause     underlying cause (I/O exception, JSON error, etc.)
     */
    public NetworkException(ErrorType errorType, String message, Throwable cause) {
        this(errorType, message, cause,
                null, null, null, null, null);
    }

    /**
     * Fully-detailed constructor for network failures. Use this when throwing from
     * the network worker so logs and callers have complete context about what failed.
     *
     * @param errorType  classification of the network failure (never null)
     * @param message    human-readable description of the error
     * @param cause      underlying cause (I/O exception, JSON error, etc.), may be null
     * @param service    logical service name (e.g. "roblox"), may be null
     * @param endpoint   logical endpoint name (e.g. "GetFriends"), may be null
     * @param traceId    correlation id tying this request to other logs/metrics, may be null
     * @param url        final resolved request URL (including path and query), may be null
     * @param statusCode HTTP status code if the remote responded, or null if none
     * @param attempts   number of attempts made (including the first), or null if not tracked
     */
    public NetworkException(
            ErrorType errorType,
            String message,
            Throwable cause,
            String service,
            String endpoint,
            String traceId,
            String url,
            Integer statusCode,
            Integer attempts
    ) {
        super(message, cause);
        this.errorType = (errorType != null) ? errorType : ErrorType.UNKNOWN;
        this.service = service;
        this.endpoint = endpoint;
        this.traceId = traceId;
        this.url = url;
        this.statusCode = statusCode;
        this.attempts = attempts;
        logSelf();
    }

    /**
     * Convenience constructor without attempts; all other context is the same
     * as the full constructor.
     */
    public NetworkException(
            ErrorType errorType,
            String message,
            Throwable cause,
            String service,
            String endpoint,
            String traceId,
            String url,
            Integer statusCode
    ) {
        this(errorType, message, cause, service, endpoint, traceId, url, statusCode, null);
    }

    // --------- getters ---------

    /**
     * Returns the high-level classification of this network failure.
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Returns the logical service/group name associated with this request,
     * or null if not specified.
     */
    public String getService() {
        return service;
    }

    /**
     * Returns the logical endpoint name associated with this request,
     * or null if not specified.
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Returns the trace/correlation id for this request, or null if not set.
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Returns the final resolved URL for this request, or null if not available.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the HTTP status code returned by the remote endpoint, or null
     * if no response was received.
     */
    public Integer getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the number of attempts made for this request (including the initial one),
     * or null if the information was not recorded.
     */
    public Integer getAttempts() {
        return attempts;
    }

    // --------- logging helper ---------

    /**
     * Builds and emits a compact log line describing this network failure.
     * Uses Logger.TAG.ERROR.
     */
    private void logSelf() {
        StringBuilder sb = new StringBuilder("[NetworkException] ")
                .append("type=").append(errorType);

        if (service != null) {
            sb.append(" | service=").append(service);
        }
        if (endpoint != null) {
            sb.append(" | endpoint=").append(endpoint);
        }
        if (statusCode != null) {
            sb.append(" | status=").append(statusCode);
        }
        if (attempts != null) {
            sb.append(" | attempts=").append(attempts);
        }
        if (url != null) {
            sb.append(" | url=").append(url);
        }
        if (traceId != null) {
            sb.append(" | traceId=").append(traceId);
        }

        String msg = getMessage();
        if (msg != null && !msg.isBlank()) {
            sb.append(" | message=").append(msg);
        }

        Throwable cause = getCause();
        if (cause != null) {
            sb.append(" | cause=")
                    .append(cause.getClass().getSimpleName())
                    .append(": ")
                    .append(cause.getMessage());
        }

        Logger.log(Logger.TAG.ERROR, sb.toString());
    }
}
