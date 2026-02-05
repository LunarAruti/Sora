package ucadmin.scheduler;

import ucadmin.exceptions.TaskException;
import ucadmin.util.Logger;

/**
 * TaskRequest = the "request object" callers build, similar to your NetworkRequest concept.
 *
 * Intent:
 * - Callers should not be forced to know internal storage/indexing details.
 * - Callers fill in fields; scheduler assigns taskId and persists it.
 *
 * This class is deliberately small:
 * - Caller sets fields via fluent setters and getters.
 * - Type setters also set timing fields (executeAt/interval/delay) to avoid mismatches.
 * - It can be made "sealable/immutable" later (like NetworkRequest) if you want.
 * - retries = number of extra attempts after the first failure (0 = no retries).
 *
 * USAGE PATTERNS:
 *
 * 1) Absolute one-shot: run at a wall-clock time (unix ms), or miss it.
 *    new TaskRequest().setName("x").setAbsoluteOnce(executeAt).setCommand("cmd")
 *
 * 2) Absolute interval: run on a wall-clock schedule anchored at executeAt, skip missed.
 *    new TaskRequest().setName("x").setAbsoluteInterval(executeAt, intervalMs).setCommand("cmd")
 *
 * 3) Uptime delay: run once after boot. Resets on restart.
 *    new TaskRequest().setName("x").setUptimeDelay(delayMs).setCommand("cmd")
 *
 * 4) Uptime interval: run repeatedly after boot. Resets on restart.
 *    new TaskRequest().setName("x").setUptimeInterval(delayMsOrNull, intervalMs).setCommand("cmd")
 */
public final class TaskRequest {
    private static final long MIN_INTERVAL_MS = 60L * 60L * 1000L; // 1 hour
    private static final int MAX_RETRIES = 5;

    /** Human-friendly name for the task. */
    private String name;
    /** Scheduling mode and required timing fields. */
    private ScheduledTask.Type type;
    /** Lower is higher priority (ties resolved by createdAt/taskId in scheduler). */
    private int priority = 10;
    /** Command string executed by scheduler's CommandExecutor. */
    private String command;
    /** Number of retry attempts after the first failure (0 = no retries). */
    private int retries = 0;

    private Long executeAt;
    private Long intervalMs;
    private Long delayMs;

    public TaskRequest() {}

    public String getName() { return name; }
    public ScheduledTask.Type getType() { return type; }
    public int getPriority() { return priority; }
    public String getCommand() { return command; }
    public int getRetries() { return retries; }
    public Long getExecuteAt() { return executeAt; }
    public Long getIntervalMs() { return intervalMs; }
    public Long getDelayMs() { return delayMs; }

    public TaskRequest setName(String name) { this.name = name; return this; }
    public TaskRequest setPriority(int priority) { this.priority = priority; return this; }
    public TaskRequest setCommand(String command) { this.command = command; return this; }
    public TaskRequest setRetries(int retries) { this.retries = retries; return this; }

    /**
     * Low-level type setter. Prefer the type-specific setters which set timing fields.
     */
    public TaskRequest setType(ScheduledTask.Type type) { this.type = type; return this; }

    public TaskRequest setExecuteAt(Long executeAt) { this.executeAt = executeAt; return this; }
    public TaskRequest setIntervalMs(Long intervalMs) { this.intervalMs = intervalMs; return this; }
    public TaskRequest setDelayMs(Long delayMs) { this.delayMs = delayMs; return this; }

    public TaskRequest setAbsoluteOnce(long executeAt) {
        this.type = ScheduledTask.Type.ABSOLUTE_ONESHOT;
        this.executeAt = executeAt;
        this.intervalMs = null;
        this.delayMs = null;
        return this;
    }

    public TaskRequest setAbsoluteInterval(long executeAt, long intervalMs) {
        this.type = ScheduledTask.Type.ABSOLUTE_INTERVAL;
        this.executeAt = executeAt;
        this.intervalMs = intervalMs;
        this.delayMs = null;
        return this;
    }

    public TaskRequest setUptimeDelay(long delayMs) {
        this.type = ScheduledTask.Type.UPTIME_DELAY;
        this.delayMs = delayMs;
        this.executeAt = null;
        this.intervalMs = null;
        return this;
    }

    public TaskRequest setUptimeInterval(Long delayMsOrNull, long intervalMs) {
        this.type = ScheduledTask.Type.UPTIME_INTERVAL;
        this.delayMs = delayMsOrNull;
        this.intervalMs = intervalMs;
        this.executeAt = null;
        return this;
    }

    /**
     * Validates and fills defaults. Must be called before scheduling.
     *
     * @return this TaskRequest after defaults are applied
     * @throws TaskException when the request is invalid
     */
    public TaskRequest lock() throws TaskException {
        if (name == null || name.isBlank()) {
            throw new TaskException("TaskRequest.lock: name is required.");
        }
        if (command == null || command.isBlank()) {
            throw new TaskException("TaskRequest.lock: command is required. name=" + name);
        }
        if (type == null) {
            throw new TaskException("TaskRequest.lock: type is required. name=" + name);
        }
        if (retries < 0 || retries > MAX_RETRIES) {
            throw new TaskException("TaskRequest.lock: retries must be 0-" + MAX_RETRIES + ". name=" + name);
        }

        long now = System.currentTimeMillis();

        switch (type) {
            case ABSOLUTE_ONESHOT:
                if (executeAt == null) {
                    throw new TaskException("TaskRequest.lock: executeAt required for one-shot. name=" + name);
                }
                if (executeAt < now) {
                    throw new TaskException("TaskRequest.lock: executeAt must be >= now. name=" + name);
                }
                intervalMs = null;
                delayMs = null;
                break;

            case ABSOLUTE_INTERVAL:
                if (executeAt == null) {
                    throw new TaskException("TaskRequest.lock: executeAt required for interval. name=" + name);
                }
                if (intervalMs == null || intervalMs < MIN_INTERVAL_MS) {
                    throw new TaskException("TaskRequest.lock: intervalMs must be >= " + MIN_INTERVAL_MS +
                            " ms. name=" + name);
                }
                delayMs = null;
                break;

            case UPTIME_DELAY:
                if (delayMs == null) delayMs = 0L;
                if (delayMs < 0) {
                    throw new TaskException("TaskRequest.lock: delayMs must be >= 0. name=" + name);
                }
                executeAt = null;
                intervalMs = null;
                break;

            case UPTIME_INTERVAL:
                if (intervalMs == null || intervalMs < MIN_INTERVAL_MS) {
                    throw new TaskException("TaskRequest.lock: intervalMs must be >= " + MIN_INTERVAL_MS +
                            " ms. name=" + name);
                }
                if (delayMs == null) delayMs = intervalMs;
                if (delayMs < 0) {
                    throw new TaskException("TaskRequest.lock: delayMs must be >= 0. name=" + name);
                }
                executeAt = null;
                break;

            default:
                throw new TaskException("TaskRequest.lock: unknown type. name=" + name);
        }

        Logger.log(Logger.TAG.DEBUG, "TaskRequest locked: name=" + name + " type=" + type);
        return this;
    }
}
