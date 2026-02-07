package ucadmin.scheduler;

import org.json.JSONObject;
import ucadmin.database.DatabaseManager;
import ucadmin.exceptions.DatabaseException;
import ucadmin.exceptions.TaskException;
import ucadmin.util.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * File-backed TaskRegistry using a single JSON file under database/registry.
 */
public final class FileTaskRegistry implements TaskRegistry {

    private static final String REGISTRY_PATH = "database/registry/tasks.json";
    private static final String TASKS_KEY = "tasks";

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final boolean keepTerminalTasks;

    /** Creates a registry that deletes terminal tasks. */
    public FileTaskRegistry() {
        this(false);
    }

    /** Creates a registry with optional terminal retention. */
    public FileTaskRegistry(boolean keepTerminalTasks) {
        this.keepTerminalTasks = keepTerminalTasks;
    }

    @Override
    /** Loads active tasks from the registry file. */
    public List<ScheduledTask> loadAllActive() throws TaskException {
        Logger.log(Logger.TAG.DEBUG, "FileTaskRegistry: loadAllActive begin.");
        List<ScheduledTask> out = new ArrayList<>();
        int total = 0;
        int bad = 0;
        Object rootObj;
        try {
            rootObj = DatabaseManager.readJSONPath(REGISTRY_PATH, "");
        } catch (DatabaseException e) {
            if (ensureRegistryIfMissing()) {
                try {
                    rootObj = DatabaseManager.readJSONPath(REGISTRY_PATH, "");
                } catch (DatabaseException retry) {
                    throw new TaskException("TaskRegistry load failed: " + retry.getMessage(), retry);
                }
            } else {
                throw new TaskException("TaskRegistry load failed: " + e.getMessage(), e);
            }
        }
        if (!(rootObj instanceof JSONObject root)) {
            throw new TaskException("TaskRegistry load failed: root is not an object.");
        }

        JSONObject tasks = root.optJSONObject(TASKS_KEY);
        if (tasks == null) {
            Logger.log(Logger.TAG.INFO, "FileTaskRegistry: loadAllActive complete (empty registry).");
            return out;
        }

        for (String taskId : tasks.keySet()) {
            total++;
            Object raw = tasks.opt(taskId);
            if (!(raw instanceof JSONObject obj)) {
                markBadTask(taskId, null, "task entry is not an object");
                bad++;
                continue;
            }

            String statusStr = obj.optString("status", null);
            if (statusStr == null) {
                markBadTask(taskId, obj, "missing status");
                bad++;
                continue;
            }

            ScheduledTask.Status status;
            try {
                status = ScheduledTask.Status.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                markBadTask(taskId, obj, "invalid status=" + statusStr);
                bad++;
                continue;
            }

            if (status != ScheduledTask.Status.SCHEDULED && status != ScheduledTask.Status.PAUSED) {
                continue;
            }

            try {
                ScheduledTask task = fromJson(taskId, obj);
                out.add(task);
            } catch (TaskException e) {
                markBadTask(taskId, obj, e.getMessage());
                bad++;
            }
        }
        Logger.log(Logger.TAG.INFO,
                "FileTaskRegistry: loadAllActive complete total=" + total +
                        " active=" + out.size() + " bad=" + bad);
        return out;
    }

    @Override
    /** Stores a new task in the registry file. */
    public void put(ScheduledTask task) throws TaskException {
        Logger.log(Logger.TAG.DEBUG, "FileTaskRegistry: put taskId=" + (task == null ? "null" : task.taskId));
        writeTask(task);
    }

    @Override
    /** Updates an existing task in the registry file. */
    public void update(ScheduledTask task) throws TaskException {
        Logger.log(Logger.TAG.DEBUG, "FileTaskRegistry: update taskId=" + (task == null ? "null" : task.taskId));
        writeTask(task);
    }

