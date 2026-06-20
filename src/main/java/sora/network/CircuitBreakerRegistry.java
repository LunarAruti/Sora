package sora.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import sora.util.Logger;
import sora.config.ConfigManager;

/**
 * Simple in-memory circuit breaker registry keyed by circuitKey.
 *
 * <p>Each key tracks:</p>
 * <ul>
 *     <li>Current state (CLOSED, OPEN, HALF_OPEN)</li>
 *     <li>Consecutive failure count</li>
 *     <li>Timestamp when the circuit was opened</li>
 * </ul>
 *
 * <p>Behavior:</p>
 * <ul>
 *     <li>On repeated failures, the breaker opens and rejects new requests.</li>
 *     <li>After a cooldown period, allowRequest(...) begins to let probes through again.</li>
 *     <li>recordSuccess(...) closes the circuit and resets counters.</li>
 *     <li>recordFailure(...) increments counters and may open the circuit.</li>
 * </ul>
 */
public final class CircuitBreakerRegistry {

    public enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    /** Number of consecutive failures before opening the circuit. */
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /** How long the circuit remains OPEN before allowing probes again (ms). */
    private static final long DEFAULT_OPEN_DURATION_MS = 30_000L;

    private static final Map<String, BreakerState> BREAKERS = new ConcurrentHashMap<>();

    private CircuitBreakerRegistry() {
        // utility
    }

    /**
     * Returns whether a new request is currently allowed for the given circuit key.
     *
     * <p>This is a pure decision based on in-memory state and the current time.
     * The caller (e.g., the network worker) is responsible for throwing a
     * NetworkException with CIRCUIT_OPEN if it receives false here.</p>
     *
     * @param key       circuit key (must not be null/blank)
     * @param nowMillis current time in epoch millis
     * @return true if the request is allowed to proceed now, false if the circuit is still open
     */
    public static boolean allowRequest(String key, long nowMillis) {
        if (key == null || key.isBlank()) {
            return true;
        }

        BreakerState state = BREAKERS.computeIfAbsent(key, k -> new BreakerState());

        synchronized (state) {
            switch (state.state) {

                case CLOSED -> {
                    Logger.log(Logger.TAG.REQUEST,
                            "Circuit[CLOSED] allowRequest=true key=" + key);
                    return true;
                }

                case OPEN -> {
                    long elapsed = nowMillis - state.openedAtMillis;
                    long openDurationMs = openDurationMs();
                    if (elapsed >= openDurationMs) {
                        // OPEN → HALF_OPEN
                        state.state = CircuitState.HALF_OPEN;
                        state.consecutiveFailures = 0;
                        state.halfOpenProbeUsed = false; // reset probe flag

                        Logger.log(Logger.TAG.REQUEST,
                                "Circuit[OPEN→HALF_OPEN] allowRequest=true key=" + key);
                        return true;
                    }

                    Logger.log(Logger.TAG.REQUEST,
                            "Circuit[OPEN] allowRequest=false key=" + key +
                                    " remaining=" + (openDurationMs - elapsed));
                    return false;
                }

                case HALF_OPEN -> {
                    if (!state.halfOpenProbeUsed) {
                        // allow exactly one probe
                        state.halfOpenProbeUsed = true;
                        Logger.log(Logger.TAG.REQUEST,
                                "Circuit[HALF_OPEN] allowRequest=true (probe) key=" + key);
                        return true;
                    }

                    // deny all subsequent attempts until probe result is known
                    Logger.log(Logger.TAG.REQUEST,
                            "Circuit[HALF_OPEN] allowRequest=false (probe already used) key=" + key);
                    return false;
                }

                default -> {
                    return true; // should never reach here
                }
            }
        }
    }

    /**
     * Returns how many milliseconds remain in the OPEN state for the given key.
     * Returns 0 if the breaker is CLOSED or HALF_OPEN.
     */
    public static long getRemainingOpenMillis(String key, long nowMillis) {
        if (key == null || key.isBlank()) return 0L;
        BreakerState state = BREAKERS.get(key);
        if (state == null) return 0L;

        synchronized (state) {
            if (state.state != CircuitState.OPEN) return 0L;

            long elapsed = nowMillis - state.openedAtMillis;
            long remaining = Math.max(0L, openDurationMs() - elapsed);

            Logger.log(Logger.TAG.DEBUG,
                    "Circuit remaining OPEN: key=" + key + " remaining=" + remaining);

            return remaining;
        }
    }

    /**
     * Records a successful call for the given circuit key.
     * This closes the breaker and resets failure counters.
     *
     * @return true if the breaker state changed, false otherwise
     */
    public static boolean recordSuccess(String key) {
        if (key == null || key.isBlank()) return false;

        BreakerState state = BREAKERS.computeIfAbsent(key, k -> new BreakerState());
        boolean changed;
        synchronized (state) {
            changed = state.state != CircuitState.CLOSED || state.consecutiveFailures != 0 ||
                    state.openedAtMillis != 0L || state.lastFailureAtMillis != 0L || state.halfOpenProbeUsed;
            state.state = CircuitState.CLOSED;
            state.consecutiveFailures = 0;
            state.openedAtMillis = 0L;
            state.lastFailureAtMillis = 0L;
            state.halfOpenProbeUsed = false; // reset probe flag

            if (changed) {
                Logger.log(Logger.TAG.REQUEST,
                        "Circuit SUCCESS: key=" + key + " → CLOSED");
            }
        }
        return changed;
    }

