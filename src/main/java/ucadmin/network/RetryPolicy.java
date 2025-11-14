package ucadmin.network;

import ucadmin.exceptions.NetworkException;
import static ucadmin.exceptions.NetworkException.*;

public final class RetryPolicy {
    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;

    private RetryPolicy(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    public static RetryPolicy of(NetworkRequest req) {
        // Future: customize per service
        return new RetryPolicy(5, 200, 3000);
    }

    /** Should another retry be attempted? */
    public boolean shouldRetry(NetworkException e, int attempt) {
        if (attempt >= maxAttempts) return false;
        return e.retryable;
    }

    /** Sleep with exponential backoff + jitter. */
    public void backoff(int attempt) {
        long delay = Math.min(baseDelayMs * (1L << (attempt - 1)), maxDelayMs);
        long jitter = (long) (Math.random() * 150);
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException ignored) {}
    }
}
