package ucadmin.scheduler;

import ucadmin.exceptions.TaskException;
import ucadmin.util.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;


public final class TaskScheduler implements Runnable {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static TaskScheduler INSTANCE;

    /**
     * Adapter to run whitelisted opKey/opArgs.
     * Implementations should throw on failure so the scheduler can retry or mark ERROR.
     */
    public interface CommandExecutor {
        /**
         * Executes the operation referenced by opKey/opArgs.
         *
         * @param opKey whitelisted operation key
         * @param opArgs comma-separated argument list (may be empty)
         * @return short result message for logging
         * @throws Exception when the operation fails (scheduler handles retries)
         */
        String execute(String opKey, String opArgs) throws Exception;
    }

    private final TaskRegistry taskRegistry;
    private final CommandExecutor executor;

    /** Anchor for uptime scheduling. */
    private final long bootAt;

    /** Global lateness tolerance for one-shots (30 minutes). */
    private static final long GRACE_MS = 30L * 60L * 1000L;

    /** Single worker loop flag. */
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();

    /** Max time to wait for a clean shutdown. */
    private static final long SHUTDOWN_AWAIT_MS = 15_000L;
    private static final long RETRY_BACKOFF_MS = 5_000L;
    private static final long LOOKAHEAD_MS = 2L * 60L * 60L * 1000L;
    private static final long REFRESH_INTERVAL_MS = 60L * 60L * 1000L;
    private static final int MAX_DUE_SOON_TASKS = 1000;

    /** Wake mechanism so scheduleRequest() can interrupt sleeps when earlier task is added. */
    private final Object wakeLock = new Object();

    /** Due-soon cache (registry file is master). */
    private final Map<String, ScheduledTask> hotCache = new HashMap<>();

    /** Command queue (QueueManager-style): other threads enqueue, worker thread mutates state. */
    private final Queue<Command> commandQueue = new ConcurrentLinkedQueue<>();

    /** Priority queue ordered by nextDueAt, then priority, then createdAt, then taskId. */
    private final PriorityQueue<QueueEntry> queue = new PriorityQueue<>(QueueEntry.ORDER);

    private long nextRefreshAt = 0L;

    /**
     * Starts the scheduler using the default file registry.
     *
     * @param executor executor used to run whitelisted ops
     * @return true if started, false if already started
     * @throws TaskException when executor is null or init fails
     */
    public static boolean start(CommandExecutor executor) throws TaskException {
        Logger.log(Logger.TAG.SYSTEM, "TaskScheduler.start() called.");
        if (!STARTED.compareAndSet(false, true)) {
            Logger.log(Logger.TAG.WARN, "TaskScheduler.start() ignored -> already started.");
            return false;
        }
        if (executor == null) {
            STARTED.set(false);
            throw new TaskException("TaskScheduler.start: executor is null.");
        }
        TaskRegistry registry = new FileTaskRegistry();
        TaskScheduler scheduler = new TaskScheduler(registry, executor);
        try {
            boolean ok = scheduler.start();
            INSTANCE = scheduler;
            Logger.log(Logger.TAG.INFO, "TaskScheduler start initiated=" + ok);
            return ok;
        } catch (TaskException e) {
            STARTED.set(false);
            throw e;
        }
    }

    /**
     * Shuts down the active scheduler instance, if any.
     *
     * @return true if shutdown initiated, false if not started
     */
    public static boolean shutdown() {
        Logger.log(Logger.TAG.SYSTEM, "TaskScheduler.shutdown() called.");
        if (!STARTED.compareAndSet(true, false)) {
            Logger.log(Logger.TAG.WARN, "TaskScheduler.shutdown() ignored -> not started.");
            return false;
        }
        if (INSTANCE == null) {
            Logger.log(Logger.TAG.WARN, "TaskScheduler.shutdown() skipped -> no instance.");
            return false;
        }
        boolean ok = INSTANCE.shutdown(true);
        INSTANCE = null;
        Logger.log(Logger.TAG.INFO, "TaskScheduler shutdown initiated=" + ok);
        return ok;
    }