    /**
     * Records a failed call for the given circuit key. When the number of
     * consecutive failures reaches the configured threshold, the circuit is opened
     * and new requests will be rejected until the open duration has elapsed.
     *
     * @param key       circuit key (must not be null/blank)
     * @param nowMillis current time in epoch millis
     * @return true if the breaker state changed, false otherwise
     */
    public static boolean recordFailure(String key, long nowMillis) {
        if (key == null || key.isBlank()) return false;

        BreakerState state = BREAKERS.computeIfAbsent(key, k -> new BreakerState());
        synchronized (state) {

            // Special behavior: in HALF_OPEN, ANY failure → go straight back to OPEN
            if (state.state == CircuitState.HALF_OPEN) {
                boolean changed = state.state != CircuitState.OPEN;
                state.state = CircuitState.OPEN;
                state.openedAtMillis = nowMillis;
                state.lastFailureAtMillis = nowMillis;
                state.consecutiveFailures = failureThreshold(); // treat failure as threshold breach
                state.halfOpenProbeUsed = false;

                Logger.log(Logger.TAG.REQUEST,
                        "Circuit FAILURE in HALF_OPEN → OPEN key=" + key);
                return changed;
            }

            // Normal CLOSED/OPEN failure logic
            state.consecutiveFailures++;
            state.lastFailureAtMillis = nowMillis;

            int threshold = failureThreshold();
            if (state.consecutiveFailures >= threshold) {
                boolean changed = state.state != CircuitState.OPEN;
                state.state = CircuitState.OPEN;
                state.openedAtMillis = nowMillis;
                state.halfOpenProbeUsed = false;

                Logger.log(Logger.TAG.REQUEST,
                        "Circuit FAILURE: key=" + key +
                                " threshold=" + threshold +
                                " → OPEN");
                return changed;
            } else {
                Logger.log(Logger.TAG.REQUEST,
                        "Circuit FAILURE: key=" + key +
                                " count=" + state.consecutiveFailures);
                return true;
            }
        }
    }

    /**
     * Returns the current state for the given circuit key, or CLOSED if unknown.
     */
    public static CircuitState getState(String key) {
        if (key == null || key.isBlank()) return CircuitState.CLOSED;
        BreakerState state = BREAKERS.get(key);
        if (state == null) return CircuitState.CLOSED;
        synchronized (state) {
            return state.state;
        }
    }

    /**
     * Immutable snapshot view of a breaker for diagnostics.
     */
    public static final class BreakerStateSnapshot {
        private final String key;
        private final CircuitState state;
        private final int consecutiveFailures;
        private final long openedAtMillis;
        private final long lastFailureAtMillis;
        private final long openUntilMillis;

        public BreakerStateSnapshot(
                String key,
                CircuitState state,
                int consecutiveFailures,
                long openedAtMillis,
                long lastFailureAtMillis,
                long openUntilMillis
        ) {
            this.key = key;
            this.state = state;
            this.consecutiveFailures = consecutiveFailures;
            this.openedAtMillis = openedAtMillis;
            this.lastFailureAtMillis = lastFailureAtMillis;
            this.openUntilMillis = openUntilMillis;
        }

        public String getKey() { return key; }
        public CircuitState getState() { return state; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public long getOpenedAtMillis() { return openedAtMillis; }
        public long getLastFailureAtMillis() { return lastFailureAtMillis; }
        public long getOpenUntilMillis() { return openUntilMillis; }

        @Override
        public String toString() {
            return "BreakerStateSnapshot{" +
                    "key='" + key + '\'' +
                    ", state=" + state +
                    ", consecutiveFailures=" + consecutiveFailures +
                    ", openedAtMillis=" + openedAtMillis +
                    ", lastFailureAtMillis=" + lastFailureAtMillis +
                    ", openUntilMillis=" + openUntilMillis +
                    '}';
        }
    }

    /**
     * Returns an immutable snapshot of all known circuit breakers.
     *
     * <p>Map key is the circuitKey, value is a {@link BreakerStateSnapshot}
     * containing current state, failure count, and timing info. Intended for
     * diagnostics, admin commands, or writing to a diagnostics DBM path.</p>
     */
    public static Map<String, BreakerStateSnapshot> snapshot() {
        Logger.log(Logger.TAG.DEBUG, "Circuit.snapshot(): generating snapshot");
        Map<String, BreakerStateSnapshot> out = new LinkedHashMap<>();
        for (Map.Entry<String, BreakerState> e : BREAKERS.entrySet()) {
            String key = e.getKey();
            BreakerState state = e.getValue();
            if (state == null) continue;

            synchronized (state) {
                long openUntil = 0L;
                if (state.state == CircuitState.OPEN && state.openedAtMillis > 0L) {
                    openUntil = state.openedAtMillis + openDurationMs();
                }

                BreakerStateSnapshot snap = new BreakerStateSnapshot(
                        key,
                        state.state,
                        state.consecutiveFailures,
                        state.openedAtMillis,
                        state.lastFailureAtMillis,
                        openUntil
                );
                out.put(key, snap);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Internal mutable breaker state guarded by its own monitor.
     */
    private static final class BreakerState {
        CircuitState state = CircuitState.CLOSED;
        int consecutiveFailures = 0;
        long openedAtMillis = 0L;
        long lastFailureAtMillis = 0L;

        // NEW FIELD: tracks whether the HALF_OPEN probe has been used
        boolean halfOpenProbeUsed = false;
    }

    /** Returns the current failure threshold for opening the circuit. */
    private static int failureThreshold() {
        return Math.max(1, ConfigManager.getInt("circuit_breaker.failure_threshold", DEFAULT_FAILURE_THRESHOLD));
    }

    /** Returns the current OPEN-state cooldown duration. */
    private static long openDurationMs() {
        return Math.max(0L, ConfigManager.getLong("circuit_breaker.open_duration_ms", DEFAULT_OPEN_DURATION_MS));
    }
}
