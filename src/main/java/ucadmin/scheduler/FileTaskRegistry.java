package ucadmin.scheduler;

import org.json.JSONObject;
import ucadmin.database.DatabaseManager;
import ucadmin.exceptions.DatabaseException;
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

    public FileTaskRegistry() {
        this(false);
    }

    public FileTaskRegistry(boolean keepTerminalTasks) {
        this.keepTerminalTasks = keepTerminalTasks;
    }

    @Override
    public List<ScheduledTask> loadAllActive() {
        ensureRegistry();
        List<ScheduledTask> out = new ArrayList<>();
        try {
            Object rootObj = DatabaseManager.readJSONPath(REGISTRY_PATH, null);
            if (!(rootObj instanceof JSONObject root)) {
                Logger.log(Logger.TAG.WARN, "TaskRegistry: root is not an object; skipping load.");
                return out;
            }

            JSONObject tasks = root.optJSONObject(TASKS_KEY);
            if (tasks == null) return out;

            for (String taskId : tasks.keySet()) {
                Object raw = tasks.opt(taskId);
                if (!(raw instanceof JSONObject obj)) continue;
                ScheduledTask task = fromJson(taskId, obj);
                if (task == null) continue;
                if (task.status == ScheduledTask.Status.SCHEDULED ||
                        task.status == ScheduledTask.Status.PAUSED) {
                    out.add(task);
                }
            }
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "TaskRegistry load failed: " + e.getMessage());
        }
        return out;
    }

    @Override
    public void put(ScheduledTask task) {
        writeTask(task);
    }

    @Override
    public void update(ScheduledTask task) {
        writeTask(task);
    }

    @Override
    public void delete(String taskId) {
        ensureRegistry();
        if (taskId == null || taskId.isBlank()) return;
        try {
            DatabaseManager.removeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "TaskRegistry delete failed for " + taskId + ": " + e.getMessage());
        }
    }

    @Override
    public void markTerminal(String taskId, ScheduledTask.Status status, long now, String result) {
        ensureRegistry();
        if (taskId == null || taskId.isBlank()) return;
        if (!keepTerminalTasks) {
            delete(taskId);
            return;
        }
        try {
            Object raw = DatabaseManager.readJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId);
            if (!(raw instanceof JSONObject)) {
                Logger.log(Logger.TAG.WARN, "TaskRegistry markTerminal: missing task " + taskId);
                return;
            }
            DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId + ".status", status.name(), true);
            DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId + ".updatedAt", now, true);
            DatabaseManager.writeJSONPath(REGISTRY_PATH, TASKS_KEY + "." + taskId + ".lastResult", result, true);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "TaskRegistry markTerminal failed for " + taskId + ": " + e.getMessage());
        }
    }

    private void writeTask(ScheduledTask task) {
        ensureRegistry();
        if (task == null) return;
        try {
            DatabaseManager.writeJSONPath(
                    REGISTRY_PATH,
                    TASKS_KEY + "." + task.taskId,
                    toJson(task),
                    true
            );
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "TaskRegistry write failed for " + task.taskId + ": " + e.getMessage());
        }
    }

    private void ensureRegistry() {
        if (initialized.get()) return;
        JSONObject root = new JSONObject().put(TASKS_KEY, new JSONObject());
        try {
            DatabaseManager.createJSON(REGISTRY_PATH, root);
            initialized.set(true);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "TaskRegistry init failed: " + e.getMessage());
        }
    }

    private static JSONObject toJson(ScheduledTask task) {
        JSONObject obj = new JSONObject();
        obj.put("name", task.name);
        obj.put("type", task.type.name());
        obj.put("status", task.status.name());
        obj.put("priority", task.priority);
        obj.put("command", task.command);
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

    private static ScheduledTask fromJson(String taskId, JSONObject obj) {
        try {
            String name = obj.optString("name", null);
            String typeStr = obj.optString("type", null);
            String statusStr = obj.optString("status", null);
            String command = obj.optString("command", null);

            if (name == null || typeStr == null || statusStr == null || command == null) {
                Logger.log(Logger.TAG.WARN, "TaskRegistry: invalid task entry for " + taskId);
                return null;
            }

            ScheduledTask.Type type = ScheduledTask.Type.valueOf(typeStr);
            ScheduledTask.Status status = ScheduledTask.Status.valueOf(statusStr);
            if (!validateTask(taskId, obj, type, status)) return null;

            return new ScheduledTask.Builder()
                    .taskId(taskId)
                    .name(name)
                    .type(type)
                    .status(status)
                    .priority(obj.optInt("priority", 10))
                    .command(command)
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
        } catch (Exception e) {
            Logger.log(Logger.TAG.WARN, "TaskRegistry: failed to parse task " + taskId + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean validateTask(String taskId, JSONObject obj, ScheduledTask.Type type, ScheduledTask.Status status) {
        if (type == null || status == null) return false;
        switch (type) {
            case ABSOLUTE_ONESHOT:
                if (getOptionalLong(obj, "executeAt") == null) {
                    Logger.log(Logger.TAG.WARN, "TaskRegistry: missing executeAt for " + taskId);
                    return false;
                }
                break;
            case ABSOLUTE_INTERVAL:
                if (getOptionalLong(obj, "executeAt") == null || getOptionalLong(obj, "intervalMs") == null) {
                    Logger.log(Logger.TAG.WARN, "TaskRegistry: missing interval fields for " + taskId);
                    return false;
                }
                break;
            case UPTIME_DELAY:
                if (getOptionalLong(obj, "delayMs") == null) {
                    Logger.log(Logger.TAG.WARN, "TaskRegistry: missing delayMs for " + taskId);
                    return false;
                }
                break;
            case UPTIME_INTERVAL:
                if (getOptionalLong(obj, "intervalMs") == null) {
                    Logger.log(Logger.TAG.WARN, "TaskRegistry: missing intervalMs for " + taskId);
                    return false;
                }
                break;
            default:
                return false;
        }
        return true;
    }

    private static Long getOptionalLong(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) return null;
        return obj.optLong(key);
    }

    private static String getOptionalString(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) return null;
        return obj.optString(key, null);
    }
}
