package sora.scheduler;

import sora.exceptions.TaskException;
import sora.util.Logger;
import sora.config.ConfigManager;

import java.util.Locale;

/**
 * Request object callers build before scheduling a task.
 *
 * Typical flow:
 * - Create TaskRequest.
 * - Set name, type/timing, opKey, opArgs, and optional priority/retries.
 * - Call TaskScheduler.scheduleRequest(request) to persist and enqueue.
 * - Use the returned taskId with TaskScheduler.pause/resume/cancel.
 *
 * opArgs format:
 * - Comma-separated list of arguments.
 * - Quotes preserve commas inside a single argument.
 *
 * lock() validates fields, applies defaults, normalizes opKey, and freezes the request.
 */
public final class TaskRequest {
    private static final long DEFAULT_MIN_INTERVAL_MS = 60L * 60L * 1000L; // 1 hour
    private static final long DEFAULT_ONE_SHOT_GRACE_MS = 30L * 60L * 1000L; // 30 minutes
    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final int DEFAULT_MIN_PRIORITY = 0;
    private static final int DEFAULT_MAX_PRIORITY = 100;
    private static final int DEFAULT_MAX_NAME_LEN = 128;

    private boolean locked;

    /** Human-friendly name for the task. */
    private String name;
    /** Scheduling mode and required timing fields. */
    private ScheduledTask.Type type;
    /** Lower is higher priority (ties resolved by createdAt/taskId in scheduler). */
    private int priority = 10;
    /** Whitelisted op key executed by scheduler's CommandExecutor. */
    private String opKey;
    /** Op arguments string (comma-separated args). */
    private String opArgs;
    /** Number of retry attempts after the first failure (0 = no retries). */
    private int retries = 0;

    private Long executeAt;
    private Long intervalMs;
    private Long delayMs;

    /**
     * Creates an empty request.
     * Call setters to populate fields before lock().
     */
    public TaskRequest() {}

    /**
     * Returns the task display name.
     *
     * @return task name or null if not set
     */
    public String getName() { return name; }

    /**
     * Returns the scheduling type.
     *
     * @return task type or null if not set
     */
    public ScheduledTask.Type getType() { return type; }

    /**
     * Returns the priority (lower means higher priority).
     *
     * @return priority value
     */
    public int getPriority() { return priority; }

    /**
     * Returns the whitelisted op key executed by the scheduler.
     *
     * @return normalized opKey or null if not set
     */
    public String getOpKey() { return opKey; }

    /**
     * Returns the op arguments string.
     *
     * @return opArgs string or null if not set
     */
    public String getOpArgs() { return opArgs; }

    /**
     * Returns remaining retry attempts after the first failure.
     *
     * @return retries count
     */
    public int getRetries() { return retries; }

    /**
     * Returns the absolute execution timestamp (ms), if applicable.
     *
     * @return executeAt ms or null if not set
     */
    public Long getExecuteAt() { return executeAt; }

    /**
     * Returns the interval length (ms), if applicable.
     *
     * @return interval length or null if not set
     */
    public Long getIntervalMs() { return intervalMs; }

    /**
     * Returns the uptime delay (ms), if applicable.
     *
     * @return delay length or null if not set
     */
    public Long getDelayMs() { return delayMs; }

