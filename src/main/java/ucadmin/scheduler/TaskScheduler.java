package ucadmin.scheduler;

import ucadmin.exceptions.TaskException;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TaskScheduler = single-threaded scheduler engine (single worker).
 *
 * Responsibilities:
 * 1) bootAt anchor for uptime tasks (set once at init).
 * 2) load registry from TaskRegistry, build in-memory index.
 * 3) compute nextDueAt per task rules.
 * 4) pick due tasks by (nextDueAt, priority, createdAt/taskId).
 * 5) execute via CommandExecutor adapter.
 * 6) update registry + reindex tasks.
 *
 * INTENTIONAL POLICY (your design):
 * - NO "run late" (except optional tiny graceMs to avoid jitter misses).
 * - NO catch-up runs.
 * - If bot is down, ABSOLUTE_ONESHOT tasks are MISSED, not executed on startup.
 * - Interval tasks skip forward to next future run.
 * - Uptime tasks reset on restart automatically (bootAt changes).
 * - Mid-run crash means it didn't finish; scheduler does not try to "make up" for it.
 *
 * Stale-index tolerance:
 * - We don't remove old heap entries. We re-check the current task state on pop.
 * - This avoids complex heap deletion logic.
 *
 * Concurrency model:
 * - External threads enqueue commands (schedule/pause/resume/cancel).
 * - Only the worker thread mutates registry and queue state.
 */
public final class TaskScheduler implements Runnable {

    /** Adapter to run command strings (tie into your existing command system). */
    public interface CommandExecutor {
        /**
         * Execute. Return short result message. Throw on failure.
         * Scheduler decides whether to mark ERROR or keep task scheduled.
         */
        String execute(String command) throws Exception;
    }

    private final TaskRegistry taskRegistry;
    private final CommandExecutor executor;

    /** Anchor for uptime scheduling. */
    private final long bootAt;

    /** Lateness tolerance. Set 0 for strict. (Recommended small value: 250-2000ms). */
    private final long graceMs;

    /** Single worker loop flag. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Wake mechanism so schedule() can interrupt sleeps when earlier task is added. */
    private final Object wakeLock = new Object();

    /** Registry cache (optional). */
    private final Map<String, ScheduledTask> registry = new HashMap<>();

    /** Command queue (QueueManager-style): other threads enqueue, worker thread mutates state. */
    private final Queue<Command> commandQueue = new ConcurrentLinkedQueue<>();

    /** Priority queue ordered by nextDueAt, then priority, then createdAt, then taskId. */
    private final PriorityQueue<QueueEntry> queue = new PriorityQueue<>(QueueEntry.ORDER);

    public TaskScheduler(TaskRegistry taskRegistry, CommandExecutor executor, long graceMs) {
        this.taskRegistry = taskRegistry;
        this.executor = executor;
        this.graceMs = Math.max(0, graceMs);
        this.bootAt = System.currentTimeMillis();
    }

    /**
     * Init flow:
     * - Load tasks from registry.
     * - Cache them.
     * - Compute and enqueue their nextDueAt.
     * - Immediately mark/delete missed one-shots (ABSOLUTE_ONESHOT and UPTIME_DELAY).
     *
     * Call before starting the worker thread.
     */
    public void init() {
        long now = System.currentTimeMillis();
        for (ScheduledTask t : taskRegistry.loadAllActive()) {
            registry.put(t.taskId, t);

            if (t.status != ScheduledTask.Status.SCHEDULED) continue;

            Long due = computeNextDueAt(t, now);
            if (due == null) continue;

            if (isOneShot(t.type) && isMissed(due, now)) {
                handleMissedOneShot(t, now);
                continue;
            }
            enqueue(t, due);
        }
    }

