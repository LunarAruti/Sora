package ucadmin.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ucadmin.util.Logger;

/**
 * Per-bucket token bucket rate limiter.
 *
 * <p>Each bucket tracks:</p>
 * <ul>
 *     <li>capacity (maximum number of tokens)</li>
 *     <li>refill rate (tokens per second)</li>
 *     <li>current token count</li>
 *     <li>last refill timestamp (wall-clock millis)</li>
 * </ul>
 *
 * <p>Unknown buckets are created on first use with a default of ~10 QPS:
 * capacity = 10, refillRatePerSecond = 10.</p>
 *
 * <p>This class is thread-safe and uses per-bucket synchronization.</p>
 */
public final class RateLimiterRegistry {

    /** Default maximum tokens per bucket. */
    private static final double DEFAULT_CAPACITY = 10.0;

    /** Default refill rate in tokens per second. */
    private static final double DEFAULT_REFILL_PER_SECOND = 10.0;

    /**
     * Maximum elapsed time (ms) we will consider when refilling a bucket.
     * This guards against huge system clock jumps causing an enormous burst.
     */
    private static final long MAX_REFILL_ELAPSED_MS = 60_000L; // 60s

    private static final Map<String, Bucket> BUCKETS = new ConcurrentHashMap<>();

    private RateLimiterRegistry() {
        // utility
    }

