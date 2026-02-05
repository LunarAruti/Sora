package ucadmin.scheduler;

import java.util.Objects;

/**
 * ScheduledTask = the single object that represents "a thing the scheduler will run".
 *
 * This is the only object the rest of the bot should need to understand.
 * - Users / modules create a TaskRequest (or build ScheduledTask via a builder).
 * - The scheduler assigns taskId, persists it, indexes it, and executes it when due.
 *
 * TIME MODELS (explicit; no null-magic):
 * 1) ABSOLUTE_ONESHOT:
 *    - Runs once at executeAt (unix ms).
 *    - If the bot is down and it misses the time, it is MISSED and never runs late.
 *
 * 2) ABSOLUTE_INTERVAL:
 *    - Repeats on a wall-clock schedule anchored to executeAt.
 *    - nextDueAt is computed by skipping forward: while(next <= now) next += intervalMs
 *    - No catch-up runs.
 *
 * 3) UPTIME_DELAY:
 *    - Runs once at (bootAt + delayMs).
 *    - Resets on restart. Never runs late if missed.
 *
 * 4) UPTIME_INTERVAL:
 *    - First run at (bootAt + (delayMs if set else intervalMs)), then repeats every intervalMs.
 *    - Skips forward while(next <= now) next += intervalMs
 *    - Resets on restart. No catch-up.
 *
 * PRIORITY:
 * - Only used when multiple tasks are due at the same time.
 * - Lower number = higher priority.
 *
 * STATUS:
 * - Minimal: SCHEDULED / PAUSED / CANCELLED
 * - Optional: DONE / MISSED / ERROR (useful if you keep history rather than deleting).
 */
public final class ScheduledTask {

    // ========= Enums live here to avoid "enum files spam" while still being readable. =========

    public enum Type {
        ABSOLUTE_ONESHOT,
        ABSOLUTE_INTERVAL,
        UPTIME_DELAY,
        UPTIME_INTERVAL
    }

    public enum Status {
        SCHEDULED,
        PAUSED,
        CANCELLED,

        // Optional terminal/diagnostic states:
        DONE,
        MISSED,
        ERROR
    }

    // ========= Required identity + behavior =========

    /** Assigned by scheduler. Immutable. */
    public final String taskId;

    /** Human-friendly name for the task. */
    public final String name;

    /** Defines timing rules and which timing fields must exist. */
    public final Type type;

    /** Minimal lifecycle control. */
    public final Status status;

    /** Lower is "more important" for tie-breaking when multiple tasks are due. */
    public final int priority;

    /** What to execute (usually routed into UC Admin command dispatcher). */
    public final String command;

    /** Remaining retries before terminal ERROR (0 = no retries). */
    public final int retriesRemaining;

    // ========= Timing fields (used depending on Type) =========

    /** Unix ms. Used by ABSOLUTE_* only. */
    public final Long executeAt;

    /** Interval length in ms. Used by *_INTERVAL only. */
    public final Long intervalMs;

    /** Delay from scheduler boot (ms). Used by UPTIME_* types. */
    public final Long delayMs;

    // ========= Metadata / observability =========

    public final long createdAt;
    public final long updatedAt;

    public final Long lastRunAt;
    public final String lastResult;
    public final long runCount;

    private ScheduledTask(Builder b) {
        this.taskId = Objects.requireNonNull(b.taskId, "taskId");
        this.name = Objects.requireNonNull(b.name, "name");
        this.type = Objects.requireNonNull(b.type, "type");
        this.status = Objects.requireNonNull(b.status, "status");
        this.priority = b.priority;
        this.command = Objects.requireNonNull(b.command, "command");
        this.retriesRemaining = b.retriesRemaining;

        this.executeAt = b.executeAt;
        this.intervalMs = b.intervalMs;
        this.delayMs = b.delayMs;

        this.createdAt = b.createdAt;
        this.updatedAt = b.updatedAt;

        this.lastRunAt = b.lastRunAt;
        this.lastResult = b.lastResult;
        this.runCount = b.runCount;

        validate();
    }

    /**
     * Validation is intentionally basic. Scheduler should still validate before persisting.
     * The point is: a reader can tell "what fields are required for which type".
     */
    private void validate() {
        require(!name.isBlank(), "name cannot be blank");
        switch (type) {
            case ABSOLUTE_ONESHOT:
                require(executeAt != null, "ABSOLUTE_ONESHOT requires executeAt");
                break;
            case ABSOLUTE_INTERVAL:
                require(executeAt != null, "ABSOLUTE_INTERVAL requires executeAt");
                require(intervalMs != null && intervalMs > 0, "ABSOLUTE_INTERVAL requires intervalMs > 0");
                break;
            case UPTIME_DELAY:
                require(delayMs != null && delayMs >= 0, "UPTIME_DELAY requires delayMs >= 0");
                break;
            case UPTIME_INTERVAL:
                require(intervalMs != null && intervalMs > 0, "UPTIME_INTERVAL requires intervalMs > 0");
                // delayMs is optional; if null, scheduler uses intervalMs as first delay.
                break;
            default:
                throw new IllegalStateException("Unknown type: " + type);
        }
        require(retriesRemaining >= 0, "retriesRemaining must be >= 0");
    }

    private static void require(boolean ok, String msg) {
        if (!ok) throw new IllegalArgumentException(msg);
    }

    public Builder toBuilder() {
        return new Builder()
                .taskId(taskId)
                .name(name)
                .type(type)
                .status(status)
                .priority(priority)
                .command(command)
                .retriesRemaining(retriesRemaining)
                .executeAt(executeAt)
                .intervalMs(intervalMs)
                .delayMs(delayMs)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .lastRunAt(lastRunAt)
                .lastResult(lastResult)
                .runCount(runCount);
    }

    // ========= Builder =========

    public static final class Builder {
        private String taskId;
        private String name;
        private Type type;
        private Status status = Status.SCHEDULED;
        private int priority = 10;
        private String command;
        private int retriesRemaining;

        private Long executeAt;
        private Long intervalMs;
        private Long delayMs;

        private long createdAt;
        private long updatedAt;

        private Long lastRunAt;
        private String lastResult;
        private long runCount;

        public Builder taskId(String v) { this.taskId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder type(Type v) { this.type = v; return this; }
        public Builder status(Status v) { this.status = v; return this; }
        public Builder priority(int v) { this.priority = v; return this; }
        public Builder command(String v) { this.command = v; return this; }
        public Builder retriesRemaining(int v) { this.retriesRemaining = v; return this; }

        public Builder executeAt(Long v) { this.executeAt = v; return this; }
        public Builder intervalMs(Long v) { this.intervalMs = v; return this; }
        public Builder delayMs(Long v) { this.delayMs = v; return this; }

        public Builder createdAt(long v) { this.createdAt = v; return this; }
        public Builder updatedAt(long v) { this.updatedAt = v; return this; }

        public Builder lastRunAt(Long v) { this.lastRunAt = v; return this; }
        public Builder lastResult(String v) { this.lastResult = v; return this; }
        public Builder runCount(long v) { this.runCount = v; return this; }

        public ScheduledTask build() { return new ScheduledTask(this); }
    }
}
