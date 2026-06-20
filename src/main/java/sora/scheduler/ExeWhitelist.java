package sora.scheduler;

import org.json.JSONArray;
import org.json.JSONObject;
import sora.config.ConfigManager;
import sora.database.QueueManager;
import sora.exceptions.NetworkException;
import sora.exceptions.QueueException;
import sora.exceptions.TaskException;
import sora.util.Logger;
import sora.util.ShutdownManager;
import sora.network.NetworkManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whitelist of allowed scheduled task operations.
 * Ops not registered here cannot be executed.
 */
public final class ExeWhitelist {

    /** Enum of whitelisted operation keys. */
    public enum OpKey {
        LOG_MESSAGE,
        PRINT_MESSAGE,
        EXIT,
        HELP,
        CONFIG_GET,
        CONFIG_SET,
        CONFIG_FLAGS,
        CONFIG_RELOAD,
        TASK_LIST,
        TASK_PAUSE,
        TASK_RESUME,
        TASK_CANCEL,
        NET_DIAG,
        NET_JOURNAL,
        QUEUE_FLUSH,
        LOG_TAIL
    }

    /**
     * Maps opKey to a handler method.
     * Handlers receive a parsed Args list and return a TaskResult.
     * Throw an exception to signal failure and trigger scheduler retry rules.
     */
    private static final Map<String, TaskExecutor.TaskOpHandler> OPS;

    static {
        Map<String, TaskExecutor.TaskOpHandler> m = new LinkedHashMap<>();
        m.put(OpKey.LOG_MESSAGE.name(), ExeWhitelist::logMessage);
        m.put(OpKey.PRINT_MESSAGE.name(), ExeWhitelist::printMessage);
        m.put(OpKey.EXIT.name(), ExeWhitelist::exitProcess);
        m.put(OpKey.HELP.name(), ExeWhitelist::printHelp);
        m.put(OpKey.CONFIG_GET.name(), ExeWhitelist::configGet);
        m.put(OpKey.CONFIG_SET.name(), ExeWhitelist::configSet);
        m.put(OpKey.CONFIG_FLAGS.name(), ExeWhitelist::configFlags);
        m.put(OpKey.CONFIG_RELOAD.name(), ExeWhitelist::configReload);
        m.put(OpKey.TASK_LIST.name(), ExeWhitelist::taskList);
        m.put(OpKey.TASK_PAUSE.name(), ExeWhitelist::taskPause);
        m.put(OpKey.TASK_RESUME.name(), ExeWhitelist::taskResume);
        m.put(OpKey.TASK_CANCEL.name(), ExeWhitelist::taskCancel);
        m.put(OpKey.NET_DIAG.name(), ExeWhitelist::netDiag);
        m.put(OpKey.NET_JOURNAL.name(), ExeWhitelist::netJournal);
        m.put(OpKey.QUEUE_FLUSH.name(), ExeWhitelist::queueFlush);
        m.put(OpKey.LOG_TAIL.name(), ExeWhitelist::logTail);

        OPS = Collections.unmodifiableMap(m);
    }

    /** Returns the handler for an opKey, or null if not whitelisted. */
    public static TaskExecutor.TaskOpHandler get(String opKey) {
        if (opKey == null) return null;
        return OPS.get(opKey);
    }

    /** Returns all whitelisted op keys. */
    public static Set<String> listOpKeys() {
        return OPS.keySet();
    }

    /** Prevents instantiation. */
    private ExeWhitelist() {}