    /**
     * Creates or updates a named token-bucket rate limiter.
     *
     * <p>Each bucket has:</p>
     * <ul>
     *     <li>capacity (max tokens)</li>
     *     <li>refill rate (tokens per second)</li>
     * </ul>
     *
     * <p>Workers automatically consume from these buckets when executing requests
     * assigned to the corresponding bucket name.</p>
     *
     * <p>Example:</p>
     * <pre>
     * RateLimiterRegistry.configureBucket("roblox.read", 30, 30);
     * </pre>
     *
     * @param bucketName identifier for this limiter (e.g., "roblox.read")
     * @param capacity maximum tokens
     * @param refillPerSecond refill rate in tokens per second
     * @return true if the bucket was created or updated, false otherwise
     */
    public static boolean configureBucket(String bucketName, double capacity, double refillPerSecond) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("bucketName must not be null/blank");
        }
        if (capacity <= 0.0 || refillPerSecond <= 0.0) {
            throw new IllegalArgumentException("capacity and refillPerSecond must be > 0");
        }

        long now = System.currentTimeMillis();
        final boolean[] changed = { false };
        BUCKETS.compute(bucketName, (name, existing) -> {
            if (existing == null) {
                Logger.log(Logger.TAG.SYSTEM,
                        "RateLimiterRegistry: created bucket=" + bucketName +
                                " cap=" + capacity + " rps=" + refillPerSecond);
                changed[0] = true;
                return new Bucket(capacity, refillPerSecond, now);
            } else {
                boolean didChange;
                synchronized (existing) {
                    didChange = existing.capacity != capacity || existing.refillPerSecond != refillPerSecond;
                    existing.capacity = capacity;
                    existing.refillPerSecond = refillPerSecond;
                    existing.tokens = Math.min(existing.tokens, capacity);
                    existing.lastRefillMillis = now;
                }
                if (didChange) {
                    Logger.log(Logger.TAG.SYSTEM,
                            "RateLimiterRegistry: updated bucket=" + bucketName +
                                    " cap=" + capacity + " rps=" + refillPerSecond);
                }
                changed[0] = didChange;
                return existing;
            }
        });
        if (!changed[0]) {
            Logger.log(Logger.TAG.WARN,
                    "RateLimiterRegistry: configureBucket no-op (unchanged) bucket=" + bucketName);
        }
        return changed[0];
    }

    /**
     * Attempts to acquire a single token from the given bucket.
     *
     * <p>If the bucket has at least one token available after refilling based
     * on elapsed time, the token is consumed and this method returns true.
     * Otherwise, it returns false and the caller should treat this as
     * "rate limited".</p>
     *
     * @param bucketName logical bucket name (e.g., "roblox.read")
     * @return true if a token was acquired, false if rate limited
     */
    public static boolean tryAcquire(String bucketName) {
        return tryAcquire(bucketName, 1);
    }

    /**
     * Attempts to acquire the given number of tokens from the bucket.
     *
     * <p>This allows heavier operations to account for their cost by requesting
     * more than one permit at a time.</p>
     *
     * @param bucketName logical bucket name (e.g., "roblox.read")
     * @param permits    number of tokens to acquire (> 0)
     * @return true if all requested permits were acquired, false if rate limited
     */
    public static boolean tryAcquire(String bucketName, int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
        if (bucketName == null || bucketName.isBlank()) {
            // No bucket name → effectively no rate limit.
            return true;
        }

        long now = System.currentTimeMillis();
        return tryAcquire(bucketName, permits, now);
    }

    /**
     * Internal helper variant that allows the caller to supply a timestamp.
     * Primarily useful for tests or scenarios where time is controlled.
     */
    static boolean tryAcquire(String bucketName, int permits, long nowMillis) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0");
        }
        if (bucketName == null || bucketName.isBlank()) {
            return true; // no rate limit → no log
        }

        Bucket bucket = BUCKETS.computeIfAbsent(
                bucketName,
                name -> {
                    Logger.log(Logger.TAG.SYSTEM,
                            "RateLimiterRegistry: auto-create bucket=" + bucketName +
                                    " cap=" + DEFAULT_CAPACITY + " rps=" + DEFAULT_REFILL_PER_SECOND);
                    return new Bucket(DEFAULT_CAPACITY, DEFAULT_REFILL_PER_SECOND, nowMillis);
                }
        );

        synchronized (bucket) {
            bucket.refill(nowMillis);

            if (bucket.tokens >= permits) {
                bucket.tokens -= permits;
                if (bucket.tokens < 0.0) bucket.tokens = 0.0;

                Logger.log(Logger.TAG.REQUEST,
                        "RateLimiterRegistry: ACQUIRE bucket=" + bucketName +
                                " permits=" + permits +
                                " remaining=" + bucket.tokens);

                return true;
            }

            Logger.log(Logger.TAG.WARN,
                    "RateLimiterRegistry: BLOCKED bucket=" + bucketName +
                            " permits=" + permits +
                            " tokens=" + bucket.tokens);

            return false;
        }
    }

    /**
     * Estimates how many milliseconds until the given number of permits is
     * likely to be available in the specified bucket.
     *
     * <p>If the permits can be acquired immediately (after a refill), this
     * returns 0. If the bucket is effectively disabled (no name) or has an
     * invalid configuration, this returns 0 as well. The value is an estimate
     * based purely on current tokens and refill rate.</p>
     *
     * @param bucketName logical bucket name
     * @param permits    number of tokens desired (> 0)
     * @return estimated delay in milliseconds until the permits are available
     */
    public static long estimateDelayMillis(String bucketName, int permits) {
        if (bucketName == null || bucketName.isBlank()) {
            return 0L;
        }
        if (permits <= 0) {
            return 0L;
        }

        long now = System.currentTimeMillis();
        Bucket bucket = BUCKETS.computeIfAbsent(
                bucketName,
                name -> {
                    Logger.log(Logger.TAG.SYSTEM,
                            "RateLimiterRegistry: auto-create bucket (estimate)=" + bucketName);
                    Bucket b = new Bucket(DEFAULT_CAPACITY, DEFAULT_REFILL_PER_SECOND, now);
                    b.tokens = 0.0;
                    return b;
                }
        );

        synchronized (bucket) {
            bucket.refill(now);

            if (bucket.tokens >= permits) {
                Logger.log(Logger.TAG.DEBUG,
                        "RateLimiterRegistry: estimateDelay=0 bucket=" + bucketName);
                return 0L;
            }

            double deficit = permits - bucket.tokens;
            if (bucket.refillPerSecond <= 0.0) {
                Logger.log(Logger.TAG.WARN,
                        "RateLimiterRegistry: bucket misconfigured (rps<=0) → infinite delay bucket=" + bucketName);
                return Long.MAX_VALUE;
            }

            double seconds = deficit / bucket.refillPerSecond;
            long millis = (long) Math.ceil(seconds * 1000.0);
            long result = Math.max(0L, millis);

            Logger.log(Logger.TAG.DEBUG,
                    "RateLimiterRegistry: estimateDelay bucket=" + bucketName +
                            " delayMs=" + result +
                            " deficit=" + deficit);

            return result;
        }
    }

    /**
     * Immutable snapshot of a rate limiter bucket for diagnostics.
     */
    public static final class RateLimiterSnapshot {
        private final String bucketName;
        private final double capacity;
        private final double refillPerSecond;
        private final double tokens;
        private final long lastRefillMillis;

        public RateLimiterSnapshot(
                String bucketName,
                double capacity,
                double refillPerSecond,
                double tokens,
                long lastRefillMillis
        ) {
            this.bucketName = bucketName;
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = tokens;
            this.lastRefillMillis = lastRefillMillis;
        }

        public String getBucketName() {
            return bucketName;
        }

        public double getCapacity() {
            return capacity;
        }

        public double getRefillPerSecond() {
            return refillPerSecond;
        }

        public double getTokens() {
            return tokens;
        }

        public long getLastRefillMillis() {
            return lastRefillMillis;
        }

        @Override
        public String toString() {
            return "RateLimiterSnapshot{" +
                    "bucketName='" + bucketName + '\'' +
                    ", capacity=" + capacity +
                    ", refillPerSecond=" + refillPerSecond +
                    ", tokens=" + tokens +
                    ", lastRefillMillis=" + lastRefillMillis +
                    '}';
        }
    }

    /**
     * Returns a snapshot of all circuit breaker entries, including their state,
     * failure counters, and remaining open time.
     *
     * <p>Intended for diagnostics and admin tools.</p>
     *
     * @return immutable map of snapshots
     */
    public static Map<String, RateLimiterSnapshot> snapshot() {
        Logger.log(Logger.TAG.DEBUG, "RateLimiterRegistry: snapshot requested");

        Map<String, RateLimiterSnapshot> out = new LinkedHashMap<>();
        for (Map.Entry<String, Bucket> e : BUCKETS.entrySet()) {
            String name = e.getKey();
            Bucket bucket = e.getValue();
            if (bucket == null) continue;

            synchronized (bucket) {
                RateLimiterSnapshot snap = new RateLimiterSnapshot(
                        name,
                        bucket.capacity,
                        bucket.refillPerSecond,
                        bucket.tokens,
                        bucket.lastRefillMillis
                );
                out.put(name, snap);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Internal token bucket implementation.
     */
    private static final class Bucket {
        double capacity;
        double refillPerSecond;

        double tokens;
        long lastRefillMillis;

        Bucket(double capacity, double refillPerSecond, long nowMillis) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
            this.tokens = capacity;
            this.lastRefillMillis = nowMillis;
        }

        void refill(long nowMillis) {
            if (nowMillis <= lastRefillMillis) {
                // Time went backwards or did not advance; skip refill.
                return;
            }
            long elapsedMillis = nowMillis - lastRefillMillis;

            // Guard against huge jumps (e.g., system clock changes).
            if (elapsedMillis > MAX_REFILL_ELAPSED_MS) {
                elapsedMillis = MAX_REFILL_ELAPSED_MS;
            }

            double elapsedSeconds = elapsedMillis / 1000.0;
            double added = elapsedSeconds * refillPerSecond;
            tokens = Math.min(capacity, tokens + added);
            if (tokens < 0.0) {
                tokens = 0.0;
            }
            lastRefillMillis = nowMillis;
        }
    }
}
