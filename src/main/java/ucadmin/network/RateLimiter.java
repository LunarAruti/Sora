package ucadmin.network;

import ucadmin.exceptions.NetworkException;
import static ucadmin.exceptions.NetworkException.*;

import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
    private static final ConcurrentHashMap<String, RateLimiter> BUCKETS = new ConcurrentHashMap<>();

    private final double rate;       // tokens per second
    private double tokens;
    private long lastRefill;

    private RateLimiter(double rate) {
        this.rate = rate;
        this.tokens = rate;
        this.lastRefill = System.nanoTime();
    }

    /** Acquire a token or throw if depleted. */
    public synchronized void acquire() throws RateLimitException {
        refill();
        if (tokens < 1) {
            throw new RateLimitException("LOCAL_RATE_LIMIT: bucket depleted",
                    "<local>", "<rate>", "<none>", "<none>", "LOCAL", null);
        }
        tokens--;
    }

    private void refill() {
        long now = System.nanoTime();
        double delta = (now - lastRefill) / 1_000_000_000.0;
        tokens = Math.min(rate, tokens + delta * rate);
        lastRefill = now;
    }

    public static RateLimiter forBucket(String key) {
        // Default: 10 requests/sec per bucket
        return BUCKETS.computeIfAbsent(key, k -> new RateLimiter(10));
    }
}