    /**
     * Sets the display name for the task.
     * Required; max length enforced on lock().
     *
     * @param name task display name
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setName(String name) { ensureNotLocked(); this.name = name; return this; }

    /**
     * Sets task priority (lower = higher priority).
     * Valid range enforced on lock().
     *
     * @param priority priority value
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setPriority(int priority) { ensureNotLocked(); this.priority = priority; return this; }

    /**
     * Sets the whitelisted operation key.
     * opKey is normalized on lock().
     *
     * @param opKey whitelisted operation key enum
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setOpKey(ExeWhitelist.OpKey opKey) {
        ensureNotLocked();
        this.opKey = opKey == null ? null : opKey.name();
        return this;
    }

    /**
     * Sets the op arguments string (comma-separated list).
     * Use quotes to preserve commas inside a single argument.
     * Empty string is allowed; null is rejected on lock().
     *
     * @param opArgs op argument string
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setOpArgs(String opArgs) { ensureNotLocked(); this.opArgs = opArgs; return this; }

    /**
     * Sets retry attempts after the first failure.
     * Valid range enforced on lock().
     *
     * @param retries number of retries
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setRetries(int retries) { ensureNotLocked(); this.retries = retries; return this; }

    /**
     * Low-level type setter. Prefer type-specific setters that set timing fields.
     *
     * @param type scheduling type
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setType(ScheduledTask.Type type) { ensureNotLocked(); this.type = type; return this; }

    /**
     * Sets the absolute execution timestamp (ms).
     * Used by ABSOLUTE_* types.
     *
     * @param executeAt epoch ms
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setExecuteAt(Long executeAt) { ensureNotLocked(); this.executeAt = executeAt; return this; }

    /**
     * Sets the interval length (ms).
     * Used by *_INTERVAL types.
     *
     * @param intervalMs interval duration ms
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setIntervalMs(Long intervalMs) { ensureNotLocked(); this.intervalMs = intervalMs; return this; }

    /**
     * Sets the uptime delay (ms).
     * Used by UPTIME_* types.
     *
     * @param delayMs delay duration ms
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setDelayMs(Long delayMs) { ensureNotLocked(); this.delayMs = delayMs; return this; }

    /**
     * Configures a single absolute execution time.
     * Sets type=ABSOLUTE_ONESHOT and clears interval/delay fields.
     *
     * @param executeAt epoch ms
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setAbsoluteOnce(long executeAt) {
        ensureNotLocked();
        this.type = ScheduledTask.Type.ABSOLUTE_ONESHOT;
        this.executeAt = executeAt;
        this.intervalMs = null;
        this.delayMs = null;
        return this;
    }

    /**
     * Configures a wall-clock anchored interval schedule.
     * Sets type=ABSOLUTE_INTERVAL and clears delay field.
     *
     * @param executeAt epoch ms anchor
     * @param intervalMs interval duration ms
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setAbsoluteInterval(long executeAt, long intervalMs) {
        ensureNotLocked();
        this.type = ScheduledTask.Type.ABSOLUTE_INTERVAL;
        this.executeAt = executeAt;
        this.intervalMs = intervalMs;
        this.delayMs = null;
        return this;
    }

    /**
     * Configures a one-shot delay from scheduler boot.
     * Sets type=UPTIME_DELAY and clears executeAt/interval.
     *
     * @param delayMs delay duration ms
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setUptimeDelay(long delayMs) {
        ensureNotLocked();
        this.type = ScheduledTask.Type.UPTIME_DELAY;
        this.delayMs = delayMs;
        this.executeAt = null;
        this.intervalMs = null;
        return this;
    }

    /**
     * Configures a boot-anchored interval with optional initial delay.
     * Sets type=UPTIME_INTERVAL and clears executeAt.
     *
     * @param delayMsOrNull initial delay or null to default to interval
     * @param intervalMs interval duration ms
     * @return this request for chaining
     * @throws IllegalStateException if already locked
     */
    public TaskRequest setUptimeInterval(Long delayMsOrNull, long intervalMs) {
        ensureNotLocked();
        this.type = ScheduledTask.Type.UPTIME_INTERVAL;
        this.delayMs = delayMsOrNull;
        this.intervalMs = intervalMs;
        this.executeAt = null;
        return this;
    }

