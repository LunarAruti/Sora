package ucadmin.scheduler;

import ucadmin.util.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        PRINT_MESSAGE
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
}