    /**
     * Fast shutdown for no-exit sequences (no join).
     *
     * @return true if shutdown initiated, false if not started
     */
    public static boolean shutdownNoExit() {
        Logger.log(Logger.TAG.SYSTEM, "TaskScheduler.shutdownNoExit() called.");
        if (!STARTED.compareAndSet(true, false)) {
            Logger.log(Logger.TAG.WARN, "TaskScheduler.shutdownNoExit() ignored -> not started.");
            return false;
        }
        if (INSTANCE == null) {
            Logger.log(Logger.TAG.WARN, "TaskScheduler.shutdownNoExit() skipped -> no instance.");
            return false;
        }
        boolean ok = INSTANCE.shutdown(false);
        INSTANCE = null;
        Logger.log(Logger.TAG.INFO, "TaskScheduler shutdownNoExit initiated=" + ok);
        return ok;
    }

    /**
     * Front-end schedule entrypoint for TaskRequest.
     *
     * @param request task request to validate and schedule
     * @return generated taskId
     * @throws TaskException when scheduler not started or request invalid
     */
    public static String scheduleRequest(TaskRequest request) throws TaskException {
        if (INSTANCE == null) {
            throw new TaskException("TaskScheduler.scheduleRequest: scheduler not started.");
        }
        return INSTANCE.scheduleRequestInternal(request);
    }

    /**
     * Pauses a scheduled task by id.
     *
     * @param taskId id returned by scheduleRequest
     * @throws TaskException when scheduler not started or not running
     * @throws IllegalArgumentException when taskId is null/blank
     */
    public static void pause(String taskId) throws TaskException {
        if (INSTANCE == null) {
            throw new TaskException("TaskScheduler.pause: scheduler not started.");
        }
        INSTANCE.pauseInternal(taskId);
    }

    /**
     * Resumes a paused task by id.
     *
     * @param taskId id returned by scheduleRequest
     * @throws TaskException when scheduler not started or not running
     * @throws IllegalArgumentException when taskId is null/blank
     */
    public static void resume(String taskId) throws TaskException {
        if (INSTANCE == null) {
            throw new TaskException("TaskScheduler.resume: scheduler not started.");
        }
        INSTANCE.resumeInternal(taskId);
    }

    /**
     * Cancels a task by id (removes from registry).
     *
     * @param taskId id returned by scheduleRequest
     * @throws TaskException when scheduler not started or not running
     * @throws IllegalArgumentException when taskId is null/blank
     */
    public static void cancel(String taskId) throws TaskException {
        if (INSTANCE == null) {
            throw new TaskException("TaskScheduler.cancel: scheduler not started.");
        }
        INSTANCE.cancelInternal(taskId);
    }

    /** Creates a scheduler bound to a registry and an executor. */
    public TaskScheduler(TaskRegistry taskRegistry, CommandExecutor executor) {
        this.taskRegistry = taskRegistry;
        this.executor = executor;
        this.bootAt = System.currentTimeMillis();
    }

    /** Loads the registry and builds the initial due-soon cache. */
    private void initFromRegistry() throws TaskException {
        hotCache.clear();
        queue.clear();
        commandQueue.clear();

        long now = System.currentTimeMillis();
        refreshFromRegistry(now, "start", true);
    }

    /** Starts the worker thread (daemon). */
    public boolean start() throws TaskException {
        if (!running.compareAndSet(false, true)) {
            Logger.log(Logger.TAG.WARN, "TaskScheduler.start: ignored -> already running.");
            return false;
        }
        try {
            initFromRegistry();
        } catch (TaskException e) {
            running.set(false);
            Logger.log(Logger.TAG.ERROR, "TaskScheduler.start: init failed. " + e.getMessage());
            throw e;
        }
        Thread t = new Thread(this, "UC-TaskScheduler");
        t.setDaemon(true);
        workerThread.set(t);
        Logger.log(Logger.TAG.SYSTEM, "TaskScheduler: worker starting (" + t.getName() + ").");
        t.start();
        return true;
    }

    /** Returns true if the worker loop is running. */
    public boolean isRunning() {
        return running.get();
    }

