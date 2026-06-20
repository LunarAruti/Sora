package sora.tools;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for working with Unix epoch seconds and readable time formats.
 */
public final class Time {

    /**
     * Default pattern used for formatting and parsing readable timestamps.
     */
    public static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * Default zone used for formatting and parsing when not specified.
     */
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/New_York");

    /**
     * UTC zone constant for convenience.
     */
    public static final ZoneId UTC = ZoneId.of("America/New_York");

    private Time() {}

    /**
     * Returns the current Unix epoch timestamp in seconds.
     *
     * Behavior:
     * - Uses System.currentTimeMillis() / 1000.
     *
     * @return current epoch time in seconds.
     */
    public static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Formats epoch seconds into a readable string using the default pattern and zone.
     *
     * Behavior:
     * - Uses DEFAULT_PATTERN and DEFAULT_ZONE.
     *
     * @param epochSeconds seconds since Unix epoch.
     * @return formatted timestamp string.
     */
    public static String formatSeconds(long epochSeconds) {
        return formatSeconds(epochSeconds, DEFAULT_PATTERN, DEFAULT_ZONE);
    }

    /**
     * Formats epoch seconds into a readable string using a custom pattern and zone.
     *
     * Behavior:
     * - If pattern is null/blank, DEFAULT_PATTERN is used.
     * - If zone is null, DEFAULT_ZONE is used.
     *
     * @param epochSeconds seconds since Unix epoch.
     * @param pattern format pattern (DateTimeFormatter).
     * @param zone zone to apply when formatting.
     * @return formatted timestamp string.
     */
    public static String formatSeconds(long epochSeconds, String pattern, ZoneId zone) {
        String fmt = (pattern == null || pattern.isBlank()) ? DEFAULT_PATTERN : pattern;
        ZoneId useZone = (zone == null) ? DEFAULT_ZONE : zone;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(fmt);
        return Instant.ofEpochSecond(epochSeconds).atZone(useZone).format(formatter);
    }

    /**
     * Parses a readable timestamp into epoch seconds using the default pattern and zone.
     *
     * Behavior:
     * - Uses DEFAULT_PATTERN and DEFAULT_ZONE.
     * - Throws if parsing fails.
     *
     * @param text readable timestamp string.
     * @return epoch seconds for the parsed time.
     * @throws DateTimeParseException if parsing fails.
     */
    public static long parseSeconds(String text) {
        return parseSeconds(text, DEFAULT_PATTERN, DEFAULT_ZONE);
    }

    /**
     * Parses a readable timestamp into epoch seconds using a custom pattern and zone.
     *
     * Behavior:
     * - If pattern is null/blank, DEFAULT_PATTERN is used.
     * - If zone is null, DEFAULT_ZONE is used.
     * - Throws if parsing fails.
     *
     * @param text readable timestamp string.
     * @param pattern format pattern (DateTimeFormatter).
     * @param zone zone to apply when parsing.
     * @return epoch seconds for the parsed time.
     * @throws DateTimeParseException if parsing fails.
     */
    public static long parseSeconds(String text, String pattern, ZoneId zone) {
        String fmt = (pattern == null || pattern.isBlank()) ? DEFAULT_PATTERN : pattern;
        ZoneId useZone = (zone == null) ? DEFAULT_ZONE : zone;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(fmt);
        LocalDateTime dateTime = LocalDateTime.parse(text, formatter);
        return dateTime.atZone(useZone).toEpochSecond();
    }

    /**
     * Converts epoch seconds to epoch milliseconds.
     *
     * Behavior:
     * - Multiplies by 1000.
     *
     * @param epochSeconds seconds since Unix epoch.
     * @return milliseconds since Unix epoch.
     */
    public static long toMillis(long epochSeconds) {
        return epochSeconds * 1000L;
    }

    /**
     * Converts epoch milliseconds to epoch seconds.
     *
     * Behavior:
     * - Divides by 1000.
     *
     * @param epochMillis milliseconds since Unix epoch.
     * @return seconds since Unix epoch.
     */
    public static long toSeconds(long epochMillis) {
        return epochMillis / 1000L;
    }