    /**
     * Validates and fills defaults, then locks the request.
     * Once locked, setters will throw IllegalStateException.
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
        if (name.length() > maxNameLen()) {
            throw new TaskException("TaskRequest.lock: name exceeds " + maxNameLen() + " chars.");
        }
        if (opKey == null || opKey.isBlank()) {
            throw new TaskException("TaskRequest.lock: opKey is required. name=" + name);
        }
        if (opArgs == null) {
            throw new TaskException("TaskRequest.lock: opArgs is required (use empty string for no args). name=" + name);
        }
        opKey = normalizeOpKey(opKey);
        this.opKey = opKey;
        if (ExeWhitelist.get(opKey) == null) {
            throw new TaskException("TaskRequest.lock: opKey not in whitelist. name=" + name + " opKey=" + opKey);
        }
        if (type == null) {
            throw new TaskException("TaskRequest.lock: type is required. name=" + name);
        }
        if (priority < minPriority() || priority > maxPriority()) {
            throw new TaskException("TaskRequest.lock: priority must be " +
                    minPriority() + "-" + maxPriority() + ". name=" + name);
        }
        if (retries < 0 || retries > maxRetries()) {
            throw new TaskException("TaskRequest.lock: retries must be 0-" + maxRetries() + ". name=" + name);
        }

        long now = System.currentTimeMillis();

        switch (type) {
            case ABSOLUTE_ONESHOT:
                if (executeAt == null) {
                    throw new TaskException("TaskRequest.lock: executeAt required for one-shot. name=" + name);
                }
                if (executeAt < (now - oneShotGraceMs())) {
                    throw new TaskException("TaskRequest.lock: executeAt is too far in the past. name=" + name);
                }
                intervalMs = null;
                delayMs = null;
                break;

            case ABSOLUTE_INTERVAL:
                if (executeAt == null) {
                    throw new TaskException("TaskRequest.lock: executeAt required for interval. name=" + name);
                }
                if (intervalMs == null || intervalMs < minIntervalMs()) {
                    throw new TaskException("TaskRequest.lock: intervalMs must be >= " + minIntervalMs() +
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
                if (intervalMs == null || intervalMs < minIntervalMs()) {
                    throw new TaskException("TaskRequest.lock: intervalMs must be >= " + minIntervalMs() +
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

        Logger.log(Logger.TAG.DEBUG,
                "TaskRequest locked: name=" + name +
                        " type=" + type +
                        " opKey=" + opKey);
        locked = true;
        return this;
    }

    /**
     * Returns whether this request has been locked/validated.
     *
     * @return true if locked
     */
    public boolean isLocked() {
        return locked;
    }

    /** Throws IllegalStateException if the request is already locked. */
    private void ensureNotLocked() {
        if (locked) {
            throw new IllegalStateException("TaskRequest is locked.");
        }
    }

    /** Normalizes opKey for case-insensitive input. */
    private static String normalizeOpKey(String opKey) {
        if (opKey == null) return null;
        String v = opKey.trim();
        return v.isEmpty() ? v : v.toUpperCase(Locale.ROOT);
    }

    /** Returns the current minimum interval validation bound. */
    private static long minIntervalMs() {
        return ConfigManager.getLong("task_request.min_interval_ms", DEFAULT_MIN_INTERVAL_MS);
    }

    /** Returns the current one-shot grace validation bound. */
    private static long oneShotGraceMs() {
        return ConfigManager.getLong("task_request.one_shot_grace_ms", DEFAULT_ONE_SHOT_GRACE_MS);
    }

    /** Returns the current max-retries validation bound. */
    private static int maxRetries() {
        return ConfigManager.getInt("task_request.max_retries", DEFAULT_MAX_RETRIES);
    }

    /** Returns the current minimum priority validation bound. */
    private static int minPriority() {
        return ConfigManager.getInt("task_request.min_priority", DEFAULT_MIN_PRIORITY);
    }

    /** Returns the current maximum priority validation bound. */
    private static int maxPriority() {
        return ConfigManager.getInt("task_request.max_priority", DEFAULT_MAX_PRIORITY);
    }

    /** Returns the current maximum task-name length validation bound. */
    private static int maxNameLen() {
        return ConfigManager.getInt("task_request.max_name_len", DEFAULT_MAX_NAME_LEN);
    }
}
