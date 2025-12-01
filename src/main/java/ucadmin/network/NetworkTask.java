package ucadmin.network;

import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import ucadmin.util.Logger;

/**
 * Internal unit of work for the network worker.
 *
 * <p>This wraps a sealed {@link NetworkRequest} together with scheduling
 * metadata (attempt index, scheduled time) and provides priority and delay
 * semantics suitable for use with a {@link java.util.concurrent.DelayQueue}.</p>
 *
 * <p>Key points:
 * <ul>
 *     <li>Tasks are ordered primarily by their scheduled execution time.</li>
 *     <li>For tasks scheduled at the same time, priority is used:
 *         HIGH &gt; NORMAL &gt; LOW &gt; BACKGROUND.</li>
 *     <li>A monotonically increasing sequence number preserves FIFO order
 *         among otherwise equal tasks.</li>
 * </ul>
 * </p>
 *
 * <p>Callers should not construct this directly; it is meant to be created
 * by {@code NetworkManager} / the network layer when enqueuing work.</p>
 */
final class NetworkTask implements Delayed {

    private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong(0L);

    /** The sealed network request to execute. */
    private final NetworkRequest request;

    /**
     * 1-based attempt index for this task.
     * 1 = first attempt, 2 = first retry, etc.
     */
    private final int attemptIndex;

    /**
     * Wall-clock time (epoch millis) when this task is eligible to run.
     * For immediate execution, this is usually the enqueue time.
     */
    private final long scheduledAtMillis;

    /**
     * Creation time (epoch millis) for metrics/ordering; not used in delay.
     */
    private final long createdAtMillis;

    /**
     * Monotonic sequence number used to preserve FIFO ordering for tasks
     * with identical scheduled time and priority.
     */
    private final long sequence;

    NetworkTask(NetworkRequest request, int attemptIndex, long scheduledAtMillis) {
        this.request = Objects.requireNonNull(request, "request");
        if (!request.isSealed()) {
            throw new IllegalArgumentException("NetworkTask requires a sealed NetworkRequest.");
        }
        if (attemptIndex < 1) {
            throw new IllegalArgumentException("attemptIndex must be >= 1 (was " + attemptIndex + ")");
        }

        this.attemptIndex = attemptIndex;
        this.scheduledAtMillis = scheduledAtMillis;
        this.createdAtMillis = System.currentTimeMillis();
        this.sequence = SEQUENCE_GENERATOR.getAndIncrement();

        Logger.log(Logger.TAG.REQUEST,
                "NetworkTask: created service=" + request.getService() +
                        " name=" + request.getName() +
                        " attempt=" + attemptIndex +
                        " scheduledAt=" + scheduledAtMillis +
                        " priority=" + request.getPriority() +
                        " seq=" + this.sequence);
    }

    /**
     * Returns a new NetworkTask representing a retry attempt for the same request,
     * scheduled at the given future time.
     */
    NetworkTask newRetry(long nextScheduledAtMillis) {
        Logger.log(Logger.TAG.DEBUG,
                "NetworkTask: scheduling RETRY for service=" + request.getService() +
                        " name=" + request.getName() +
                        " prevAttempt=" + this.attemptIndex +
                        " nextAttempt=" + (this.attemptIndex + 1) +
                        " nextScheduledAt=" + nextScheduledAtMillis);

        return new NetworkTask(this.request, this.attemptIndex + 1, nextScheduledAtMillis);
    }

    public NetworkRequest getRequest() {
        return request;
    }

    public int getAttemptIndex() {
        return attemptIndex;
    }

    public long getScheduledAtMillis() {
        return scheduledAtMillis;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long now = System.currentTimeMillis();
        long diff = scheduledAtMillis - now;

        if (diff < 0) {
            Logger.log(Logger.TAG.DEBUG,
                    "NetworkTask: overdue task service=" + request.getService() +
                            " name=" + request.getName() +
                            " attempt=" + attemptIndex +
                            " overdueBy=" + (-diff) + "ms");
        }

        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    /**
     * Tasks are ordered by:
     * <ol>
     *     <li>scheduledAtMillis (earlier runs first)</li>
     *     <li>priority (HIGH before NORMAL before LOW before BACKGROUND)</li>
     *     <li>sequence (FIFO for otherwise equal tasks)</li>
     * </ol>
     */
    @Override
    public int compareTo(Delayed other) {
        if (other == this) return 0;
        if (!(other instanceof NetworkTask o)) {
            long d = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
            return (d < 0L) ? -1 : (d > 0L ? 1 : 0);
        }

        // 1) scheduled time
        int cmp = Long.compare(this.scheduledAtMillis, o.scheduledAtMillis);
        if (cmp != 0) return cmp;

        // 2) priority (lower weight = higher priority)
        int p1 = priorityWeight(this.request.getPriority());
        int p2 = priorityWeight(o.request.getPriority());
        cmp = Integer.compare(p1, p2);
        if (cmp != 0) return cmp;

        // 3) sequence (FIFO)
        return Long.compare(this.sequence, o.sequence);
    }

    private static int priorityWeight(NetworkRequest.Priority p) {
        // HIGH -> 0, NORMAL -> 1, LOW -> 2, BACKGROUND -> 3
        if (p == null) return 1; // default to NORMAL if somehow null
        return switch (p) {
            case HIGH -> 0;
            case NORMAL -> 1;
            case LOW -> 2;
            case BACKGROUND -> 3;
        };
    }

    @Override
    public String toString() {
        return "NetworkTask{" +
                "service=" + request.getService() +
                ", name=" + request.getName() +
                ", attemptIndex=" + attemptIndex +
                ", scheduledAtMillis=" + scheduledAtMillis +
                ", priority=" + request.getPriority() +
                '}';
    }
}
