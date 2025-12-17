package ucadmin.network;

import org.json.JSONArray;
import org.json.JSONObject;
import ucadmin.database.DatabaseManager;
import ucadmin.exceptions.DatabaseException;
import ucadmin.exceptions.NetworkException;
import ucadmin.util.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background worker that consumes {@link NetworkTask} items from a queue and
 * executes them using the HTTP engine, retry logic, and resilience helpers.
 *
 * <p>This class is not public API. It is owned and managed by {@link NetworkManager},
 * which exposes the {@code request(NetworkRequest)} method and handles lifecycle.</p>
 *
 * Responsibilities:
 * <ul>
 *     <li>Take tasks from the {@link DelayQueue} (respecting backoff delays).</li>
 *     <li>Check circuit breaker and rate limiter.</li>
 *     <li>Execute a single HTTP attempt via {@link HttpExecutor}.</li>
 *     <li>Apply {@link RetryDecider} to decide requeue vs final outcome.</li>
 *     <li>Apply {@link NetworkRequest.FailureMode} to determine how to handle failures.</li>
 *     <li>Write success/error payloads into DBM using {@link DatabaseManager}.</li>
 * </ul>
 */
final class NetworkWorker implements Runnable {

    /** Max time (ms) we are willing to "hold" a task waiting for a circuit to close. */
    private static final long MAX_CIRCUIT_HOLD_MS = 5_000L;

    private final String workerName;
    private final DelayQueue<NetworkTask> queue;
    /**
     * Global dedupe map shared across all workers, managed by {@link NetworkManager}.
     * Key: dedupeKey, Value: simple boolean sentinel.
     */
    private final Map<String, Boolean> inflightDedupe;

    private final AtomicBoolean running = new AtomicBoolean(true);

    NetworkWorker(String workerName,
                  DelayQueue<NetworkTask> queue,
                  Map<String, Boolean> inflightDedupe) {
        this.workerName = Objects.requireNonNull(workerName, "workerName");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.inflightDedupe = Objects.requireNonNull(inflightDedupe, "inflightDedupe");
    }

    /**
     * Signals the worker loop to stop after the current iteration.
     * {@link NetworkManager} is responsible for joining the thread.
     */
    void shutdown() {
        running.set(false);
    }

