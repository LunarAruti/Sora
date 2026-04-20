package ucadmin.exceptions;

import ucadmin.util.Logger;

/**
 * Thrown when a Discord action fails to validate or execute properly.
 *
 * <p>Use this for action-layer errors that should surface cleanly
 * to callers without exposing lower-level exceptions.</p>
 */
public class ActionException extends RuntimeException {

    /**
     * Constructs a new ActionException with the specified detail message.
     *
     * @param message description of what failed in the action layer
     */
    public ActionException(String message) {
        this(message, null, Logger.TAG.ACTION_REJECT);
    }

    /**
     * Constructs a new ActionException with a message and root cause.
     *
     * @param message description of what failed in the action layer
     * @param cause   the underlying exception
     */
    public ActionException(String message, Throwable cause) {
        this(message, cause, Logger.TAG.ACTION_REJECT);
    }

    /**
     * Constructs a new ActionException with a specific log tag.
     *
     * @param message description of what failed in the action layer
     * @param tag     log tag to use (WARN/ERROR/ACTION_REJECT, etc.)
     */
    public ActionException(String message, Logger.TAG tag) {
        this(message, null, tag);
    }

    /**
     * Constructs a new ActionException with a message, root cause, and log tag.
     *
     * @param message description of what failed in the action layer
     * @param cause   the underlying exception
     * @param tag     log tag to use (WARN/ERROR/ACTION_REJECT, etc.)
     */
    public ActionException(String message, Throwable cause, Logger.TAG tag) {
        super(message, cause);
        Logger.TAG safeTag = (tag == null) ? Logger.TAG.ERROR : tag;
        String log = "[ActionException] " + message
                + " | Cause: "
                + (cause != null ? cause.getClass().getSimpleName() + " - " + cause.getMessage() : "Unknown");
        Logger.log(safeTag, log);
    }
}
