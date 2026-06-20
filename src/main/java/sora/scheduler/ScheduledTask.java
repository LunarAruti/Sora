package sora.scheduler;

import java.util.Objects;


public final class ScheduledTask {

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

    /** Whitelisted op key to execute. */
    public final String opKey;
    /** Op arguments string (comma-separated args). */
    public final String opArgs;

    /** Remaining retries before terminal ERROR (0 = no retries). */
    public final int retriesRemaining;

    /** Unix ms. Used by ABSOLUTE_* only. */
    public final Long executeAt;

    /** Interval length in ms. Used by *_INTERVAL only. */
    public final Long intervalMs;

    /** Delay from scheduler boot (ms). Used by UPTIME_* types. */
    public final Long delayMs;

    public final long createdAt;
    public final long updatedAt;

    public final Long lastRunAt;
    public final String lastResult;
    public final long runCount;

    /** Internal constructor used by the builder. */
    private ScheduledTask(Builder b) {
        this.taskId = Objects.requireNonNull(b.taskId, "taskId");
        this.name = Objects.requireNonNull(b.name, "name");
        this.type = Objects.requireNonNull(b.type, "type");
        this.status = Objects.requireNonNull(b.status, "status");
        this.priority = b.priority;
        this.opKey = Objects.requireNonNull(b.opKey, "opKey");
        this.opArgs = b.opArgs;
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
        require(!opKey.isBlank(), "opKey cannot be blank");
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

    /** Throws if validation fails. */
    private static void require(boolean ok, String msg) {
        if (!ok) throw new IllegalArgumentException(msg);
    }

    /** Creates a builder prefilled with this task's values. */
    public Builder toBuilder() {
        return new Builder()
                .taskId(taskId)
                .name(name)
                .type(type)
                .status(status)
                .priority(priority)
                .opKey(opKey)
                .opArgs(opArgs)
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

    public static final class Builder {
        private String taskId;
        private String name;
        private Type type;
        private Status status = Status.SCHEDULED;
        private int priority = 10;
        private String opKey;
        private String opArgs;
        private int retriesRemaining;

        private Long executeAt;
        private Long intervalMs;
        private Long delayMs;

        private long createdAt;
        private long updatedAt;

        private Long lastRunAt;
        private String lastResult;
        private long runCount;

        /** Sets the task id. */
        public Builder taskId(String v) { this.taskId = v; return this; }
        /** Sets the display name. */
        public Builder name(String v) { this.name = v; return this; }
        /** Sets the scheduling type. */
        public Builder type(Type v) { this.type = v; return this; }
        /** Sets the task status. */
        public Builder status(Status v) { this.status = v; return this; }
        /** Sets the priority. */
        public Builder priority(int v) { this.priority = v; return this; }
        /** Sets the whitelisted op key. */
        public Builder opKey(String v) { this.opKey = v; return this; }
        /** Sets the op args string. */
        public Builder opArgs(String v) { this.opArgs = v; return this; }
        /** Sets remaining retries. */
        public Builder retriesRemaining(int v) { this.retriesRemaining = v; return this; }

        /** Sets the absolute executeAt time. */
        public Builder executeAt(Long v) { this.executeAt = v; return this; }
        /** Sets the interval length. */
        public Builder intervalMs(Long v) { this.intervalMs = v; return this; }
        /** Sets the uptime delay. */
        public Builder delayMs(Long v) { this.delayMs = v; return this; }

        /** Sets created timestamp. */
        public Builder createdAt(long v) { this.createdAt = v; return this; }
        /** Sets updated timestamp. */
        public Builder updatedAt(long v) { this.updatedAt = v; return this; }

        /** Sets last run timestamp. */
        public Builder lastRunAt(Long v) { this.lastRunAt = v; return this; }
        /** Sets last result string. */
        public Builder lastResult(String v) { this.lastResult = v; return this; }
        /** Sets run count. */
        public Builder runCount(long v) { this.runCount = v; return this; }

        /** Builds an immutable ScheduledTask. */
        public ScheduledTask build() { return new ScheduledTask(this); }
    }
}
