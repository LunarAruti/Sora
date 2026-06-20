package sora.tools.pixelgenerator;

import sora.util.Logger;

/**
 * Runtime exception for pixel-generator validation and render failures.
 *
 * <p>Each instance logs itself immediately for diagnostics so render failures
 * are visible even when callers do not catch and log the exception.</p>
 */
public class PixelGeneratorException extends RuntimeException {

    /**
     * Creates a new exception with a detail message.
     *
     * @param message description of the failure
     */
    public PixelGeneratorException(String message) {
        this(message, null);
    }

    /**
     * Creates a new exception with a detail message and root cause.
     *
     * @param message description of the failure
     * @param cause underlying cause when available
     */
    public PixelGeneratorException(String message, Throwable cause) {
        super(message, cause);
        StringBuilder builder = new StringBuilder("[PixelGeneratorException] ")
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
