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

    private boolean locked;

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

    /** Creates an empty request; caller must fill fields before locking. */
    public TaskRequest() {}

    /** Returns the task display name. */
    public String getName() { return name; }
    /** Returns the scheduling type. */
    public ScheduledTask.Type getType() { return type; }
    /** Returns the priority (lower means higher priority). */
    public int getPriority() { return priority; }
    /** Returns the command string executed by the scheduler. */
    public String getCommand() { return command; }
    /** Returns remaining retry attempts after the first failure. */
    public int getRetries() { return retries; }
    /** Returns the absolute execution timestamp (ms), if applicable. */
    public Long getExecuteAt() { return executeAt; }
    /** Returns the interval length (ms), if applicable. */
    public Long getIntervalMs() { return intervalMs; }
    /** Returns the uptime delay (ms), if applicable. */
    public Long getDelayMs() { return delayMs; }

    /** Sets the task name. */
    public TaskRequest setName(String name) { ensureNotLocked(); this.name = name; return this; }
    /** Sets the task priority. */
    public TaskRequest setPriority(int priority) { ensureNotLocked(); this.priority = priority; return this; }
    /** Sets the command string. */
    public TaskRequest setCommand(String command) { ensureNotLocked(); this.command = command; return this; }
    /** Sets allowed retry attempts after the first failure. */
    public TaskRequest setRetries(int retries) { ensureNotLocked(); this.retries = retries; return this; }

    /**
     * Low-level type setter. Prefer the type-specific setters which set timing fields.
     */
    public TaskRequest setType(ScheduledTask.Type type) { ensureNotLocked(); this.type = type; return this; }

    /** Sets the absolute execution timestamp (ms). */
    public TaskRequest setExecuteAt(Long executeAt) { ensureNotLocked(); this.executeAt = executeAt; return this; }
    /** Sets the interval length (ms). */
    public TaskRequest setIntervalMs(Long intervalMs) { ensureNotLocked(); this.intervalMs = intervalMs; return this; }
    /** Sets the uptime delay (ms). */
    public TaskRequest setDelayMs(Long delayMs) { ensureNotLocked(); this.delayMs = delayMs; return this; }

    /** Configures a single absolute execution time. */
    public TaskRequest setAbsoluteOnce(long executeAt) {
        ensureNotLocked();
        this.type = ScheduledTask.Type.ABSOLUTE_ONESHOT;
        this.executeAt = executeAt;
        this.intervalMs = null;
        this.delayMs = null;
        return this;
    }

    /** Configures a wall-clock anchored interval schedule. */
    public TaskRequest setAbsoluteInterval(long executeAt, long intervalMs) {
        ensureNotLocked();
        this.type = ScheduledTask.Type.ABSOLUTE_INTERVAL;
        this.executeAt = executeAt;
        this.intervalMs = intervalMs;
        this.delayMs = null;
        return this;
    }

    /** Configures a one-shot delay from scheduler boot. */
    public TaskRequest setUptimeDelay(long delayMs) {
        ensureNotLocked();
        this.type = ScheduledTask.Type.UPTIME_DELAY;
        this.delayMs = delayMs;
        this.executeAt = null;
        this.intervalMs = null;
        return this;
    }

    /** Configures a boot-anchored interval with optional initial delay. */
    public TaskRequest setUptimeInterval(Long delayMsOrNull, long intervalMs) {
        ensureNotLocked();
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
        if (locked) {
            return this;
        }
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
        locked = true;
        return this;
    }

    /** Returns whether this request has been locked/validated. */
    public boolean isLocked() {
        return locked;
    }

    /** Enforces immutability after lock(). */
    private void ensureNotLocked() {
        if (locked) {
            throw new IllegalStateException("TaskRequest is locked.");
        }
    }
}
