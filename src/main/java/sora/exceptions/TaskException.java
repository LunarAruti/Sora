package sora.exceptions;

import sora.util.Logger;

/**
 * Thrown when a task fails validation or cannot be scheduled safely.
 *
 * Typical causes include missing required fields, invalid timing
 * configurations, or unsupported task types. Each instance automatically
 * logs itself to sora/LOGGER.txt for diagnosis.
 */
public class TaskException extends Exception {

    /**
     * Constructs a new TaskException with the specified detail message.
     *
     * @param message description of what failed in the task setup
     */
    public TaskException(String message) {
        super(message);
        Logger.log(Logger.TAG.ERROR, "[TaskException] " + message);
    }

    /**
     * Constructs a new TaskException with a message and root cause.
     *
     * @param message description of what failed in the task setup
     * @param cause   the underlying exception
     */
    public TaskException(String message, Throwable cause) {
        super(message, cause);
        String log = "[TaskException] " + message
                + " | Cause: "
                + (cause != null ? cause.getClass().getSimpleName() + " - " + cause.getMessage() : "Unknown");
        Logger.log(Logger.TAG.ERROR, log);
    }
}
