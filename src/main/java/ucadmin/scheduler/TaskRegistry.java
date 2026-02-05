package ucadmin.scheduler;

import java.util.List;

/**
 * TaskRegistry = persistence boundary for scheduler.
 *
 * Goal:
 * - Scheduler should not care whether tasks are stored in DBM, flat files, JSON lines, etc.
 * - Another AI can implement a FileTaskRegistry or a DB-backed adapter later.
 *
 * Required behaviors (given your strict-time philosophy):
 * - Tasks must survive restarts (registry persisted via DBM/QueueManager).
 * - Storage target: single JSON file under database/registry (e.g., database/registry/tasks.json).
 * - UPTIME_* tasks persist delay/interval only; they are re-anchored on each boot.
 * - One-shots are deleted after DONE/MISSED; ERROR tasks stay until cleared.
 *
 * Minimal API:
 * - loadAllActive(): load tasks scheduler should consider (SCHEDULED + PAUSED typically)
 * - put/update/delete: basic registry operations
 *
 * NOTE: delta-patch persistence can be implemented inside the registry if desired.
 */
public interface TaskRegistry {

    /**
     * Load all tasks that should be indexed on scheduler startup.
     * Usually includes SCHEDULED and PAUSED, excludes CANCELLED and terminal tasks.
     */
    List<ScheduledTask> loadAllActive();

    /** Persist a new task. Must be durable before scheduler considers it scheduled. */
    void put(ScheduledTask task);

    /** Replace/update an existing task (status changes, lastRunAt, etc). */
    void update(ScheduledTask task);

    /** Remove task (used for auto-cleanup of terminal one-shots if you delete). */
    void delete(String taskId);

    /**
     * Optional: if you want strong "mark terminal" without deleting.
     * Default implementers can just call update(task.withStatus(DONE/MISSED)).
     */
    default void markTerminal(String taskId, ScheduledTask.Status status, long now, String result) {
        throw new UnsupportedOperationException("Optional");
    }
}