    /**
     * LOG_MESSAGE op.
     *
     * Args:
     * 0 = type (debug, info, warn, error, system)
     * 1 = message
     *
     * Throws IllegalArgumentException on invalid type or message length > 2000.
     */
    private static TaskExecutor.TaskResult logMessage(TaskExecutor.Args args) {
        String typeRaw = args.req(0).value();
        String message = args.req(1).value();
        if (message.length() > 2000) {
            throw new IllegalArgumentException("LOG_MESSAGE: message exceeds 2000 chars.");
        }
        Logger.TAG tag;
        try {
            tag = Logger.TAG.valueOf(typeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("LOG_MESSAGE: invalid type=" + typeRaw);
        }
        Logger.log(tag, message);
        return TaskExecutor.TaskResult.ok("logged");
    }

    /**
     * PRINT_MESSAGE op.
     *
     * Args:
     * 0 = message
     *
     * Throws IllegalArgumentException when message length > 2000.
     */
    private static TaskExecutor.TaskResult printMessage(TaskExecutor.Args args) {
        String message = args.req(0).value();
        if (message.length() > 2000) {
            throw new IllegalArgumentException("PRINT_MESSAGE: message exceeds 2000 chars.");
        }
        System.out.println(message);
        return TaskExecutor.TaskResult.ok("printed");
    }

    /**
     * EXIT op.
     *
     * Args:
     * 0 = optional mode
     *
     * Modes:
     * - missing / empty / "0" / "none" = full graceful shutdown
     * - "1" = fast no-exit shutdown
     */
    private static TaskExecutor.TaskResult exitProcess(TaskExecutor.Args args) {
        String mode = args.opt(0).def("0").value().trim().toLowerCase();
        switch (mode) {
            case "":
            case "0":
            case "none":
                Logger.log(Logger.TAG.SYSTEM, "EXIT op: invoking graceful shutdown.");
                ShutdownManager.shutdown(null);
                return TaskExecutor.TaskResult.ok("graceful shutdown requested");

            case "1":
                Logger.log(Logger.TAG.SYSTEM, "EXIT op: invoking fast no-exit shutdown.");
                ShutdownManager.shutdownNoExit(null);
                return TaskExecutor.TaskResult.ok("fast shutdown requested");

            default:
                throw new IllegalArgumentException("EXIT: invalid mode=" + mode + " (use none/0 or 1)");
        }
    }

    /** Prints the console command inventory file. */
    private static TaskExecutor.TaskResult printHelp(TaskExecutor.Args args) throws Exception {
        String text = Files.readString(Path.of("src", "main", "ConsoleCommands.txt"), StandardCharsets.UTF_8);
        System.out.println(text);
        return TaskExecutor.TaskResult.ok("help printed");
    }

    /** Prints the current in-memory config value and flags for a path. */
    private static TaskExecutor.TaskResult configGet(TaskExecutor.Args args) {
        String path = args.req(0).value();
        Object value = ConfigManager.getValue(path);
        int flags = ConfigManager.getFlags(path);
        boolean noRuntime = ConfigManager.doesNotUpdateRuntime(path);
        boolean explicitApply = ConfigManager.requiresExplicitApply(path);
        String out = "CONFIG_GET path=" + path +
                " value=" + renderValue(value) +
                " flags=" + flags +
                " noRuntimeUpdate=" + noRuntime +
                " requiresExplicitApply=" + explicitApply;
        System.out.println(out);
        return TaskExecutor.TaskResult.ok("config printed");
    }

    /** Updates an in-memory config value only. */
    private static TaskExecutor.TaskResult configSet(TaskExecutor.Args args) {
        String path = args.req(0).value();
        Object oldValue = ConfigManager.getValue(path);
        Object newValue = parseConfigValue(args.req(1).value());
        ConfigManager.setValueInMemory(path, newValue);

        int flags = ConfigManager.getFlags(path);
        StringBuilder out = new StringBuilder()
                .append("CONFIG_SET path=").append(path)
                .append(" old=").append(renderValue(oldValue))
                .append(" new=").append(renderValue(newValue))
                .append(" flags=").append(flags);

        if (ConfigManager.doesNotUpdateRuntime(path)) {
            out.append(" warning=change stored in config memory but does not update live runtime");
        } else if (ConfigManager.requiresExplicitApply(path)) {
            out.append(" warning=change stored in config memory but requires explicit module apply");
        }

        System.out.println(out);
        return TaskExecutor.TaskResult.ok("config updated");
    }

    /** Prints the current flags metadata for a config path. */
    private static TaskExecutor.TaskResult configFlags(TaskExecutor.Args args) {
        String path = args.req(0).value();
        int flags = ConfigManager.getFlags(path);
        String meaning;
        if (ConfigManager.doesNotUpdateRuntime(path)) {
            meaning = "does_not_update_runtime";
        } else if (ConfigManager.requiresExplicitApply(path)) {
            meaning = "requires_explicit_apply";
        } else {
            meaning = "none";
        }
        System.out.println("CONFIG_FLAGS path=" + path + " flags=" + flags + " meaning=" + meaning);
        return TaskExecutor.TaskResult.ok("flags printed");
    }

    /** Reloads runtime config from disk and prints the load source result. */
    private static TaskExecutor.TaskResult configReload(TaskExecutor.Args args) {
        boolean loaded = ConfigManager.reload();
        System.out.println("CONFIG_RELOAD loadedFromFile=" + loaded + " path=" + ConfigManager.getConfigPath());
        return TaskExecutor.TaskResult.ok(loaded ? "reloaded from file" : "reloaded using defaults/fallback");
    }

    /** Prints active scheduled/paused tasks from the registry. */
    private static TaskExecutor.TaskResult taskList(TaskExecutor.Args args) throws TaskException {
        List<ScheduledTask> tasks = new FileTaskRegistry().loadAllActive();
        if (tasks.isEmpty()) {
            System.out.println("TASK_LIST empty");
            return TaskExecutor.TaskResult.ok("no active tasks");
        }
        System.out.println("TASK_LIST count=" + tasks.size());
        for (ScheduledTask task : tasks) {
            System.out.println("  taskId=" + task.taskId +
                    " name=" + task.name +
                    " status=" + task.status +
                    " type=" + task.type +
                    " opKey=" + task.opKey +
                    " priority=" + task.priority);
        }
        return TaskExecutor.TaskResult.ok("task list printed");
    }

    /** Pauses a task by id. */
    private static TaskExecutor.TaskResult taskPause(TaskExecutor.Args args) throws TaskException {
        String taskId = args.req(0).value();
        TaskScheduler.pause(taskId);
        return TaskExecutor.TaskResult.ok("pause requested for " + taskId);
    }

    /** Resumes a task by id. */
    private static TaskExecutor.TaskResult taskResume(TaskExecutor.Args args) throws TaskException {
        String taskId = args.req(0).value();
        TaskScheduler.resume(taskId);
        return TaskExecutor.TaskResult.ok("resume requested for " + taskId);
    }

    /** Cancels a task by id. */
    private static TaskExecutor.TaskResult taskCancel(TaskExecutor.Args args) throws TaskException {
        String taskId = args.req(0).value();
        TaskScheduler.cancel(taskId);
        return TaskExecutor.TaskResult.ok("cancel requested for " + taskId);
    }

    /** Prints a live network diagnostics summary. */
    private static TaskExecutor.TaskResult netDiag(TaskExecutor.Args args) {
        Map<String, Object> diag = NetworkManager.getDiagnosticsSummary();
        System.out.println("NET_DIAG " + diag);
        return TaskExecutor.TaskResult.ok("network diagnostics printed");
    }

    /** Dumps the network journal to a temp path and prints that path. */
    private static TaskExecutor.TaskResult netJournal(TaskExecutor.Args args) throws NetworkException {
        String path = NetworkManager.dumpJournalToTemp();
        System.out.println("NET_JOURNAL path=" + path);
        return TaskExecutor.TaskResult.ok("network journal dumped");
    }

    /** Flushes QueueManager immediately with materialization. */
    private static TaskExecutor.TaskResult queueFlush(TaskExecutor.Args args) throws QueueException {
        QueueManager.flushAll(true);
        return TaskExecutor.TaskResult.ok("queue flush complete");
    }

    /** Prints the most recent in-memory log lines. */
    private static TaskExecutor.TaskResult logTail(TaskExecutor.Args args) {
        int count = Integer.parseInt(args.opt(0).def("20").value());
        if (count < 1 || count > 500) {
            throw new IllegalArgumentException("LOG_TAIL: count must be between 1 and 500");
        }
        List<String> lines = Logger.tailSnapshot(count);
        System.out.println("LOG_TAIL count=" + lines.size());
        for (String line : lines) {
            System.out.print(line);
        }
        return TaskExecutor.TaskResult.ok("log tail printed");
    }

    /** Parses a console config value token into a supported JSON-compatible value. */
    private static Object parseConfigValue(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase("null")) {
            return null;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        if ((value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"))) {
            try {
                return value.startsWith("{") ? new JSONObject(value) : new JSONArray(value);
            } catch (Exception ignored) {
                // Fall through to scalar parsing below.
            }
        }
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    /** Renders a value into concise console text. */
    private static String renderValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }
        return String.valueOf(value);
    }
}
