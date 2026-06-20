package sora.exceptions;

import sora.util.Logger;

/**
 * Thrown when a queue operation fails to enqueue, dequeue,
 * or execute properly within the QueueManager.
 *
 * Typical causes include corrupted job data, interrupted threads,
 * or failed writes to the batch subsystem. Each instance automatically
 * logs itself to sora/LOGGER.txt for diagnosis.
 */
public class QueueException extends Exception {

    /**
     * Constructs a new QueueException with the specified detail message.
     *
     * @param message description of what failed in the queue
     */
    public QueueException(String message) {
        super(message);
        Logger.log(Logger.TAG.ERROR, "[QueueException] " + message);
    }

    /**
     * Constructs a new QueueException with a message and root cause.
     *
     * @param message description of what failed in the queue
     * @param cause   the underlying exception
     */
    public QueueException(String message, Throwable cause) {
        super(message, cause);
        String log = "[QueueException] " + message
                + " | Cause: "
                + (cause != null ? cause.getClass().getSimpleName() + " - " + cause.getMessage() : "Unknown");
        Logger.log(Logger.TAG.ERROR, log);
    }
}
