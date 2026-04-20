package ucadmin.exceptions;

import ucadmin.util.Logger;

/**
 * Thrown when a Discord object is mutated illegally or fails validation.
 *
 * <p>Used by object setters to enforce sealed objects and input constraints.
 * Each instance logs itself to LOGGER.txt for diagnosis.</p>
 */
public class ObjectException extends RuntimeException {

    /**
     * Constructs a new ObjectException with the specified detail message.
     *
     * @param message description of the validation or mutation failure
     */
    public ObjectException(String message) {
        this(message, null, Logger.TAG.OBJECT_REJECT);
    }

    /**
     * Constructs a new ObjectException with a message and root cause.
     *
     * @param message description of the validation or mutation failure
     * @param cause   the underlying exception
     */
    public ObjectException(String message, Throwable cause) {
        this(message, cause, Logger.TAG.OBJECT_REJECT);
    }

    /**
     * Constructs a new ObjectException with a specific log tag.
     *
     * @param message description of the validation or mutation failure
     * @param tag     log tag to use (WARN/ERROR/OBJECT_REJECT, etc.)
     */
    public ObjectException(String message, Logger.TAG tag) {
        this(message, null, tag);
    }

    /**
     * Constructs a new ObjectException with a message, root cause, and log tag.
     *
     * @param message description of the validation or mutation failure
     * @param cause   the underlying exception
     * @param tag     log tag to use (WARN/ERROR/OBJECT_REJECT, etc.)
     */
    public ObjectException(String message, Throwable cause, Logger.TAG tag) {
        super(message, cause);
        Logger.TAG safeTag = (tag == null) ? Logger.TAG.ERROR : tag;
        String log = "[ObjectException] " + message
                + " | Cause: "
                + (cause != null ? cause.getClass().getSimpleName() + " - " + cause.getMessage() : "Unknown");
        Logger.log(safeTag, log);
    }
}