    /**
     * Checks whether a timestamp is within a range of epoch seconds.
     *
     * Behavior:
     * - If inclusive is true, endpoints are considered inside the range.
     * - If inclusive is false, endpoints are excluded.
     *
     * @param valueSeconds value to test.
     * @param startSeconds range start in epoch seconds.
     * @param endSeconds range end in epoch seconds.
     * @param inclusive true for inclusive range, false for exclusive.
     * @return true if the value is within the range, false otherwise.
     */
    public static boolean isWithinRangeSeconds(long valueSeconds, long startSeconds, long endSeconds, boolean inclusive) {
        long min = AMath.min(startSeconds, endSeconds);
        long max = AMath.max(startSeconds, endSeconds);
        if (inclusive) {
            return valueSeconds >= min && valueSeconds <= max;
        }
        return valueSeconds > min && valueSeconds < max;
    }

    /**
     * Clamps a timestamp to the given epoch-second bounds.
     *
     * Behavior:
     * - If value is below min, returns min.
     * - If value is above max, returns max.
     *
     * @param valueSeconds value to clamp.
     * @param minSeconds minimum bound.
     * @param maxSeconds maximum bound.
     * @return clamped value in epoch seconds.
     */
    public static long clampSeconds(long valueSeconds, long minSeconds, long maxSeconds) {
        long min = AMath.min(minSeconds, maxSeconds);
        long max = AMath.max(minSeconds, maxSeconds);
        if (valueSeconds < min) {
            return min;
        }
        if (valueSeconds > max) {
            return max;
        }
        return valueSeconds;
    }

    /**
     * Formats a duration in seconds into a human-readable string.
     *
     * Behavior:
     * - Uses days, hours, minutes, and seconds.
     * - Preserves sign by prefixing "-" for negative durations.
     *
     * @param durationSeconds duration in seconds.
     * @return formatted duration string.
     */
    public static String formatDurationSeconds(long durationSeconds) {
        boolean negative = durationSeconds < 0;
        long remaining = Math.abs(durationSeconds);

        long days = remaining / 86_400L;
        remaining %= 86_400L;
        long hours = remaining / 3_600L;
        remaining %= 3_600L;
        long minutes = remaining / 60L;
        long seconds = remaining % 60L;

        String result = days + "d " + hours + "h " + minutes + "m " + seconds + "s";
        return negative ? "-" + result : result;
    }

    /**
     * Simple timer utility for measuring elapsed time.
     */
    public static final class Timer {

        private long startNanos = -1L;
        private long endNanos = -1L;
        private boolean running = false;

        /**
         * Creates a new timer in a stopped state.
         *
         * Behavior:
         * - Timer does not start automatically.
         * - Call start() to begin timing.
         */
        public Timer() {}

        /**
         * Starts or restarts the timer.
         *
         * Behavior:
         * - Resets previous end time.
         * - Sets the start time to the current monotonic time.
         *
         * @return this timer for convenience chaining.
         */
        public Timer start() {
            this.startNanos = System.nanoTime();
            this.endNanos = -1L;
            this.running = true;
            return this;
        }

        /**
         * Ends the timer and freezes the elapsed time.
         *
         * Behavior:
         * - If the timer is not running, no changes are made.
         * - Elapsed time becomes stable until start() is called again.
         *
         * @return this timer for convenience chaining.
         */
        public Timer end() {
            if (!running) {
                return this;
            }
            this.endNanos = System.nanoTime();
            this.running = false;
            return this;
        }

        /**
         * Reads the elapsed time in seconds.
         *
         * Behavior:
         * - Works while running or after end().
         * - Returns 0 if the timer has never been started.
         * - Uses a monotonic clock (not affected by system time changes).
         *
         * @return elapsed time in whole seconds.
         */
        public long read() {
            if (startNanos < 0L) {
                return 0L;
            }
            long end = running ? System.nanoTime() : endNanos;
            if (end < 0L) {
                end = System.nanoTime();
            }
            long elapsedNanos = AMath.max(0L, end - startNanos);
            return TimeUnit.NANOSECONDS.toSeconds(elapsedNanos);
        }
    }
}
