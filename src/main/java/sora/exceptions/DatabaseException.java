package sora.exceptions;

import sora.util.Logger;

/**
 * Custom runtime exception for database-related errors.
 *
 * This exception is automatically logged to sora/LOGGER.txt
 * upon creation. The logger records both the message and, if
 * available, the underlying cause for debugging purposes.
 */
public class DatabaseException extends RuntimeException {

    /**
     * Constructs a new DatabaseException with the specified detail message.
     * Automatically logs the message to the system logger as an ERROR.
     *
     * @param message the detail message describing the error
     */
    public DatabaseException(String message) {
        super(message);
        Logger.log(Logger.TAG.ERROR, "[DatabaseException] " + message);
    }

    /**
     * Constructs a new DatabaseException with the specified detail message
     * and cause. Automatically logs the message and cause stack trace to
     * the system logger as an ERROR.
     *
     * @param message the detail message describing the error
     * @param cause the underlying cause of this exception
     */
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
        StringBuilder builder = new StringBuilder("[DatabaseException] ")
                .append(message);
        if (cause != null) {
            builder.append(" | Cause: ")
                    .append(cause.getClass().getSimpleName())
                    .append(" - ")
                    .append(cause.getMessage());
        }
        Logger.log(Logger.TAG.ERROR, builder.toString());
    }
}
