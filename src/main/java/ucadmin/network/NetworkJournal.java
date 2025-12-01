package ucadmin.network;

import ucadmin.exceptions.NetworkException;
import ucadmin.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Returns a chronological list of the most recent network request completions,
 * ordered from oldest to newest.
 *
 * <p>Used for debugging, admin history views, or request tracing.</p>
 *
 * @return immutable list of recent journal entries
 */
public final class NetworkJournal {

    /** Default number of entries to keep in memory. */
    private static final int DEFAULT_CAPACITY = 512;

    private static final Object LOCK = new Object();
    private static JournalEntry[] buffer = new JournalEntry[DEFAULT_CAPACITY];
    private static int writeIndex = 0;
    private static int size = 0;

    private NetworkJournal() {
        // no instances
    }

    /**
     * Immutable summary of a completed network request.
     */
    public static final class JournalEntry {
        private final long loggedAtMillis;
        private final String service;
        private final String endpoint;
        private final String traceId;
        private final String finalUrl;
        private final int statusCode;
        private final int attempts;
        private final long latencyMillis;
        private final boolean success;
        private final NetworkException.ErrorType errorType;

        public JournalEntry(
                long loggedAtMillis,
                String service,
                String endpoint,
                String traceId,
                String finalUrl,
                int statusCode,
                int attempts,
                long latencyMillis,
                boolean success,
                NetworkException.ErrorType errorType
        ) {
            this.loggedAtMillis = loggedAtMillis;
            this.service = service;
            this.endpoint = endpoint;
            this.traceId = traceId;
            this.finalUrl = finalUrl;
            this.statusCode = statusCode;
            this.attempts = attempts;
            this.latencyMillis = latencyMillis;
            this.success = success;
            this.errorType = errorType;
        }

        public long getLoggedAtMillis() { return loggedAtMillis; }
        public String getService() { return service; }
        public String getEndpoint() { return endpoint; }
        public String getTraceId() { return traceId; }
        public String getFinalUrl() { return finalUrl; }
        public int getStatusCode() { return statusCode; }
        public int getAttempts() { return attempts; }
        public long getLatencyMillis() { return latencyMillis; }
        public boolean isSuccess() { return success; }
        public NetworkException.ErrorType getErrorType() { return errorType; }

        @Override
        public String toString() {
            return "JournalEntry{" +
                    "loggedAtMillis=" + loggedAtMillis +
                    ", service='" + service + '\'' +
                    ", endpoint='" + endpoint + '\'' +
                    ", traceId='" + traceId + '\'' +
                    ", finalUrl='" + finalUrl + '\'' +
                    ", statusCode=" + statusCode +
                    ", attempts=" + attempts +
                    ", latencyMillis=" + latencyMillis +
                    ", success=" + success +
                    ", errorType=" + (errorType != null ? errorType.name() : "null") +
                    '}';
        }
    }

    /**
     * Records a completed request into the in-memory journal and emits a
     * concise log line via {@link Logger}.
     *
     * @param result    the final {@link NetworkResult} for the request
     * @param errorType null for success, or a {@link NetworkException.ErrorType}
     *                  describing the terminal failure classification
     */
    public static void record(NetworkResult result, NetworkException.ErrorType errorType) {
        Objects.requireNonNull(result, "result");

        long now = System.currentTimeMillis();
        JournalEntry entry = new JournalEntry(
                now,
                result.getService(),
                result.getEndpoint(),
                result.getTraceId(),
                result.getFinalUrl(),
                result.getStatusCode(),
                result.getAttempts(),
                result.getLatencyMillis(),
                result.isSuccess(),
                errorType
        );

        synchronized (LOCK) {
            buffer[writeIndex] = entry;
            writeIndex = (writeIndex + 1) % buffer.length;
            if (size < buffer.length) {
                size++;
            }
            Logger.log(Logger.TAG.REQUEST,
                    "NetworkJournal: wrote entry service=" + result.getService() +
                            " endpoint=" + result.getEndpoint() +
                            " traceId=" + result.getTraceId() +
                            " success=" + result.isSuccess());
        }

        // Compact log line already exists:
        StringBuilder sb = new StringBuilder("[NetworkJournal] ")
                .append(result.isSuccess() ? "SUCCESS" : "FAILURE")
                .append(" | service=").append(result.getService())
                .append(" | endpoint=").append(result.getEndpoint())
                .append(" | status=").append(result.getStatusCode())
                .append(" | attempts=").append(result.getAttempts())
                .append(" | latency_ms=").append(result.getLatencyMillis())
                .append(" | traceId=").append(result.getTraceId());

        if (!result.isSuccess() && errorType != null) {
            sb.append(" | errorType=").append(errorType.name());
        }

        Logger.log(Logger.TAG.INFO, sb.toString());
    }

    /**
     * Returns a snapshot list of the most recent journal entries, ordered
     * from oldest to newest.
     *
     * <p>The returned list is detached from the internal buffer and will not
     * be affected by future writes.</p>
     */
    public static List<JournalEntry> snapshot() {
        Logger.log(Logger.TAG.DEBUG,
                "NetworkJournal: snapshot requested (size=" + size + ")");

        synchronized (LOCK) {
            if (size == 0) {
                return Collections.emptyList();
            }
            List<JournalEntry> out = new ArrayList<>(size);
            int idx = (writeIndex - size + buffer.length) % buffer.length;
            for (int i = 0; i < size; i++) {
                JournalEntry e = buffer[idx];
                if (e != null) {
                    out.add(e);
                }
                idx = (idx + 1) % buffer.length;
            }
            return Collections.unmodifiableList(out);
        }
    }

    /**
     * Clears all entries from the in-memory network journal.
     *
     * <p>Primarily for testing or admin commands. Normal operation should never
     * need to call this.</p>
     */
    public static void clear() {
        synchronized (LOCK) {
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = null;
            }
            writeIndex = 0;
            size = 0;

            Logger.log(Logger.TAG.SYSTEM, "NetworkJournal: CLEAR invoked");
        }
    }
}