    @Override
    public void run() {
        Logger.log(Logger.TAG.SYSTEM, "[NetworkWorker " + workerName + "] started.");
        while (running.get() || !queue.isEmpty()) {
            try {
                NetworkTask task = queue.take(); // respects delay/backoff
                processTask(task);

            } catch (InterruptedException ie) {
                // Allow graceful shutdown; do not spam logs.
                if (!running.get()) break;
                Logger.log(Logger.TAG.WARN,
                        "[NetworkWorker " + workerName + "] interrupted while running, continuing: " + ie.getMessage());

            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR,
                        "[NetworkWorker " + workerName + "] UNCAUGHT throwable: " + t.toString());
                t.printStackTrace(); // ensure visibility in JVM logs
            }
        }
        Logger.log(Logger.TAG.SYSTEM, "[NetworkWorker " + workerName + "] stopped.");
    }

    /**
     * Processes a single logical task, including retries and DBM materialization.
     */
    private void processTask(NetworkTask task) {
        NetworkRequest request = task.getRequest();
        String dedupeKey = request.getDedupeKey();

        Logger.log(Logger.TAG.REQUEST,
                "[NetworkWorker " + workerName + "] Processing task: service=" +
                        request.getService() + ", name=" + request.getName() +
                        ", attempt=" + task.getAttemptIndex());

        try {
            executeWithRetries(task);
        } finally {
            if (dedupeKey != null && !dedupeKey.isBlank()) {
                inflightDedupe.remove(dedupeKey);
                Logger.log(Logger.TAG.DEBUG,
                        "[NetworkWorker " + workerName + "] Cleared dedupeKey=" + dedupeKey);
            }
        }
    }

    /**
     * Core attempt loop for a single logical request.
     * Uses RetryDecider to decide backoff and requeue versus final outcome.
     */
    private void executeWithRetries(NetworkTask initialTask) {
        NetworkRequest request = initialTask.getRequest();
        NetworkRequest.RetryPolicy policy = request.getRetryPolicy();
        NetworkRequest.FailureMode failureMode = request.getFailureMode();
        String circuitKey = request.getCircuitKey();
        String rateBucket = resolveRateBucket(request);

        int attemptIndex = initialTask.getAttemptIndex();
        NetworkException.ErrorType lastErrorType = null;
        Integer lastStatus = null;
        HttpExecutor.HttpAttemptResult lastAttempt = null;
        Exception lastException = null;

        final long startedAtMillis = System.currentTimeMillis();
        long completedAtMillis = startedAtMillis;

        while (true) {
            long now = System.currentTimeMillis();
            boolean rateLimitedOnThisAttempt = false;

            // --- CIRCUIT BREAKER ---
            if (!CircuitBreakerRegistry.allowRequest(circuitKey, now)) {
                long remaining = CircuitBreakerRegistry.getRemainingOpenMillis(circuitKey, now);
                Logger.log(Logger.TAG.REQUEST,
                        "[NetworkWorker " + workerName + "] Circuit OPEN for key=" + circuitKey +
                                ", remaining=" + remaining + "ms");

                if (remaining > 0 && remaining <= MAX_CIRCUIT_HOLD_MS) {
                    long nextAt = now + remaining;
                    Logger.log(Logger.TAG.REQUEST,
                            "[NetworkWorker " + workerName + "] Holding task for circuit; retry scheduledAt=" + nextAt);
                    queue.offer(new NetworkTask(request, attemptIndex, nextAt));
                    return;
                }

                lastErrorType = NetworkException.ErrorType.CIRCUIT_OPEN;
                completedAtMillis = System.currentTimeMillis();
                break;
            }

            // --- RATE LIMITER ---
            if (!RateLimiterRegistry.tryAcquire(rateBucket)) {
                rateLimitedOnThisAttempt = true;
                lastErrorType = NetworkException.ErrorType.REMOTE_STATUS;
                lastStatus = 429;
                completedAtMillis = System.currentTimeMillis();

                Logger.log(Logger.TAG.REQUEST,
                        "[NetworkWorker " + workerName + "] Rate-limited: bucket=" + rateBucket +
                                " service=" + request.getService() +
                                ", name=" + request.getName());
            } else {
                // --- HTTP ATTEMPT ---
                Logger.log(Logger.TAG.REQUEST,
                        "[NetworkWorker " + workerName + "] HTTP attempt " + attemptIndex +
                                " service=" + request.getService() +
                                ", name=" + request.getName());

                try {
                    lastAttempt = HttpExecutor.executeSingleAttempt(request);
                    lastStatus = lastAttempt.getStatusCode();
                    completedAtMillis = System.currentTimeMillis();

                    Logger.log(Logger.TAG.REQUEST,
                            "[NetworkWorker " + workerName + "] HTTP status=" + lastStatus +
                                    " redirectCount=" + lastAttempt.getRedirectCount());

                    boolean success = isStatusSuccess(request, lastStatus)
                            && isContentTypeAllowed(request, lastAttempt)
                            && isBodySizeAllowed(request, lastAttempt);

                    if (success) {
                        CircuitBreakerRegistry.recordSuccess(circuitKey);
                        materializeSuccess(request, lastAttempt, attemptIndex, startedAtMillis, completedAtMillis);
                        return;
                    } else {
                        Logger.log(Logger.TAG.DEBUG,
                                "[NetworkWorker " + workerName + "] HTTP failure classification → retry may occur");
                        lastErrorType = NetworkException.ErrorType.REMOTE_STATUS;
                        CircuitBreakerRegistry.recordFailure(circuitKey, now);
                    }
                } catch (Exception e) {
                    lastException = e;
                    lastErrorType = classifyExceptionAsErrorType(e);
                    CircuitBreakerRegistry.recordFailure(circuitKey, now);
                    completedAtMillis = System.currentTimeMillis();

                    Logger.log(Logger.TAG.ERROR,
                            "[NetworkWorker " + workerName + "] HTTP exception: " + e.getMessage());
                }
            }

            // --- RETRY DECISION ---
            RetryDecider.RetryDecision decision = RetryDecider.decide(
                    policy,
                    request,
                    attemptIndex,
                    lastStatus,
                    lastErrorType
            );

            if (!decision.shouldRetry()) {
                Logger.log(Logger.TAG.REQUEST,
                        "[NetworkWorker " + workerName + "] No retry → terminal failure");
                break;
            }

            long delayMs = decision.getNextDelayMs();

            if (rateLimitedOnThisAttempt) {
                long limiterDelay = RateLimiterRegistry.estimateDelayMillis(rateBucket, 1);
                delayMs = Math.max(delayMs, limiterDelay);
            }

            long nextAt = System.currentTimeMillis() + delayMs;
            attemptIndex++;

            Logger.log(Logger.TAG.REQUEST,
                    "[NetworkWorker " + workerName + "] Scheduling RETRY attempt=" + attemptIndex +
                            " delayMs=" + delayMs +
                            " nextAt=" + nextAt);

            queue.offer(new NetworkTask(request, attemptIndex, nextAt));
            return;
        }

        // --- FINAL FAILURE ---
        handleFinalFailure(
                request,
                failureMode,
                attemptIndex,
                lastStatus,
                lastErrorType,
                lastAttempt,
                lastException,
                startedAtMillis,
                completedAtMillis
        );
    }

    // ---------------- success / failure handling ----------------

    private void materializeSuccess(
            NetworkRequest request,
            HttpExecutor.HttpAttemptResult attempt,
            int attempts,
            long startedAtMillis,
            long completedAtMillis
    ) {
        Logger.log(Logger.TAG.REQUEST,
                "[NetworkWorker " + workerName + "] SUCCESS service=" +
                        request.getService() + ", name=" + request.getName() +
                        ", attempts=" + attempts);

        boolean collectMetrics = request.isCollectMetrics();
        String cachePath = request.getCachePath();

        String body = attempt.getBody();
        JSONObject jsonObject = null;
        JSONArray jsonArray = null;
        boolean jsonDecoded = false;

        if (body != null && !body.isBlank()) {
            try {
                if (request.getResponseType() == NetworkRequest.ResponseType.JSON_ARRAY) {
                    jsonArray = new JSONArray(body);
                    jsonDecoded = true;
                } else {
                    jsonObject = new JSONObject(body);
                    jsonDecoded = true;
                }
            } catch (Exception e) {
                Logger.log(Logger.TAG.ERROR,
                        "[NetworkWorker " + workerName + "] JSON decode failed on success: " + e.getMessage());
            }
        }

        long latencyMillis = Math.max(0L, completedAtMillis - startedAtMillis);

        NetworkResult result = new NetworkResult(
                request.getService(),
                request.getName(),
                request.getTraceId(),
                attempt.getFinalUrl(),
                attempt.getRedirectCount(),
                attempts,
                attempt.getStatusCode(),
                attempt.getHeaders(),
                body,
                true,
                request.getResponseType(),
                jsonObject,
                jsonArray,
                jsonDecoded,
                startedAtMillis,
                completedAtMillis,
                latencyMillis
        );

        Object dbmValue = result.toDbmValue(collectMetrics);
        writeToDbm(cachePath, dbmValue);
    }

    private void handleFinalFailure(
            NetworkRequest request,
            NetworkRequest.FailureMode failureMode,
            int attempts,
            Integer statusCode,
            NetworkException.ErrorType errorType,
            HttpExecutor.HttpAttemptResult attempt,
            Exception cause,
            long startedAtMillis,
            long completedAtMillis
    ) {
        String urlForLog;
        try {
            urlForLog = (attempt != null) ? attempt.getFinalUrl() : request.getFinalUrl();
        } catch (IllegalStateException e) {
            urlForLog = "<unavailable>";
        }

        Logger.log(Logger.TAG.ERROR,
                "[NetworkWorker " + workerName + "] FINAL FAILURE service=" +
                        request.getService() +
                        " name=" + request.getName() +
                        " traceId=" + request.getTraceId() +
                        " attempts=" + attempts +
                        " status=" + statusCode +
                        " errorType=" + errorType +
                        " url=" + urlForLog);

        if (failureMode == NetworkRequest.FailureMode.WRITE_ERROR_JSON) {
            boolean collectMetrics = request.isCollectMetrics();
            String cachePath = request.getCachePath();

            String body = (attempt != null) ? attempt.getBody() : null;
            JSONObject jsonObject = null;
            JSONArray jsonArray = null;
            boolean jsonDecoded = false;

            if (body != null && !body.isBlank()) {
                try {
                    if (request.getResponseType() == NetworkRequest.ResponseType.JSON_ARRAY) {
                        jsonArray = new JSONArray(body);
                        jsonDecoded = true;
                    } else {
                        jsonObject = new JSONObject(body);
                        jsonDecoded = true;
                    }
                } catch (Exception e) {
                    Logger.log(Logger.TAG.ERROR,
                            "[NetworkWorker " + workerName + "] JSON decode failed on failure: " + e.getMessage());
                }
            }

            long latencyMillis = Math.max(0L, completedAtMillis - startedAtMillis);
            int finalStatus = (statusCode != null) ? statusCode : 0;

            NetworkResult result = new NetworkResult(
                    request.getService(),
                    request.getName(),
                    request.getTraceId(),
                    urlForLog,
                    (attempt != null) ? attempt.getRedirectCount() : 0,
                    attempts,
                    finalStatus,
                    (attempt != null) ? attempt.getHeaders() : null,
                    body,
                    false,
                    request.getResponseType(),
                    jsonObject,
                    jsonArray,
                    jsonDecoded,
                    startedAtMillis,
                    completedAtMillis,
                    latencyMillis
            );

            Object dbmValue = result.toDbmValue(collectMetrics);
            writeToDbm(cachePath, dbmValue);
        }
    }

    // ---------------- policy helpers ----------------

    /**
     * Determines whether the HTTP status should be treated as "successful"
     * for this request based on acceptableStatusCodes and treatOtherStatusAsError.
     */
    private boolean isStatusSuccess(NetworkRequest request, int status) {
        Set<Integer> okCodes = request.getAcceptableStatusCodes();
        boolean treatOtherAsError = request.isTreatOtherStatusAsError();

        if (okCodes != null && !okCodes.isEmpty()) {
            if (okCodes.contains(status)) {
                return true;
            }
            return !treatOtherAsError;
        }

        // Default: 2xx is success; others optionally treated as error.
        if (status >= 200 && status < 300) {
            return true;
        }
        return !treatOtherAsError;
    }

    /**
     * Enforces allowed content-types (if configured).
     * Returns true if no restrictions are set or the response matches one of them.
     */
    private boolean isContentTypeAllowed(NetworkRequest request, HttpExecutor.HttpAttemptResult attempt) {
        Set<String> allowed = request.getAllowedContentTypes();
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }

        Map<String, List<String>> headers = attempt.getHeaders();
        String ct = getFirstHeaderIgnoreCase(headers, "Content-Type");
        if (ct == null || ct.isBlank()) {
            return false;
        }

        String base = ct.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        for (String allowedType : allowed) {
            if (base.equalsIgnoreCase(allowedType.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enforces max response size, combining per-request and global/default limits.
     *
     * Resolution order:
     *  - If the request specifies maxResponseBytes, use that.
     *  - Else, fall back to NetworkConfig.computeEffectiveMaxResponseBytes(service).
     *  - If no effective limit, the body is always allowed.
     *
     * The check uses the UTF-8 byte length of the response body.
     */
    private boolean isBodySizeAllowed(NetworkRequest request, HttpExecutor.HttpAttemptResult attempt) {
        // 1) Per-request override from the NRO.
        Long maxBytes = request.getMaxResponseBytes();

        // 2) If not set, fall back to global/service-level config.
        if (maxBytes == null || maxBytes <= 0L) {
            maxBytes = NetworkConfig.computeEffectiveMaxResponseBytes(request.getService());
            if (maxBytes == null || maxBytes <= 0L) {
                // No effective limit configured anywhere.
                return true;
            }
        }

        String body = attempt.getBody();
        if (body == null) {
            return true;
        }

        // Measure actual bytes in UTF-8, since NetworkConfig deals in bytes.
        int byteLen = body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return byteLen <= maxBytes;
    }


    private String getFirstHeaderIgnoreCase(Map<String, List<String>> headers, String name) {
        if (headers == null || headers.isEmpty()) return null;
        String target = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(target)) {
                List<String> values = e.getValue();
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
        }
        return null;
    }

    private NetworkException.ErrorType classifyExceptionAsErrorType(Exception e) {
        if (e instanceof NetworkException ne) {
            return ne.getErrorType();
        }
        // A real implementation might inspect the cause hierarchy (SocketTimeoutException,
        // UnknownHostException, etc.). For now we treat unknowns as NETWORK_IO.
        return NetworkException.ErrorType.NETWORK_IO;
    }

    /**
     * Resolves the effective rate limiter bucket for this request.
     *
     * Resolution order:
     * <ol>
     *     <li>If {@link NetworkConfig} defines a default bucket for the
     *         request's service name, use that.</li>
     *     <li>Otherwise, fall back to {@link NetworkRequest#getRateBucket()},
     *         which itself defaults to the service name when unset.</li>
     * </ol>
     *
     * This allows global config to steer traffic into shared buckets
     * (e.g. "roblox.read") while still letting individual requests override
     * via {@code setRateBucket(...)} if needed.
     */
    private String resolveRateBucket(NetworkRequest request) {
        // First, see if config has an override for this logical service.
        String service = request.getService();
        String configured = NetworkConfig.getDefaultRateBucket(service);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }

        // Otherwise, use the request's own bucket (defaults to service).
        return request.getRateBucket();
    }

    /**
     * Writes a value to DBM temp cache at the root path for this request's cachePath.
     * Projection (if any) is expected to be applied at read time.
     */
    private void writeToDbm(String cachePath, Object value) {
        try {
            DatabaseManager.writeJSONPathTemp(
                    cachePath,
                    null,
                    value,
                    true
            );
            Logger.log(Logger.TAG.DEBUG,
                    "[NetworkWorker " + workerName + "] DBM write success: path=" + cachePath);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkWorker " + workerName + "] DBM write FAILED for path=" +
                            cachePath + ": " + e.getMessage());
        }
    }
}
