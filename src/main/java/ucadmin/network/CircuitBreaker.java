package ucadmin.network;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple half-open circuit breaker.
 * Protects from repeatedly calling failing upstreams.
 */
public final class CircuitBreaker {
    private static final ConcurrentHashMap<String, CircuitBreaker> MAP = new ConcurrentHashMap<>();

    private static final int THRESHOLD = 3;      // consecutive failures to open
    private static final long RESET_MS = 10_000; // time before half-open
    private int failures = 0;
    private long openedAt = 0;

    private CircuitBreaker() {}

    /** Returns true if currently open (blocking requests). */
    public synchronized boolean isOpen() {
        if (failures >= THRESHOLD && (System.currentTimeMillis() - openedAt) < RESET_MS)
            return true;
        if ((System.currentTimeMillis() - openedAt) >= RESET_MS)
            failures = 0; // reset after cool-down
        return false;
    }

    /** Record a failure. May open the circuit. */
    public synchronized void onFailure() {
        failures++;
        if (failures >= THRESHOLD) openedAt = System.currentTimeMillis();
    }

    /** Record a success. Resets breaker. */
    public synchronized void onSuccess() {
        failures = 0;
    }

    /** Global registry by key (service or host). */
    public static CircuitBreaker forKey(String key) {
        return MAP.computeIfAbsent(key, k -> new CircuitBreaker());
    }
}
