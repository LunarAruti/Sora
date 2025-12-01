package ucadmin.network;

import ucadmin.exceptions.NetworkException.ErrorType;
import ucadmin.network.NetworkRequest.RetryMode;
import ucadmin.network.NetworkRequest.RetryPolicy;
import ucadmin.util.Logger;

import java.util.Set;

/**
 * Pure retry decision helper.
 *
 * Given a RetryPolicy, the original NetworkRequest, how many attempts have
 * been made so far, and the failure information, this class decides:
 *
 * - whether another retry should be attempted
 * - how long to wait (in milliseconds) before the next attempt
 *
 * This class has no side effects and no threading; it is easy to unit test.
 */
public final class RetryDecider {

    /** Hard cap on computed backoff delay, in milliseconds. */
    private static final long MAX_BACKOFF_MS = 60_000L;

    /**
     * Status codes that should never be retried, regardless of policy.
     * These usually represent caller/config/validation issues, not transient
     * network or backend problems.
     */
    private static final Set<Integer> HARD_NON_RETRY_STATUS = Set.of(
            400, // Bad Request – caller error
            401, // Unauthorized – auth issue
            403, // Forbidden – permission issue
            404, // Not Found – usually permanent for that URL
            405, // Method Not Allowed
            409, // Conflict – business-level issue
            410, // Gone
            422  // Unprocessable Entity – validation/business failure
    );

    private RetryDecider() {
        // utility class
    }

    /**
     * Decides whether another retry should be scheduled for this request.
     *
     * @param policy        the retry policy from the NetworkRequest (must not be null)
     * @param request       the NetworkRequest (used to determine idempotence)
     * @param attemptsSoFar how many attempts have already been made; must be >= 1
     * @param statusCode    HTTP status code if we reached the remote, or null
     * @param errorType     NetworkException.ErrorType describing the failure cause, may be null
     * @return a RetryDecision describing whether to retry and the delay before the next attempt
     */
    public static RetryDecision decide(
            RetryPolicy policy,
            NetworkRequest request,
            int attemptsSoFar,
            Integer statusCode,
            ErrorType errorType
    ) {
        Logger.log(Logger.TAG.DEBUG,
                "RetryDecider: decide start attempts=" + attemptsSoFar +
                        " status=" + statusCode +
                        " errorType=" + errorType +
                        " mode=" + (policy != null ? policy.getMode() : "<null>"));

        if (policy == null || request == null) {
            Logger.log(Logger.TAG.DEBUG, "RetryDecider: noRetry (null policy/request)");
            return RetryDecision.noRetry();
        }
        if (attemptsSoFar < 1) {
            throw new IllegalArgumentException("attemptsSoFar must be >= 1 (was " + attemptsSoFar + ")");
        }

        if (attemptsSoFar >= policy.getMaxAttempts()) {
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: noRetry (hit maxAttempts=" + policy.getMaxAttempts() + ")");
            return RetryDecision.noRetry();
        }

        // Hard non-retry error classes.
        if (errorType == ErrorType.INVALID_REQUEST
                || errorType == ErrorType.POLICY_VIOLATION
                || errorType == ErrorType.CIRCUIT_OPEN
                || errorType == ErrorType.DECODE_ERROR
                || errorType == ErrorType.CANCELLED) {
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: noRetry (hard errorType=" + errorType + ")");
            return RetryDecision.noRetry();
        }

        boolean reasonRetryable = isReasonRetryable(policy, statusCode, errorType);
        if (!reasonRetryable) {
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: noRetry (reason not retryable)");
            return RetryDecision.noRetry();
        }

        // Mode and idempotence
        boolean idempotent = isIdempotent(request);
        RetryMode mode = policy.getMode();

        switch (mode) {
            case NEVER -> {
                Logger.log(Logger.TAG.DEBUG, "RetryDecider: noRetry (mode=NEVER)");
                return RetryDecision.noRetry();
            }
            case IDEMPOTENT_ONLY -> {
                if (!idempotent) {
                    Logger.log(Logger.TAG.DEBUG,
                            "RetryDecider: noRetry (mode=IDEMPOTENT_ONLY, request non-idempotent)");
                    return RetryDecision.noRetry();
                }
            }
            case ALWAYS -> {
                // nothing additional
            }
        }

        long delayMs = computeBackoffDelay(policy, attemptsSoFar);

        Logger.log(Logger.TAG.DEBUG,
                "RetryDecider: RETRY after delayMs=" + delayMs +
                        " (attemptSoFar=" + attemptsSoFar + ")");

        return new RetryDecision(true, delayMs);
    }

