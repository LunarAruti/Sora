package ucadmin.network;

import ucadmin.exceptions.NetworkException;
import ucadmin.exceptions.NetworkException.ErrorType;
import ucadmin.util.Logger;
import ucadmin.database.DatabaseManager;
import ucadmin.exceptions.DatabaseException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;


/**
 * Public entrypoint for the UC Admin network module.
 *
 * <p>Callers construct a sealed {@link NetworkRequest} and pass it to
 * {@link #request(NetworkRequest)}. The request is enqueued for asynchronous
 * execution by one of the background {@link NetworkWorker} threads.</p>
 *
 * <p>High-level behavior:</p>
 * <ul>
 *     <li>Validates the request (non-null, sealed, basic invariants).</li>
 *     <li>Performs simple in-flight deduplication using the request's dedupe key.</li>
 *     <li>Enqueues a {@link NetworkTask} into a shared {@link DelayQueue}.</li>
 *     <li>Returns {@code true} if the task was accepted, {@code false} if rejected
 *         (e.g., during shutdown or if the queue is at capacity).</li>
 *     <li>Throws {@link NetworkException} only for immediate/validation errors.</li>
 * </ul>
 *
 * <p>Results are written into the DBM temp cache by the workers; callers
 * should read them using {@code DatabaseManager} with the {@code cachePath}
 * configured on the {@link NetworkRequest}.</p>
 */
public final class NetworkManager {

    /** Default number of worker threads if not otherwise configured. */
    private static final int DEFAULT_WORKER_COUNT = 2;

    /**
     * Soft upper bound on the number of queued tasks. This prevents unbounded
     * memory usage if callers enqueue faster than the workers can drain.
     */
    private static final int MAX_QUEUE_SIZE = 10_000;

    /** Global task queue shared across all workers. */
    private static final DelayQueue<NetworkTask> QUEUE = new DelayQueue<>();

    /**
     * Global deduplication map: dedupeKey -> in-flight sentinel.
     *
     * <p>If a new request arrives with a dedupeKey that already exists in
     * this map, it is treated as coalesced with the existing logical request:
     * we do not enqueue a duplicate, but we still return true from
     * {@link #request(NetworkRequest)} to indicate the system is already
     * working on that key.</p>
     */
    private static final Map<String, Boolean> INFLIGHT_DEDUPE = new ConcurrentHashMap<>();

    /** Worker instances (for shutdown coordination). */
    private static final List<NetworkWorker> WORKERS = new ArrayList<>();

    /** Worker threads. */
    private static final List<Thread> WORKER_THREADS = new ArrayList<>();

