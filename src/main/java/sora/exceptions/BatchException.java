package sora.exceptions;

import sora.util.Logger;

/**
 * Thrown when a batch of JSON operations fails to build or execute.
 *
 * This includes issues such as malformed path parsing, invalid type
 * conversions, or general data mutation failures. All instances of
 * BatchException automatically log their message and cause to
 * sora/LOGGER.txt for debugging.
 */
public class BatchException extends Exception {

    /**
     * Constructs a new BatchException with the specified detail message.
     *
     * @param message a description of what failed in the batch
     */
    public BatchException(String message) {
        super(message);
        Logger.log(Logger.TAG.ERROR, "[BatchException] " + message);
    }

    /**
     * Constructs a new BatchException with a message and root cause.
     *
     * @param message a description of what failed in the batch
     * @param cause   the underlying exception that triggered this one
     */
    public BatchException(String message, Throwable cause) {
        super(message, cause);
        String log = "[BatchException] " + message
                + " | Cause: "
                + (cause != null ? cause.getClass().getSimpleName() + " - " + cause.getMessage() : "Unknown");
        Logger.log(Logger.TAG.ERROR, log);
    }
}