    /** Fetches a task by id from the registry file. */
    @Override
    public ScheduledTask get(String taskId) throws TaskException {
        Logger.log(Logger.TAG.DEBUG, "FileTaskRegistry: get taskId=" + taskId);
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskRegistry.get: taskId is null/blank.");
        }
        Object raw;
        try {
            raw = DatabaseManager.readJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId);
        } catch (DatabaseException e) {
            if (ensureRegistryIfMissing()) {
                try {
                    raw = DatabaseManager.readJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId);
                } catch (DatabaseException retry) {
                    throw new TaskException("TaskRegistry get failed for " + taskId + ": " + retry.getMessage(), retry);
                }
            } else {
                throw new TaskException("TaskRegistry get failed for " + taskId + ": " + e.getMessage(), e);
            }
        }
        if (raw == null) {
            Logger.log(Logger.TAG.DEBUG, "FileTaskRegistry: get miss taskId=" + taskId);
            return null;
        }
        if (!(raw instanceof JSONObject obj)) {
            markBadTask(taskId, null, "task entry is not an object");
            Logger.log(Logger.TAG.WARN, "FileTaskRegistry: get bad entry taskId=" + taskId);
            return null;
        }
        try {
            return fromJson(taskId, obj);
        } catch (TaskException e) {
            markBadTask(taskId, obj, e.getMessage());
            Logger.log(Logger.TAG.WARN, "FileTaskRegistry: get parse failed taskId=" + taskId);
            return null;
        }
    }

    @Override
    /** Deletes a task from the registry file. */
    public void delete(String taskId) throws TaskException {
        Logger.log(Logger.TAG.DEBUG, "FileTaskRegistry: delete taskId=" + taskId);
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskRegistry.delete: taskId is null/blank.");
        }
        try {
            DatabaseManager.removeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId);
        } catch (DatabaseException e) {
            if (ensureRegistryIfMissing()) {
                try {
                    DatabaseManager.removeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId);
                } catch (DatabaseException retry) {
                    throw new TaskException("TaskRegistry delete failed for " + taskId + ": " + retry.getMessage(), retry);
                }
            } else {
                throw new TaskException("TaskRegistry delete failed for " + taskId + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    /** Marks a task terminal or deletes it based on retention settings. */
    public void markTerminal(String taskId, ScheduledTask.Status status, long now, String result) throws TaskException {
        Logger.log(Logger.TAG.DEBUG, "FileTaskRegistry: markTerminal taskId=" + taskId + " status=" + status);
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("TaskRegistry.markTerminal: taskId is null/blank.");
        }
        if (!keepTerminalTasks) {
            delete(taskId);
            return;
        }
        try {
            Object raw = DatabaseManager.readJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId);
            if (!(raw instanceof JSONObject)) {
                throw new TaskException("TaskRegistry markTerminal: missing task " + taskId);
            }
            DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId + ".status", status.name(), true);
            DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId + ".updatedAt", now, true);
            DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId + ".lastResult", result, true);
        } catch (DatabaseException e) {
            if (ensureRegistryIfMissing()) {
                try {
                    Object raw = DatabaseManager.readJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId);
                    if (!(raw instanceof JSONObject)) {
                        throw new TaskException("TaskRegistry markTerminal: missing task " + taskId);
                    }
                    DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId + ".status", status.name(), true);
                    DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId + ".updatedAt", now, true);
                    DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId + ".lastResult", result, true);
                } catch (DatabaseException retry) {
                    throw new TaskException("TaskRegistry markTerminal failed for " + taskId + ": " + retry.getMessage(), retry);
                }
            } else {
                throw new TaskException("TaskRegistry markTerminal failed for " + taskId + ": " + e.getMessage(), e);
            }
        }
    }

    /** Writes a task object to the registry file. */
    private void writeTask(ScheduledTask task) throws TaskException {
        if (task == null) {
            throw new IllegalArgumentException("TaskRegistry.writeTask: task is null.");
        }
        try {
            DatabaseManager.writeJSONPath(
                    REGISTRY_PATH,
                    TASKS_KEY + "." + task.taskId,
                    toJson(task),
                    true
            );
        } catch (DatabaseException e) {
            if (ensureRegistryIfMissing()) {
                try {
                    DatabaseManager.writeJSONPath(
                            REGISTRY_PATH,
                            TASKS_KEY + "." + task.taskId,
                            toJson(task),
                            true
                    );
                } catch (DatabaseException retry) {
                    throw new TaskException("TaskRegistry write failed for " + task.taskId + ": " + retry.getMessage(), retry);
                }
            } else {
                throw new TaskException("TaskRegistry write failed for " + task.taskId + ": " + e.getMessage(), e);
            }
        }
    }

    /** Creates the registry file if missing; returns true if created. */
    private boolean ensureRegistryIfMissing() throws TaskException {
        if (initialized.get()) return false;
        boolean exists;
        try {
            exists = DatabaseManager.fileExists(REGISTRY_PATH);
        } catch (DatabaseException e) {
            throw new TaskException("TaskRegistry init failed: " + e.getMessage(), e);
        }
        if (exists) {
            initialized.set(true);
            Logger.log(Logger.TAG.DEBUG, "FileTaskRegistry: registry file already exists.");
            return false;
        }
        JSONObject root = new JSONObject().put(TASKS_KEY, new JSONObject());
        try {
            DatabaseManager.createJSON(REGISTRY_PATH, root);
            initialized.set(true);
            Logger.log(Logger.TAG.INFO, "FileTaskRegistry: registry file created.");
            return true;
        } catch (DatabaseException e) {
            throw new TaskException("TaskRegistry init failed: " + e.getMessage(), e);
        }
    }

    /** Serializes a ScheduledTask into JSON. */
    private static JSONObject toJson(ScheduledTask task) {
        JSONObject obj = new JSONObject();
        obj.put("name", task.name);
        obj.put("type", task.type.name());
        obj.put("status", task.status.name());
        obj.put("priority", task.priority);
        obj.put("opKey", task.opKey);
        obj.put("opArgs", task.opArgs == null ? JSONObject.NULL : task.opArgs);
        obj.put("retriesRemaining", task.retriesRemaining);

        obj.put("executeAt", task.executeAt == null ? JSONObject.NULL : task.executeAt);
        obj.put("intervalMs", task.intervalMs == null ? JSONObject.NULL : task.intervalMs);
        obj.put("delayMs", task.delayMs == null ? JSONObject.NULL : task.delayMs);

        obj.put("createdAt", task.createdAt);
        obj.put("updatedAt", task.updatedAt);
        obj.put("lastRunAt", task.lastRunAt == null ? JSONObject.NULL : task.lastRunAt);
        obj.put("lastResult", task.lastResult == null ? JSONObject.NULL : task.lastResult);
        obj.put("runCount", task.runCount);
        return obj;
    }

    /** Deserializes a ScheduledTask from JSON. */
    private static ScheduledTask fromJson(String taskId, JSONObject obj) throws TaskException {
        try {
            String name = obj.optString("name", null);
            String typeStr = obj.optString("type", null);
            String statusStr = obj.optString("status", null);
            String opKey = obj.optString("opKey", null);
            String opArgs = getOptionalString(obj, "opArgs");

            if (name == null || typeStr == null || statusStr == null || opKey == null) {
                throw new TaskException("TaskRegistry: invalid task entry for " + taskId);
            }

            ScheduledTask.Type type = ScheduledTask.Type.valueOf(typeStr);
            ScheduledTask.Status status = ScheduledTask.Status.valueOf(statusStr);
            validateTask(taskId, obj, type, status);

            return new ScheduledTask.Builder()
                    .taskId(taskId)
                    .name(name)
                    .type(type)
                    .status(status)
                    .priority(obj.optInt("priority", 10))
                    .opKey(opKey)
                    .opArgs(opArgs)
                    .retriesRemaining(obj.optInt("retriesRemaining", 0))
                    .executeAt(getOptionalLong(obj, "executeAt"))
                    .intervalMs(getOptionalLong(obj, "intervalMs"))
                    .delayMs(getOptionalLong(obj, "delayMs"))
                    .createdAt(obj.optLong("createdAt", 0L))
                    .updatedAt(obj.optLong("updatedAt", 0L))
                    .lastRunAt(getOptionalLong(obj, "lastRunAt"))
                    .lastResult(getOptionalString(obj, "lastResult"))
                    .runCount(obj.optLong("runCount", 0L))
                    .build();
        } catch (TaskException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskException("TaskRegistry: failed to parse task " + taskId + ": " + e.getMessage(), e);
        }
    }

    /** Validates type-specific required fields. */
    private static void validateTask(String taskId, JSONObject obj, ScheduledTask.Type type, ScheduledTask.Status status)
            throws TaskException {
        if (type == null || status == null) {
            throw new TaskException("TaskRegistry: missing type/status for " + taskId);
        }
        switch (type) {
            case ABSOLUTE_ONESHOT:
                if (getOptionalLong(obj, "executeAt") == null) {
                    throw new TaskException("TaskRegistry: missing executeAt for " + taskId);
                }
                break;
            case ABSOLUTE_INTERVAL:
                if (getOptionalLong(obj, "executeAt") == null || getOptionalLong(obj, "intervalMs") == null) {
                    throw new TaskException("TaskRegistry: missing interval fields for " + taskId);
                }
                break;
            case UPTIME_DELAY:
                if (getOptionalLong(obj, "delayMs") == null) {
                    throw new TaskException("TaskRegistry: missing delayMs for " + taskId);
                }
                break;
            case UPTIME_INTERVAL:
                if (getOptionalLong(obj, "intervalMs") == null) {
                    throw new TaskException("TaskRegistry: missing intervalMs for " + taskId);
                }
                break;
            default:
                throw new TaskException("TaskRegistry: unknown type for " + taskId);
        }
    }

    /** Reads an optional long, returning null if missing or null. */
    private static Long getOptionalLong(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) return null;
        return obj.optLong(key);
    }

    /** Reads an optional string, returning null if missing or null. */
    private static String getOptionalString(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) return null;
        return obj.optString(key, null);
    }

    /** Marks a malformed task as ERROR, preserving any readable fields. */
    private static void markBadTask(String taskId, JSONObject obj, String reason) {
        String message = "TaskRegistry: bad task " + taskId + " -> " + reason;
        Logger.log(Logger.TAG.ERROR, message);
        new TaskException(message);
        JSONObject err = new JSONObject();
        if (obj != null) {
            copyIfPresent(obj, err, "name");
            copyIfPresent(obj, err, "type");
            copyIfPresent(obj, err, "opKey");
            copyIfPresent(obj, err, "opArgs");
            copyIfPresent(obj, err, "priority");
            copyIfPresent(obj, err, "retriesRemaining");
            copyIfPresent(obj, err, "executeAt");
            copyIfPresent(obj, err, "intervalMs");
            copyIfPresent(obj, err, "delayMs");
            copyIfPresent(obj, err, "createdAt");
            copyIfPresent(obj, err, "updatedAt");
            copyIfPresent(obj, err, "lastRunAt");
            copyIfPresent(obj, err, "lastResult");
            copyIfPresent(obj, err, "runCount");
        }
        err.put("status", ScheduledTask.Status.ERROR.name());
        err.put("lastResult", message);
        err.put("updatedAt", System.currentTimeMillis());
        try {
            DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId, err, true);
        } catch (DatabaseException e) {
            new TaskException("TaskRegistry: failed to mark bad task " + taskId + ": " + e.getMessage(), e);
        }
    }

    /** Copies a JSON field if present and non-null. */
    private static void copyIfPresent(JSONObject src, JSONObject dest, String key) {
        if (src.has(key) && !src.isNull(key)) {
            dest.put(key, src.get(key));
        }
    }
}