    /** Returns a lightweight diagnostics snapshot for debugging/telemetry. */
    public Map<String, Object> getDiagnosticsSummary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("running", running.get());
        out.put("hotCacheSize", hotCache.size());
        out.put("queueSize", queue.size());
        out.put("pendingCommands", commandQueue.size());
        QueueEntry head = queue.peek();
        out.put("nextDueAt", head == null ? null : head.nextDueAt);
        out.put("nextRefreshAt", nextRefreshAt);
        Thread t = workerThread.get();
        out.put("workerName", t == null ? null : t.getName());
        return out;
    }

    /** Persists and schedules a task request. */
    private String scheduleRequestInternal(TaskRequest request) throws TaskException {
        if (request == null) {
            throw new TaskException("TaskScheduler.schedule: request is null.");
        }
        if (!running.get()) {
            throw new TaskException("TaskScheduler.schedule: scheduler is not running.");
        }
        if (!request.isLocked()) {
            request.lock();
        }
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();

        ScheduledTask task = new ScheduledTask.Builder()
                .taskId(id)
                .name(request.getName())
                .type(request.getType())
                .status(ScheduledTask.Status.SCHEDULED)
                .priority(request.getPriority())
                .opKey(request.getOpKey())
                .opArgs(request.getOpArgs())
                .executeAt(request.getExecuteAt())
                .intervalMs(request.getIntervalMs())
                .delayMs(request.getDelayMs())
                .retriesRemaining(Math.max(0, request.getRetries()))
                .createdAt(now)
                .updatedAt(now)
                .runCount(0)
                .build();

        Logger.log(Logger.TAG.DEBUG,
                "TaskScheduler.schedule: name=" + task.name +
                        " id=" + task.taskId +
                        " type=" + task.type +
                        " opKey=" + task.opKey);
        enqueueCommand(Command.createSchedule(task));
        return id;
    }

    /** Pauses a task by id. */
    private void pauseInternal(String taskId) throws TaskException {
        if (!running.get()) {
            throw new TaskException("TaskScheduler.pause: scheduler is not running.");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskScheduler.pause: taskId is null/blank.");
        }
        enqueueCommand(Command.pause(taskId));
    }

    /** Resumes a paused task by id. */
    private void resumeInternal(String taskId) throws TaskException {
        if (!running.get()) {
            throw new TaskException("TaskScheduler.resume: scheduler is not running.");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskScheduler.resume: taskId is null/blank.");
        }
        enqueueCommand(Command.resume(taskId));
    }

    /** Cancels a task by id (deletes from registry). */
    private void cancelInternal(String taskId) throws TaskException {
        if (!running.get()) {
            throw new TaskException("TaskScheduler.cancel: scheduler is not running.");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskScheduler.cancel: taskId is null/blank.");
        }
        enqueueCommand(Command.cancel(taskId));
    }

    /** Enqueues a command for the worker to process. */
    private void enqueueCommand(Command command) {
        commandQueue.add(command);
        Logger.log(Logger.TAG.DEBUG, "TaskScheduler: command queued -> " + command.type);
        wake();
    }

    /** Updates task status in the registry and returns the updated task. */
    private ScheduledTask setStatus(String taskId, ScheduledTask.Status status) {
        ScheduledTask t = loadTask(taskId, "setStatus");
        if (t == null) return null;
        long now = System.currentTimeMillis();
        ScheduledTask updated = t.toBuilder().status(status).updatedAt(now).build();
        try {
            taskRegistry.update(updated);
        } catch (TaskException e) {
            skipTask(updated, "registry update failed (status=" + status + ")", e);
            return null;
        }
        if (status != ScheduledTask.Status.SCHEDULED) {
            hotCache.remove(taskId);
        }
        return updated;
    }

    /** Loads a single task from the registry. */
    private ScheduledTask loadTask(String taskId, String context) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskScheduler." + context + ": taskId is null/blank.");
        }
        try {
            return taskRegistry.get(taskId);
        } catch (TaskException e) {
            Logger.log(Logger.TAG.ERROR,
                    "TaskScheduler: failed to load taskId=" + taskId + " context=" + context +
                            " err=" + e.getMessage());
            return null;
        }
    }

    /** Deletes a task from the registry and cache. */
    private void cancelTask(String taskId) {
        ScheduledTask t = loadTask(taskId, "cancel");
        hotCache.remove(taskId);
        if (t == null) {
            Logger.log(Logger.TAG.WARN, "TaskScheduler: cancel ignored (not found) taskId=" + taskId);
        }
        try {
            taskRegistry.delete(taskId);
        } catch (TaskException e) {
            Logger.log(Logger.TAG.ERROR,
                    "TaskScheduler: registry delete failed for taskId=" + taskId + " (cancel)");
        }
        Logger.log(Logger.TAG.INFO, "TaskScheduler: cancelled taskId=" + taskId);
    }

    /** Marks a bad task as ERROR and removes it from the hot cache. */
    private void skipTask(ScheduledTask task, String reason, TaskException cause) {
        if (task == null) return;
        hotCache.remove(task.taskId);
        Logger.log(Logger.TAG.ERROR,
                "TaskScheduler: skipping taskId=" + task.taskId + " reason=" + reason +
                        (cause == null ? "" : " err=" + cause.getMessage()));

        ScheduledTask failed = task.toBuilder()
                .status(ScheduledTask.Status.ERROR)
                .lastResult("ERROR: " + reason)
                .updatedAt(System.currentTimeMillis())
                .build();
        try {
            taskRegistry.update(failed);
        } catch (TaskException e) {
            Logger.log(Logger.TAG.ERROR,
                    "TaskScheduler: registry update failed for taskId=" + task.taskId + " (skip)");
        }
    }

    /** Drains the command queue and applies each command. */
    private void drainCommands(long now) {
        Command cmd;
        while ((cmd = commandQueue.poll()) != null) {
            switch (cmd.type) {
                case SCHEDULE: {
                    ScheduledTask t = cmd.task;
                    try {
                        taskRegistry.put(t);
                    } catch (TaskException e) {
                        skipTask(t, "registry put failed", e);
                        break;
                    }
                    Logger.log(Logger.TAG.INFO,
                            "TaskScheduler: scheduled name=" + t.name + " id=" + t.taskId + " type=" + t.type);
                    try {
                        refreshFromRegistry(now, "schedule", false);
                    } catch (TaskException e) {
                        Logger.log(Logger.TAG.ERROR,
                                "TaskScheduler: refresh failed after schedule err=" + e.getMessage());
                    }
                    break;
                }
                case PAUSE:
                    ScheduledTask paused = setStatus(cmd.taskId, ScheduledTask.Status.PAUSED);
                    if (paused == null) {
                        Logger.log(Logger.TAG.WARN, "TaskScheduler: pause ignored (not found) taskId=" + cmd.taskId);
                    } else {
                        Logger.log(Logger.TAG.INFO, "TaskScheduler: paused taskId=" + cmd.taskId);
                    }
                    break;
                case RESUME: {
                    ScheduledTask t = setStatus(cmd.taskId, ScheduledTask.Status.SCHEDULED);
                    if (t == null) {
                        Logger.log(Logger.TAG.WARN, "TaskScheduler: resume ignored (not found) taskId=" + cmd.taskId);
                    } else {
                        Logger.log(Logger.TAG.INFO, "TaskScheduler: resumed taskId=" + cmd.taskId);
                        Long due = computeNextDueAt(t, now);
                        if (due != null && !(isOneShot(t.type) && isMissed(due, now))) {
                            cacheIfDueSoon(t, due, now);
                        } else if (due != null && isOneShot(t.type) && isMissed(due, now)) {
                            handleMissedOneShot(t, now);
                        }
                    }
                    break;
                }
                case CANCEL:
                    cancelTask(cmd.taskId);
                    break;
                default:
                    break;
            }
        }
    }

    /** Rebuilds the due-soon cache by rereading the registry. */
    private void refreshFromRegistry(long now, String reason, boolean throwOnFailure) throws TaskException {
        List<ScheduledTask> tasks;
        try {
            tasks = taskRegistry.loadAllActive();
        } catch (TaskException e) {
            Logger.log(Logger.TAG.ERROR,
                    "TaskScheduler: refresh failed reason=" + reason + " err=" + e.getMessage());
            if (throwOnFailure) throw e;
            nextRefreshAt = now + 60_000L;
            return;
        }

        List<DueTask> dueSoon = new ArrayList<>();
        for (ScheduledTask t : tasks) {
            if (t.status != ScheduledTask.Status.SCHEDULED) continue;

            Long due = computeNextDueAt(t, now);
            if (due == null) continue;

            if (isOneShot(t.type) && isMissed(due, now)) {
                handleMissedOneShot(t, now);
                continue;
            }

            if (isDueSoon(due, now)) {
                dueSoon.add(new DueTask(t, due));
            }
        }

        dueSoon.sort(DueTask.ORDER);
        long localNextRefresh = now + REFRESH_INTERVAL_MS;
        if (dueSoon.size() > MAX_DUE_SOON_TASKS) {
            long firstDroppedDue = dueSoon.get(MAX_DUE_SOON_TASKS).dueAt;
            dueSoon = new ArrayList<>(dueSoon.subList(0, MAX_DUE_SOON_TASKS));
            long refreshEarlyAt = Math.max(now + 5_000L, firstDroppedDue - 30_000L);
            localNextRefresh = Math.min(localNextRefresh, refreshEarlyAt);
            Logger.log(Logger.TAG.WARN,
                    "TaskScheduler: due-soon cache capped at " + MAX_DUE_SOON_TASKS +
                            " (reason=" + reason + "), will refresh early.");
        }

        hotCache.clear();
        queue.clear();
        for (DueTask due : dueSoon) {
            hotCache.put(due.task.taskId, due.task);
            enqueue(due.task, due.dueAt);
        }
        nextRefreshAt = localNextRefresh;
        Logger.log(Logger.TAG.INFO,
                "TaskScheduler: refresh reason=" + reason + " total=" + tasks.size() +
                        " dueSoon=" + dueSoon.size() + " nextRefreshAt=" + nextRefreshAt);
    }

    /** Returns true if a task is within the lookahead window. */
    private boolean isDueSoon(long dueAt, long now) {
        return dueAt <= now + LOOKAHEAD_MS;
    }

    /** Adds or removes a task from the due-soon cache depending on its due time. */
    private void cacheIfDueSoon(ScheduledTask t, long dueAt, long now) {
        if (isDueSoon(dueAt, now)) {
            hotCache.put(t.taskId, t);
            enqueue(t, dueAt);
        } else {
            hotCache.remove(t.taskId);
        }
    }

    /** Worker loop: refresh, sleep, and execute due tasks. */
    @Override
    public void run() {
        Thread thread = Thread.currentThread();
        if (workerThread.get() != thread) {
            workerThread.set(thread);
        }
        Logger.log(Logger.TAG.SYSTEM, "TaskScheduler: worker started (" + thread.getName() + ").");

        while (running.get()) {
            long now = System.currentTimeMillis();
            drainCommands(now);
            now = System.currentTimeMillis();

            if (now >= nextRefreshAt) {
                try {
                    refreshFromRegistry(now, "periodic", false);
                } catch (TaskException e) {
                    Logger.log(Logger.TAG.ERROR,
                            "TaskScheduler: periodic refresh failed err=" + e.getMessage());
                }
                now = System.currentTimeMillis();
            }

            QueueEntry head = queue.peek();
            if (head == null) {
                long sleepMs = 60_000;
                long refreshIn = nextRefreshAt - now;
                if (refreshIn > 0) {
                    sleepMs = Math.min(sleepMs, refreshIn);
                }
                sleepOrWake(sleepMs);
                continue;
            }

            long sleepMs = Math.max(0, head.nextDueAt - now);
            long refreshIn = nextRefreshAt - now;
            if (refreshIn > 0) {
                sleepMs = Math.min(sleepMs, refreshIn);
            }
            if (sleepMs > 0) {
                sleepOrWake(Math.min(sleepMs, 60_000));
                continue;
            }

            QueueEntry entry = queue.poll();
            if (entry == null) continue;
            ScheduledTask latest = loadTask(entry.taskId, "run");
            if (latest == null) {
                hotCache.remove(entry.taskId);
                continue;
            }
            ScheduledTask t = latest;
            hotCache.put(t.taskId, t);
            if (t.status != ScheduledTask.Status.SCHEDULED) continue;

            Long due = computeNextDueAt(t, now);
            if (due == null) continue;

            if (due.longValue() != entry.nextDueAt) {
                cacheIfDueSoon(t, due, now);
                continue;
            }

            if (isOneShot(t.type) && isMissed(due, now)) {
                handleMissedOneShot(t, now);
                continue;
            }

            execute(t, now);
        }
        Logger.log(Logger.TAG.SYSTEM, "TaskScheduler: worker stopped.");
    }

    /** Requests shutdown and optionally waits for the worker to exit. */
    public boolean shutdown(boolean waitForJoin) {
        if (!running.compareAndSet(true, false)) {
            Logger.log(Logger.TAG.WARN, "TaskScheduler.shutdown: ignored -> not running.");
            return false;
        }
        Logger.log(Logger.TAG.SYSTEM, "TaskScheduler: shutdown requested.");
        wake();
        if (waitForJoin) {
            Thread t = workerThread.get();
            if (t != null) {
                try {
                    t.join(SHUTDOWN_AWAIT_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    Logger.log(Logger.TAG.WARN, "TaskScheduler: shutdown wait interrupted.");
                }
            }
        }
        return true;
    }

    /** Computes next due time based on the task type. */
    private Long computeNextDueAt(ScheduledTask t, long now) {
        switch (t.type) {
            case ABSOLUTE_ONESHOT:
                return t.executeAt;

            case ABSOLUTE_INTERVAL:
                return nextIntervalAfter(t.executeAt, t.intervalMs, now);

            case UPTIME_DELAY:
                return bootAt + t.delayMs;

            case UPTIME_INTERVAL: {
                long step = t.intervalMs;
                long first = bootAt + (t.delayMs != null ? t.delayMs : step);
                return nextIntervalAfter(first, step, now);
            }

            default:
                return null;
        }
    }

    /** Returns true if a one-shot task is beyond the grace window. */
    private boolean isMissed(long dueAt, long now) {
        return now > (dueAt + GRACE_MS);
    }

    /** Returns true if the task should only run once. */
    private boolean isOneShot(ScheduledTask.Type type) {
        return type == ScheduledTask.Type.ABSOLUTE_ONESHOT || type == ScheduledTask.Type.UPTIME_DELAY;
    }

    /** Executes a task and updates the registry based on outcome. */
    private void execute(ScheduledTask t, long now) {
        String result;
        try {
            Logger.log(Logger.TAG.INFO,
                    "TaskScheduler: executing name=" + t.name +
                            " id=" + t.taskId +
                            " opKey=" + t.opKey +
                            " attempt=" + (t.runCount + 1));
            result = executor.execute(t.opKey, t.opArgs);
        } catch (Exception e) {
            ScheduledTask updated = t.toBuilder()
                    .status(ScheduledTask.Status.SCHEDULED)
                    .lastRunAt(now)
                    .lastResult("ERROR: " + e.getClass().getSimpleName() + ": " + safeMsg(e.getMessage()))
                    .runCount(t.runCount + 1)
                    .updatedAt(now)
                    .build();

            if (t.retriesRemaining > 0) {
                ScheduledTask retrying = updated.toBuilder()
                        .retriesRemaining(t.retriesRemaining - 1)
                        .build();
                try {
                    taskRegistry.update(retrying);
                } catch (TaskException te) {
                    skipTask(retrying, "registry update failed (retry)", te);
                    return;
                }
                cacheIfDueSoon(retrying, now + RETRY_BACKOFF_MS, now);
                Logger.log(Logger.TAG.WARN,
                        "TaskScheduler: retrying name=" + t.name + " id=" + t.taskId +
                                " opKey=" + t.opKey +
                                " remaining=" + retrying.retriesRemaining +
                                " backoffMs=" + RETRY_BACKOFF_MS);
                return;
            }

            ScheduledTask failed = updated.toBuilder()
                    .status(ScheduledTask.Status.ERROR)
                    .build();
            try {
                taskRegistry.update(failed);
            } catch (TaskException te) {
                skipTask(failed, "registry update failed (error)", te);
                return;
            }
            hotCache.remove(failed.taskId);
            Logger.log(Logger.TAG.ERROR,
                    "TaskScheduler: failed name=" + t.name + " id=" + t.taskId +
                            " err=" + safeMsg(e.getMessage()));
            return;
        }

        if (isOneShot(t.type)) {
            ScheduledTask updated = t.toBuilder()
                    .status(ScheduledTask.Status.DONE)
                    .lastRunAt(now)
                    .lastResult(result)
                    .runCount(t.runCount + 1)
                    .updatedAt(now)
                    .build();

            try {
                taskRegistry.delete(updated.taskId);
            } catch (TaskException e) {
                Logger.log(Logger.TAG.ERROR,
                        "TaskScheduler: registry delete failed for taskId=" + updated.taskId);
            }
            hotCache.remove(updated.taskId);
            Logger.log(Logger.TAG.INFO,
                    "TaskScheduler: completed one-shot name=" + t.name +
                            " id=" + t.taskId +
                            " opKey=" + t.opKey);
            return;
        }

        ScheduledTask updated = t.toBuilder()
                .status(ScheduledTask.Status.SCHEDULED)
                .lastRunAt(now)
                .lastResult(result)
                .runCount(t.runCount + 1)
                .updatedAt(now)
                .build();
        try {
            taskRegistry.update(updated);
        } catch (TaskException e) {
            skipTask(updated, "registry update failed (interval)", e);
            return;
        }

        Long next = computeNextDueAt(updated, now);
        if (next != null) {
            cacheIfDueSoon(updated, next, now);
        } else {
            hotCache.remove(updated.taskId);
        }
        Logger.log(Logger.TAG.DEBUG,
                "TaskScheduler: completed interval name=" + t.name +
                        " id=" + t.taskId +
                        " opKey=" + t.opKey +
                        " nextDueAt=" + next);
    }

    /** Handles a missed one-shot by marking and deleting it. */
    private void handleMissedOneShot(ScheduledTask t, long now) {
        ScheduledTask updated = t.toBuilder()
                .status(ScheduledTask.Status.MISSED)
                .updatedAt(now)
                .lastResult("MISSED: dueAt passed while bot was down or scheduler started late")
                .build();

        try {
            taskRegistry.delete(updated.taskId);
        } catch (TaskException e) {
            Logger.log(Logger.TAG.ERROR,
                    "TaskScheduler: registry delete failed for taskId=" + updated.taskId + " (missed)");
        }
        hotCache.remove(updated.taskId);
        Logger.log(Logger.TAG.WARN,
                "TaskScheduler: missed one-shot name=" + t.name +
                        " id=" + t.taskId +
                        " opKey=" + t.opKey);
    }

    /** Adds a task to the priority queue. */
    private void enqueue(ScheduledTask t, long dueAt) {
        queue.add(new QueueEntry(t.taskId, dueAt, t.priority, t.createdAt));
    }

    /** Wakes the worker if it is sleeping. */
    private void wake() {
        synchronized (wakeLock) { wakeLock.notifyAll(); }
    }

    /** Sleeps up to maxMs, or wakes early if notified. */
    private void sleepOrWake(long maxMs) {
        synchronized (wakeLock) {
            try { wakeLock.wait(maxMs); } catch (InterruptedException ignored) {}
        }
    }

    /** Trims error strings for safe logging. */
    private static String safeMsg(String s) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", " ");
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    /** Computes the next interval occurrence after now. */
    private static long nextIntervalAfter(long first, long intervalMs, long now) {
        if (now < first) return first;
        long elapsed = now - first;
        long steps = (elapsed / intervalMs) + 1;
        return first + (steps * intervalMs);
    }

    public static final class Command {
        enum Type { SCHEDULE, PAUSE, RESUME, CANCEL }

        final Type type;
        final ScheduledTask task;
        final String taskId;

        /** Creates a command wrapper for the worker loop. */
        private Command(Type type, ScheduledTask task, String taskId) {
            this.type = type;
            this.task = task;
            this.taskId = taskId;
        }

        /** Creates a schedule command for a new task. */
        private static Command createSchedule(ScheduledTask task) {
            return new Command(Type.SCHEDULE, task, null);
        }

        /** Creates a pause command for a task id. */
        static Command pause(String taskId) {
            return new Command(Type.PAUSE, null, taskId);
        }

        /** Creates a resume command for a task id. */
        static Command resume(String taskId) {
            return new Command(Type.RESUME, null, taskId);
        }

        /** Creates a cancel command for a task id. */
        static Command cancel(String taskId) {
            return new Command(Type.CANCEL, null, taskId);
        }
    }

    private static final class DueTask {
        final ScheduledTask task;
        final long dueAt;

        /** Wraps a task with its computed due time. */
        DueTask(ScheduledTask task, long dueAt) {
            this.task = task;
            this.dueAt = dueAt;
        }

        static final Comparator<DueTask> ORDER = Comparator
                .comparingLong((DueTask d) -> d.dueAt)
                .thenComparingInt(d -> d.task.priority)
                .thenComparingLong(d -> d.task.createdAt)
                .thenComparing(d -> d.task.taskId);
    }

    private static final class QueueEntry {
        final String taskId;
        final long nextDueAt;
        final int priority;
        final long createdAt;

        /** Minimal queue entry used by the priority heap. */
        QueueEntry(String taskId, long nextDueAt, int priority, long createdAt) {
            this.taskId = taskId;
            this.nextDueAt = nextDueAt;
            this.priority = priority;
            this.createdAt = createdAt;
        }

        static final Comparator<QueueEntry> ORDER = Comparator
                .comparingLong((QueueEntry e) -> e.nextDueAt)
                .thenComparingInt(e -> e.priority)
                .thenComparingLong(e -> e.createdAt)
                .thenComparing(e -> e.taskId);
    }
}