    /** Global lifecycle flags. */
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);
    private static final AtomicInteger CACHE_SUFFIX_COUNTER = new AtomicInteger(0);

    private NetworkManager() {
        // no instances
    }

    // ----------------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------------

    /**
     * Initializes the UC Admin network subsystem and starts the default number
     * of background worker threads.
     *
     * <p>This must be called before any network requests are submitted.
     * Calling it multiple times is safe — subsequent calls are ignored
     * once the subsystem is already started.</p>
     *
     * <p>Side effects:</p>
     * <ul>
     *     <li>Creates and launches the default number of {@code NetworkWorker} threads.</li>
     *     <li>Clears any shutdown markers and prepares shared structures (queue, dedupe map).</li>
     * </ul>
     *
     * <p>Usage:</p>
     * <pre>
     * NetworkManager.start();
     * </pre>
     *
     * <p>This should normally be invoked once during bot startup.</p>
     *
     * @return true if the subsystem started, false if it was already running
     */
    public static boolean start() {
        Logger.log(Logger.TAG.SYSTEM, "[NetworkManager] start() called (default worker count).");
        return start(DEFAULT_WORKER_COUNT);
    }

    /**
     * Starts the network subsystem using a caller-defined number of worker threads.
     *
     * <p>Useful when the caller wants higher parallel request throughput or needs
     * to tune performance based on hardware constraints.</p>
     *
     * <p>workerCount must be ≥ 1. Creating too many workers may lead to excessive
     * parallelism or rate-limiter starvation depending on configuration.</p>
     *
     * <p>If the subsystem is already running, this method does nothing.</p>
     *
     * <p>Usage:</p>
     * <pre>
     * NetworkManager.start(4); // start with 4 worker threads
     * </pre>
     *
     * @param workerCount number of worker threads to start
     * @return true if the subsystem started, false if it was already running
     */
    public static boolean start(int workerCount) {
        if (workerCount < 1) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkManager] Invalid workerCount=" + workerCount);
            throw new IllegalArgumentException("workerCount must be >= 1 (was " + workerCount + ")");
        }
        if (!STARTED.compareAndSet(false, true)) {
            Logger.log(Logger.TAG.WARN,
                    "[NetworkManager] start() ignored → already started.");
            return false;
        }

        SHUTTING_DOWN.set(false);
        Logger.log(Logger.TAG.SYSTEM,
                "[NetworkManager] Starting network workers: count=" + workerCount);

        for (int i = 0; i < workerCount; i++) {
            String name = "NetworkWorker-" + i;
            NetworkWorker worker = new NetworkWorker(name, QUEUE, INFLIGHT_DEDUPE);
            Thread thread = new Thread(worker, name);
            thread.setDaemon(true);

            WORKERS.add(worker);
            WORKER_THREADS.add(thread);
            thread.start();

            Logger.log(Logger.TAG.SYSTEM, "[NetworkManager] Worker started: " + name);
        }

        Logger.log(Logger.TAG.SYSTEM,
                "[NetworkManager] All network workers started successfully.");
        return true;
    }

    /**
     * Gracefully stops all network worker threads and prevents further requests
     * from being accepted.
     *
     * <p>This is normally called once during bot shutdown. The method:
     * <ul>
     *     <li>Signals all workers to stop</li>
     *     <li>Interrupts queue wait states</li>
     *     <li>Waits (briefly) for workers to exit</li>
     * </ul>
     *
     * <p>Calls after shutdown are ignored.</p>
     *
     * <p>Usage:</p>
     * <pre>
     * NetworkManager.shutdown();
     * </pre>
     *
     * @return true if shutdown was initiated, false if it was already stopped/shutting down
     */
    public static boolean shutdown() {
        if (!STARTED.get()) {
            Logger.log(Logger.TAG.WARN,
                    "[NetworkManager] shutdown() ignored → not started.");
            return false;
        }
        if (!SHUTTING_DOWN.compareAndSet(false, true)) {
            Logger.log(Logger.TAG.WARN,
                    "[NetworkManager] shutdown() ignored → already shutting down.");
            return false;
        }

        Logger.log(Logger.TAG.SYSTEM, "[NetworkManager] Shutting down network workers...");

        for (NetworkWorker worker : WORKERS) {
            worker.shutdown();
        }

        for (Thread t : WORKER_THREADS) {
            t.interrupt();
        }

        for (Thread t : WORKER_THREADS) {
            try {
                t.join(3_000L);
            } catch (InterruptedException ignored) {
                Logger.log(Logger.TAG.WARN,
                        "[NetworkManager] shutdown() interrupted while waiting for worker=" + t.getName());
                Thread.currentThread().interrupt();
            }
            if (t.isAlive()) {
                Logger.log(Logger.TAG.WARN,
                        "[NetworkManager] worker still alive after join: " + t.getName());
            } else {
                Logger.log(Logger.TAG.INFO,
                        "[NetworkManager] worker joined: " + t.getName());
            }
        }

        Logger.log(Logger.TAG.SYSTEM, "[NetworkManager] Network workers shut down.");
        return true;
    }

    // ----------------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------------

    /**
     * Submits a fully-constructed and sealed {@link NetworkRequest} into the
     * asynchronous network execution pipeline.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>The request is validated and automatically {@code seal()}ed if needed.</li>
     *     <li>If the system is shutting down or the queue is full, {@code false} is returned.</li>
     *     <li>If another in-flight request shares the same dedupe key, this call
     *     does NOT enqueue a duplicate; instead it returns {@code true} to signal
     *     that the logical work is already in progress.</li>
     *     <li>Otherwise, a {@link NetworkTask} is enqueued into the worker queue.</li>
     * </ul>
     *
     * <p>Return values:</p>
     * <ul>
     *     <li><b>true</b> → request was accepted or coalesced</li>
     *     <li><b>false</b> → request rejected (shutdown or queue capacity reached)</li>
     *     <li><b>throws NetworkException</b> → request configuration or lifecycle error</li>
     * </ul>
     *
     * <p>Usage:</p>
     * <pre>
     * NetworkRequest req = new NetworkRequest("roblox", "GetUser")
     *      .setType(GET)
     *      .seal();
     *
     * boolean ok = NetworkManager.request(req);
     * </pre>
     *
     * @param request a sealed NetworkRequest object
     * @return {@code true} if accepted, {@code false} if rejected
     * @throws NetworkException for validation or lifecycle failures
     */
    public static boolean request(NetworkRequest request) throws NetworkException {
        Objects.requireNonNull(request, "request must not be null");

        if (!STARTED.get()) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkManager] request() before start(): service=" +
                            request.getService() + ", name=" + request.getName());
            throw new NetworkException(
                    ErrorType.POLICY_VIOLATION,
                    "NetworkManager.request() called before NetworkManager.start()."
            );
        }

        if (SHUTTING_DOWN.get()) {
            Logger.log(Logger.TAG.WARN,
                    "[NetworkManager] Rejecting request during shutdown: service=" +
                            request.getService() + ", name=" + request.getName());
            return false;
        }

        try {
            request.seal();
        } catch (IllegalStateException ise) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkManager] request seal() failed: " + ise.getMessage());
            throw new NetworkException(
                    ErrorType.INVALID_REQUEST,
                    "NetworkRequest failed validation during seal(): " + ise.getMessage(),
                    ise,
                    request.getService(),
                    request.getName(),
                    request.getTraceId(),
                    null,
                    null
            );
        }

        validateHighLevel(request);

        // Resolve cache path collisions on disk (best-effort).
        String resolvedCachePath = resolveUniqueCachePath(request.getCachePath(), request);
        if (!resolvedCachePath.equals(request.getCachePath())) {
            request.overrideCachePathInternal(resolvedCachePath);
        }

        String dedupeKey = request.getDedupeKey();
        if (dedupeKey != null && !dedupeKey.isBlank()) {
            Boolean existing = INFLIGHT_DEDUPE.putIfAbsent(dedupeKey, Boolean.TRUE);
            if (existing != null) {
                Logger.log(Logger.TAG.REQUEST,
                        "[NetworkManager] Coalesced request (dedupeKey=" + dedupeKey +
                                ") service=" + request.getService() +
                                ", name=" + request.getName());
                return true;
            }
        }

        int currentSize = QUEUE.size();
        if (currentSize >= MAX_QUEUE_SIZE) {
            Logger.log(Logger.TAG.WARN,
                    "[NetworkManager] Queue FULL (" + currentSize + "/" + MAX_QUEUE_SIZE +
                            ") rejecting request: service=" + request.getService() +
                            ", name=" + request.getName());

            if (dedupeKey != null && !dedupeKey.isBlank()) {
                INFLIGHT_DEDUPE.remove(dedupeKey);
            }
            return false;
        }

        long now = System.currentTimeMillis();
        NetworkTask task = new NetworkTask(request, 1, now);

        boolean offered = false;
        try {
            offered = QUEUE.offer(task);
        } catch (Throwable t) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkManager] Exception while enqueuing task: " + t.getMessage());
        }

        if (!offered) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkManager] Failed to enqueue task: service=" +
                            request.getService() + ", name=" + request.getName());

            // mandatory cleanup
            if (dedupeKey != null && !dedupeKey.isBlank()) {
                INFLIGHT_DEDUPE.remove(dedupeKey);
            }
            return false;
        }

        Logger.log(Logger.TAG.REQUEST,
                "[NetworkManager] Enqueued request: service=" + request.getService() +
                        ", name=" + request.getName() +
                        ", priority=" + request.getPriority() +
                        ", traceId=" + request.getTraceId() +
                        ", cachePath=" + request.getCachePath());

        return true;
    }

    /**
     * Submits a {@link NetworkRequest} and returns the DBM TEMP cache path where
     * its eventual result JSON will be written asynchronously by a worker.
     *
     * <p>This method is commonly used by front-end callers who want to issue a
     * request and immediately know where to read the result from after workers
     * finish processing.</p>
     *
     * <p>Semantics:</p>
     * <ul>
     *     <li>Returns a non-null <b>cache path</b> when request was accepted.</li>
     *     <li>Returns <b>null</b> if the request was rejected immediately
     *     (shutdown / queue full).</li>
     *     <li>Throws <b>NetworkException</b> for configuration or lifecycle errors.</li>
     * </ul>
     *
     * <p>Note: This method does <i>not</i> wait for any network response.
     * It only returns the location where the worker will eventually write it.</p>
     *
     * <p>Usage:</p>
     * <pre>
     * String path = NetworkManager.requestAndReturnCachePath(req);
     * if (path != null) {
     *     // Later...
     *     JSONObject result = DatabaseManager.readJSONRaw(path);
     * }
     * </pre>
     *
     * @param request sealed NetworkRequest
     * @return cache path or null
     * @throws NetworkException if request is malformed or subsystem not started
     */
    public static String requestAndReturnCachePath(NetworkRequest request) throws NetworkException {
        Objects.requireNonNull(request, "request must not be null");

        Logger.log(Logger.TAG.DEBUG,
                "[NetworkManager] requestAndReturnCachePath: service=" +
                        request.getService() + ", name=" + request.getName());

        boolean accepted = request(request);

        String cachePath = request.getCachePath();

        if (!accepted) {
            Logger.log(Logger.TAG.WARN,
                    "[NetworkManager] requestAndReturnCachePath: NOT ACCEPTED service=" +
                            request.getService() + ", name=" + request.getName());
            return null;
        }

        if (cachePath == null || cachePath.isBlank()) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkManager] NRO has null/blank cachePath after acceptance!");
            throw new NetworkException(
                    ErrorType.INVALID_REQUEST,
                    "NetworkRequest.cachePath is null/blank after request(); cannot determine result location.",
                    null,
                    request.getService(),
                    request.getName(),
                    request.getTraceId(),
                    null,
                    null,
                    null
            );
        }

        return cachePath;
    }

    // ----------------------------------------------------------------------
    // Validation helpers
    // ----------------------------------------------------------------------

    /**
     * Performs light high-level validation that is cheap and logical-layer
     * focused. Heavy validation (URL, path template, body rules, auth) is
     * handled inside {@link NetworkRequest#seal()}.
     *
     * @param request sealed NetworkRequest
     * @throws NetworkException on validation failures
     */
    private static void validateHighLevel(NetworkRequest request) throws NetworkException {
        Logger.log(Logger.TAG.DEBUG,
                "[NetworkManager] validateHighLevel: service=" +
                        request.getService() + ", name=" + request.getName());

        if (!request.isSealed()) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkManager] validateHighLevel: request not sealed!");
            throw new NetworkException(
                    ErrorType.INVALID_REQUEST,
                    "NetworkRequest must be sealed before enqueue."
            );
        }

        if (request.getService() == null || request.getService().isBlank()) {
            throw new NetworkException(
                    ErrorType.INVALID_REQUEST,
                    "NetworkRequest.service must not be null/blank."
            );
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new NetworkException(
                    ErrorType.INVALID_REQUEST,
                    "NetworkRequest.name must not be null/blank."
            );
        }

        String cachePath = request.getCachePath();
        if (cachePath == null || cachePath.isBlank()) {
            throw new NetworkException(
                    ErrorType.INVALID_REQUEST,
                    "NetworkRequest.cachePath must not be null/blank; ensure seal() has been called."
            );
        }

        if (!cachePath.startsWith("database/")) {
            Logger.log(Logger.TAG.WARN,
                    "[NetworkManager] Non-database cachePath: " +
                            cachePath + " service=" + request.getService() +
                            ", name=" + request.getName());
        }
    }

    private static String resolveUniqueCachePath(String basePath, NetworkRequest request) {
        String path = ensureJsonPath(basePath);
        if (path == null) return basePath;

        try {
            boolean disk = DatabaseManager.fileExists(path);
            boolean cache = DatabaseManager.cacheExists(path);
            if (!disk && !cache) {
                return path;
            }
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.WARN,
                    "[NetworkManager] cache path check failed, using base: " + e.getMessage());
            return path;
        }

        int dot = path.toLowerCase(Locale.ROOT).lastIndexOf(".json");
        String stem = (dot >= 0) ? path.substring(0, dot) : path;
        String suffix = "-dup-" + System.currentTimeMillis() + "-" + CACHE_SUFFIX_COUNTER.incrementAndGet();
        String candidate = stem + suffix + ".json";

        Logger.log(Logger.TAG.WARN,
                "[NetworkManager] cache path collision; using " + candidate +
                        " service=" + request.getService() + " name=" + request.getName());
        return candidate;
    }

    private static String ensureJsonPath(String path) {
        if (path == null) return null;
        if (path.toLowerCase(Locale.ROOT).endsWith(".json")) return path;
        return path + ".json";
    }

    /**
     * Builds and returns a structured snapshot of the current network subsystem
     * state. This method is safe to call at any time and does not block workers.
     *
     * <p>The snapshot includes:</p>
     * <ul>
     *     <li>whether the subsystem is started</li>
     *     <li>whether it is shutting down</li>
     *     <li>current queue size</li>
     *     <li>queue capacity</li>
     *     <li>in-flight dedupe count</li>
     *     <li>circuit breaker snapshots</li>
     *     <li>rate limiter snapshots</li>
     * </ul>
     *
     * <p>Useful for debugging, admin panels, or health monitoring.</p>
     *
     * <p>Usage:</p>
     * <pre>
     * Map<String, Object> diag = NetworkManager.getDiagnosticsSummary();
     * System.out.println(diag);
     * </pre>
     *
     * @return immutable map containing diagnostics
     */
    public static Map<String, Object> getDiagnosticsSummary() {
        Logger.log(Logger.TAG.DEBUG,
                "[NetworkManager] Building diagnostics summary…");

        Map<String, Object> out = new LinkedHashMap<>();

        out.put("started", STARTED.get());
        out.put("shuttingDown", SHUTTING_DOWN.get());
        out.put("queueSize", QUEUE.size());
        out.put("maxQueueSize", MAX_QUEUE_SIZE);
        out.put("inflightDedupeCount", INFLIGHT_DEDUPE.size());

        out.put("circuitBreakers", CircuitBreakerRegistry.snapshot());
        out.put("rateLimiters", RateLimiterRegistry.snapshot());

        return Collections.unmodifiableMap(out);
    }

    /**
     * Serializes the full diagnostics snapshot into a TEMP DBM JSON file at:
     *
     * <pre>
     *   database/network/diagnostics/state.json
     * </pre>
     *
     * <p>This is useful for live debugging, admin commands, or exporting health
     * information without disrupting worker threads.</p>
     *
     * <p>If the write fails, a {@link NetworkException} is thrown.</p>
     *
     * <p>Usage:</p>
     * <pre>
     * String path = NetworkManager.dumpDiagnosticsToTemp();
     * JSONObject diag = DatabaseManager.readJSONRaw(path);
     * </pre>
     *
     * @return cache path where diagnostics JSON was written
     * @throws NetworkException if DBM write fails
     */
    public static String dumpDiagnosticsToTemp() throws NetworkException {
        final String path = "database/network/diagnostics/state.json";

        Logger.log(Logger.TAG.SYSTEM,
                "[NetworkManager] Dumping diagnostics to " + path);

        Map<String, Object> diag = getDiagnosticsSummary();
        diag = new LinkedHashMap<>(diag);
        diag.put("generated_at_ms", System.currentTimeMillis());
        diag.put("cache_root", "database/network");
        diag.put("worker_count", WORKER_THREADS.size());
        diag.put("queue_capacity", MAX_QUEUE_SIZE);
        JSONObject json = new JSONObject(diag);

        try {
            DatabaseManager.createJSON(path);
            DatabaseManager.makeTemporary(path);
            DatabaseManager.writeJSONPath(path, null, json, true);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkManager] Failed to write diagnostics to DBM at " +
                            path + ": " + e.getMessage());
            throw new NetworkException(
                    ErrorType.UNKNOWN,
                    "Failed to write network diagnostics to DBM.",
                    e,
                    "network",
                    "diagnostics",
                    null,
                    path,
                    null,
                    null
            );
        }

        Logger.log(Logger.TAG.INFO,
                "[NetworkManager] Wrote network diagnostics to TEMP path: " + path);

        return path;
    }

    /**
     * Serializes the in-memory NetworkJournal ring buffer into a TEMP DBM JSON file at:
     *
     * <pre>
     *   database/network/journal/recent.json
     * </pre>
     *
     * @return cache path where journal JSON was written
     * @throws NetworkException if DBM write fails
     */
    public static String dumpJournalToTemp() throws NetworkException {
        final String path = "database/network/journal/recent.json";

        Logger.log(Logger.TAG.SYSTEM,
                "[NetworkManager] Dumping NetworkJournal to " + path);

        var entries = NetworkJournal.snapshot();
        org.json.JSONArray arr = new org.json.JSONArray();
        for (NetworkJournal.JournalEntry e : entries) {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("logged_at_ms", e.getLoggedAtMillis());
            obj.put("service", e.getService());
            obj.put("endpoint", e.getEndpoint());
            obj.put("trace_id", e.getTraceId());
            obj.put("final_url", e.getFinalUrl());
            obj.put("status_code", e.getStatusCode());
            obj.put("attempts", e.getAttempts());
            obj.put("latency_ms", e.getLatencyMillis());
            obj.put("success", e.isSuccess());
            obj.put("error_type", e.getErrorType() != null ? e.getErrorType().name() : "null");
            arr.put(obj);
        }

        org.json.JSONObject root = new org.json.JSONObject();
        root.put("generated_at_ms", System.currentTimeMillis());
        root.put("count", entries.size());
        root.put("entries", arr);

        try {
            DatabaseManager.createJSON(path);
            DatabaseManager.makeTemporary(path);
            DatabaseManager.writeJSONPath(path, null, root, true);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR,
                    "[NetworkManager] Failed to write NetworkJournal to DBM at " +
                            path + ": " + e.getMessage());
            throw new NetworkException(
                    ErrorType.UNKNOWN,
                    "Failed to write network journal to DBM.",
                    e,
                    "network",
                    "journal",
                    null,
                    path,
                    null,
                    null
            );
        }

        Logger.log(Logger.TAG.INFO,
                "[NetworkManager] Wrote NetworkJournal to TEMP path: " + path);

        return path;
    }

}