    private static boolean isReasonRetryable(
            RetryPolicy policy,
            Integer statusCode,
            ErrorType errorType
    ) {
        if (errorType == ErrorType.TIMEOUT) {
            boolean r = policy.isRetryOnTimeout();
            Logger.log(Logger.TAG.DEBUG, "RetryDecider: timeout retryable=" + r);
            return r;
        }

        if (errorType == ErrorType.NETWORK_IO) {
            boolean r = policy.isRetryOnNetworkError();
            Logger.log(Logger.TAG.DEBUG, "RetryDecider: networkIO retryable=" + r);
            return r;
        }

        if (errorType == ErrorType.REMOTE_STATUS || statusCode != null) {
            int sc = (statusCode != null) ? statusCode : -1;
            boolean r = isStatusRetryable(policy, sc);
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: status retryable=" + r + " statusCode=" + sc);
            return r;
        }

        Logger.log(Logger.TAG.DEBUG,
                "RetryDecider: reason not retryable (errorType=" + errorType + ")");
        return false;
    }

    private static boolean isStatusRetryable(RetryPolicy policy, int statusCode) {
        if (statusCode <= 0) {
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: status non-retryable (invalid statusCode=" + statusCode + ")");
            return false;
        }

        if (HARD_NON_RETRY_STATUS.contains(statusCode)) {
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: status=" + statusCode + " hard non-retry");
            return false;
        }

        var configured = policy.getRetryOnStatus();
        if (configured != null && !configured.isEmpty()) {
            boolean r = configured.contains(statusCode);
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: policy retryOnStatus=" + configured + " → " + r);
            return r;
        }

        if (statusCode == 408 || statusCode == 429) {
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: status=" + statusCode + " retryable (standard)");
            return true;
        }

        boolean r = (statusCode >= 500 && statusCode < 600);
        Logger.log(Logger.TAG.DEBUG,
                "RetryDecider: status=" + statusCode + " retryable5xx=" + r);
        return r;
    }

    /**
     * Simple definition of idempotent operations:
     * - GET and DELETE are treated as idempotent.
     *
     * POST/PUT/PATCH are treated as non-idempotent here. Once NetworkRequest
     * exposes an idempotency key accessor, this method can be extended to treat
     * those as idempotent when such a key is present.
     */
    private static boolean isIdempotent(NetworkRequest request) {
        NetworkRequest.Type t = request.getType();
        return (t == NetworkRequest.Type.GET || t == NetworkRequest.Type.DELETE);
    }

    private static long computeBackoffDelay(RetryPolicy policy, int attemptsSoFar) {
        long base = policy.getInitialBackoffMs();
        double factor = policy.getBackoffFactor();

        if (base <= 0L) {
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: computeBackoffDelay base<=0 → 0ms");
            return 0L;
        }
        if (factor <= 0.0) {
            long v = Math.min(base, MAX_BACKOFF_MS);
            Logger.log(Logger.TAG.DEBUG,
                    "RetryDecider: computeBackoffDelay invalid factor → " + v + "ms");
            return v;
        }

        int exponent = Math.max(0, attemptsSoFar - 1);
        double raw = base * Math.pow(factor, exponent);

        if (raw < 0) raw = 0;
        if (raw > MAX_BACKOFF_MS) raw = MAX_BACKOFF_MS;

        long result = (long) raw;

        Logger.log(Logger.TAG.DEBUG,
                "RetryDecider: computeBackoffDelay attempts=" + attemptsSoFar +
                        " → " + result + "ms");

        return result;
    }

    /**
     * Small value object describing a retry decision.
     */
    public static final class RetryDecision {
        private final boolean shouldRetry;
        private final long nextDelayMs;

        private RetryDecision(boolean shouldRetry, long nextDelayMs) {
            this.shouldRetry = shouldRetry;
            this.nextDelayMs = nextDelayMs;
        }

        public static RetryDecision noRetry() {
            return new RetryDecision(false, 0L);
        }

        public static RetryDecision retryAfter(long delayMs) {
            return new RetryDecision(true, Math.max(0L, delayMs));
        }

        /**
         * Whether another attempt should be scheduled.
         */
        public boolean shouldRetry() {
            return shouldRetry;
        }

        /**
         * Delay before the next attempt in milliseconds.
         * Only meaningful when {@link #shouldRetry()} is true.
         */
        public long getNextDelayMs() {
            return nextDelayMs;
        }
    }
}