    /**
     * Public scheduling API:
     * - Caller builds a TaskRequest with desired type/fields.
     * - Scheduler assigns id externally OR caller passes it; pick one approach and standardize it.
     *
     * This outline assigns ID here to keep callers dumb.
     */
    public String schedule(TaskRequest request) throws TaskException {
        if (request == null) {
            throw new TaskException("TaskScheduler.schedule: request is null.");
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
                .command(request.getCommand())
                .executeAt(request.getExecuteAt())
                .intervalMs(request.getIntervalMs())
                .delayMs(request.getDelayMs())
                .retriesRemaining(Math.max(0, request.getRetries()))
                .createdAt(now)
                .updatedAt(now)
                .runCount(0)
                .build();

        enqueueCommand(Command.schedule(task));
        return id;
    }

    public void pause(String taskId) {
        enqueueCommand(Command.pause(taskId));
    }

    public void resume(String taskId) {
        enqueueCommand(Command.resume(taskId));
    }

    public void cancel(String taskId) {
        enqueueCommand(Command.cancel(taskId));
    }

    private void enqueueCommand(Command command) {
        commandQueue.add(command);
        wake();
    }

    private void setStatus(String taskId, ScheduledTask.Status status) {
        ScheduledTask t;
        t = registry.get(taskId);
        if (t == null) return;
        long now = System.currentTimeMillis();
        ScheduledTask updated = t.toBuilder().status(status).updatedAt(now).build();
        taskRegistry.update(updated);
        registry.put(taskId, updated);
    }

    private void drainCommands(long now) {
        Command cmd;
        while ((cmd = commandQueue.poll()) != null) {
            switch (cmd.type) {
                case SCHEDULE: {
                    ScheduledTask t = cmd.task;
                    taskRegistry.put(t);
                    registry.put(t.taskId, t);

                    Long due = computeNextDueAt(t, now);
                    if (due != null && !(isOneShot(t.type) && isMissed(due, now))) {
                        enqueue(t, due);
                    } else if (due != null && isOneShot(t.type) && isMissed(due, now)) {
                        handleMissedOneShot(t, now);
                    }
                    break;
                }
                case PAUSE:
                    setStatus(cmd.taskId, ScheduledTask.Status.PAUSED);
                    break;
                case RESUME: {
                    setStatus(cmd.taskId, ScheduledTask.Status.SCHEDULED);
                    ScheduledTask t = registry.get(cmd.taskId);
                    if (t != null) {
                        Long due = computeNextDueAt(t, now);
                        if (due != null && !(isOneShot(t.type) && isMissed(due, now))) {
                            enqueue(t, due);
                        } else if (due != null && isOneShot(t.type) && isMissed(due, now)) {
                            handleMissedOneShot(t, now);
                        }
                    }
                    break;
                }
                case CANCEL:
                    setStatus(cmd.taskId, ScheduledTask.Status.CANCELLED);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) return;

        while (running.get()) {
            long now = System.currentTimeMillis();
            drainCommands(now);
            now = System.currentTimeMillis();

            QueueEntry head = queue.peek();
            if (head == null) {
                sleepOrWake(60_000);
                continue;
            }

            long sleepMs = Math.max(0, head.nextDueAt - now);
            if (sleepMs > 0) {
                sleepOrWake(Math.min(sleepMs, 60_000));
                continue;
            }

            // Due or overdue: pop one, re-check current truth from registry (stale entries are ignored).
            QueueEntry entry = queue.poll();
            ScheduledTask t = entry == null ? null : registry.get(entry.taskId);
            if (entry == null) continue;
            if (t == null) continue;
            if (t.status != ScheduledTask.Status.SCHEDULED) continue;

            Long due = computeNextDueAt(t, now);
            if (due == null) continue;

            // Stale heap entry: if due changed, re-enqueue correct due and skip.
            if (due.longValue() != entry.nextDueAt) {
                enqueue(t, due);
                continue;
            }

            // Strict miss behavior for one-shots:
            if (isOneShot(t.type) && isMissed(due, now)) {
                handleMissedOneShot(t, now);
                continue;
            }

            // Execute one task at a time.
            execute(t, now);
        }
    }

    public void shutdown() {
        running.set(false);
        wake();
    }

    // ======================
    // RULES: due-time math
    // ======================

    /**
     * Compute next due time for this task, based on TaskType rules.
     * Returns a unix ms timestamp.
     *
     * ABSOLUTE_INTERVAL / UPTIME_INTERVAL:
     * - skip-forward math ensures no catch-up runs.
     */
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

    private boolean isMissed(long dueAt, long now) {
        return now > (dueAt + graceMs);
    }

    private boolean isOneShot(ScheduledTask.Type type) {
        return type == ScheduledTask.Type.ABSOLUTE_ONESHOT || type == ScheduledTask.Type.UPTIME_DELAY;
    }

    // ======================
    // RULES: execution + post-run updates
    // ======================

    /**
     * Execution policy:
     * - Single thread.
     * - No RUNNING state required (your current stance). Mid-run crash = lost attempt.
     * - On success:
     *   - one-shot: DONE or delete
     *   - interval: stay SCHEDULED, re-enqueue next due
     * - On failure: if retriesRemaining > 0, decrement and retry; else mark ERROR and keep in registry
     */
    private void execute(ScheduledTask t, long now) {
        String result;
        try {
            result = executor.execute(t.command);
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
                taskRegistry.update(retrying);
                registry.put(t.taskId, retrying);
                enqueue(retrying, now + 1);
                return;
            }

            ScheduledTask failed = updated.toBuilder()
                    .status(ScheduledTask.Status.ERROR)
                    .build();
            taskRegistry.update(failed);
            registry.put(failed.taskId, failed);
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

            taskRegistry.delete(updated.taskId);
            registry.remove(updated.taskId);
            return;
        }

        // Interval: update observability fields, keep scheduled, re-enqueue computed next due.
        ScheduledTask updated = t.toBuilder()
                .status(ScheduledTask.Status.SCHEDULED)
                .lastRunAt(now)
                .lastResult(result)
                .runCount(t.runCount + 1)
                .updatedAt(now)
                .build();
        taskRegistry.update(updated);
        registry.put(t.taskId, updated);

        Long next = computeNextDueAt(updated, now);
        if (next != null) enqueue(updated, next);
    }

    private void handleMissedOneShot(ScheduledTask t, long now) {
        ScheduledTask updated = t.toBuilder()
                .status(ScheduledTask.Status.MISSED)
                .updatedAt(now)
                .lastResult("MISSED: dueAt passed while bot was down or scheduler started late")
                .build();

        taskRegistry.delete(updated.taskId);
        registry.remove(updated.taskId);
    }

    // ======================
    // Indexing + wake helpers
    // ======================

    private void enqueue(ScheduledTask t, long dueAt) {
        queue.add(new QueueEntry(t.taskId, dueAt, t.priority, t.createdAt));
    }

    private void wake() {
        synchronized (wakeLock) { wakeLock.notifyAll(); }
    }

    private void sleepOrWake(long maxMs) {
        synchronized (wakeLock) {
            try { wakeLock.wait(maxMs); } catch (InterruptedException ignored) {}
        }
    }

    private static String safeMsg(String s) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", " ");
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    private static long nextIntervalAfter(long first, long intervalMs, long now) {
        if (now < first) return first;
        long elapsed = now - first;
        long steps = (elapsed / intervalMs) + 1;
        return first + (steps * intervalMs);
    }

    private static final class Command {
        enum Type { SCHEDULE, PAUSE, RESUME, CANCEL }

        final Type type;
        final ScheduledTask task;
        final String taskId;

        private Command(Type type, ScheduledTask task, String taskId) {
            this.type = type;
            this.task = task;
            this.taskId = taskId;
        }

        static Command schedule(ScheduledTask task) {
            return new Command(Type.SCHEDULE, task, null);
        }

        static Command pause(String taskId) {
            return new Command(Type.PAUSE, null, taskId);
        }

        static Command resume(String taskId) {
            return new Command(Type.RESUME, null, taskId);
        }

        static Command cancel(String taskId) {
            return new Command(Type.CANCEL, null, taskId);
        }
    }

    private static final class QueueEntry {
        final String taskId;
        final long nextDueAt;
        final int priority;
        final long createdAt;

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
