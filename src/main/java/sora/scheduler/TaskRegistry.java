package sora.scheduler;

import sora.exceptions.TaskException;

import java.util.List;


public interface TaskRegistry {

    /**
     * Load all tasks that should be indexed on scheduler startup.
     * Usually includes SCHEDULED and PAUSED, excludes CANCELLED and terminal tasks.
     */
    List<ScheduledTask> loadAllActive() throws TaskException;

    /** Persist a new task. Must be durable before scheduler considers it scheduled. */
    void put(ScheduledTask task) throws TaskException;

    /** Replace/update an existing task (status changes, lastRunAt, etc). */
    void update(ScheduledTask task) throws TaskException;

    /** Fetch a single task by id. Returns null if not found. */
    ScheduledTask get(String taskId) throws TaskException;

    /** Remove task (used for auto-cleanup of terminal one-shots if you delete). */
    void delete(String taskId) throws TaskException;

    /**
     * Optional: if you want strong "mark terminal" without deleting.
     * Default implementers can just call update(task.withStatus(DONE/MISSED)).
     */
    default void markTerminal(String taskId, ScheduledTask.Status status, long now, String result) throws TaskException {
        throw new UnsupportedOperationException("Optional");
    }
}
