package sora.util;

import sora.config.BootstrapConfig;
import sora.config.ConfigManager;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple file-based logger for Sora.
 * Adds millisecond precision, thread name, and structured formatting.
 */
public final class Logger {

    /**
     * When true, logger output is printed to console only and never written to files.
     * Useful for local testing where persistent log files are not wanted.
     */
    public static volatile boolean CONSOLE_ONLY = BootstrapConfig.CONSOLE_ONLY;

    /** Tag categories for log entries. */
    public enum TAG {
        WARN, DEBUG, ERROR, INFO, FLAG, REQUEST, SYSTEM, COMMAND, DUMP, OBJECT_REJECT, ACTION_REJECT, NULL
    }
    // DUMP - Used for corrupt data entries into DUMP.txt

    /** Logger path/format bootstrap constants plus runtime-cached tunables. */
    private static final class Config {
        static final String LOG_FILE  = BootstrapConfig.LOGPATH;
        static final String DUMP_FILE = BootstrapConfig.DUMPPATH;

        static final DateTimeFormatter FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    }

    /** Runtime-cached ignore list for normal logger tags. */
    private static volatile EnumSet<TAG> runtimeLogIgnore = EnumSet.copyOf(BootstrapConfig.LOG_IGNORE);

    /** Buffered writer size (bytes). Larger = fewer system calls. */
    private static volatile int runtimeLogBufferBytes = 256 * 1024;

    /** Max lines to keep in main log (0 = unlimited). */
    private static volatile int runtimeLogMaxLines = 200_000;

    /** Batch size per worker tick. */
    private static volatile int runtimeWorkerBatchMax = 250;

    /** Optional dump rotation guard (0 = disable). */
    private static volatile long runtimeDumpMaxBytes = 8L * 1024 * 1024;

    /** Min interval between main log rewrites when max line cap is exceeded. */
    private static volatile long runtimeLogRewriteMinIntervalMs = 250;

    /** Default wait for shutdownWait (ms). <= 0 means wait indefinitely. */
    private static volatile long runtimeShutdownWaitMs = 15_000;

    private Logger() {}

    /**
     * Refreshes logger runtime tunables from ConfigManager.
     *
     * Behavior:
     * - Reads config-backed logger values with coded-default fallback.
     * - Updates cached runtime fields used by the logger worker and helper paths.
     * - Keeps bootstrap file paths unchanged; path values are intentionally not
     *   live-updated.
     *
     * Notes:
     * - This method is safe to call before or after logger initialization.
     * - Invalid enum names in `logger.log_ignore` are skipped.
     */
    public static synchronized void applyRuntimeConfig() {
        runtimeLogIgnore = parseLogIgnore(
                ConfigManager.getStringList("logger.log_ignore", List.of("NULL")),
                EnumSet.copyOf(BootstrapConfig.LOG_IGNORE)
        );
        runtimeLogBufferBytes = Math.max(1024, ConfigManager.getInt("logger.log_buffer_bytes", 256 * 1024));
        runtimeLogMaxLines = Math.max(0, ConfigManager.getInt("logger.log_max_lines", 200_000));
        runtimeWorkerBatchMax = Math.max(1, ConfigManager.getInt("logger.worker_batch_max", 250));
        runtimeDumpMaxBytes = Math.max(0L, ConfigManager.getLong("logger.dump_max_bytes", 8L * 1024 * 1024));
        runtimeLogRewriteMinIntervalMs = ConfigManager.getLong("logger.log_rewrite_min_interval_ms", 250L);
        runtimeShutdownWaitMs = ConfigManager.getLong("logger.shutdown_wait_ms", 15_000L);
    }

    /**
     * Clears the main log, ensures dump file exists, and starts the background writer.
     * Call once at startup before heavy logging.
     */
    public static synchronized void init() {
        applyRuntimeConfig();
        if (CONSOLE_ONLY) {
            synchronized (LOG_RING) {
                LOG_RING.clear();
            }
            System.out.print(formatLine(TAG.SYSTEM, "Logger initialized in console-only mode."));
            return;
        }
        try {
            // Main log prep (clear)
            File logFile = new File(Config.LOG_FILE);
            try (FileWriter fw = new FileWriter(logFile, false)) {
                fw.write(""); // clear old log
            }
            synchronized (LOG_RING) {
                LOG_RING.clear();
            }

            // Dump file prep (do NOT clear by default; just ensure folder/file)
            File dumpFile = new File(Config.DUMP_FILE);
            if (!dumpFile.exists()) {
                try (FileWriter fw = new FileWriter(dumpFile, true)) {
                    fw.write(""); // create empty
                }
            }

            ensureWorkerStarted();
            log(TAG.SYSTEM, "Logger initialized. Previous log cleared. Dump file ready.");
        } catch (IOException e) {
            System.err.println("Logger initialization failed: " + e.getMessage());
        }
    }

    /**
     * Enqueues a line for the background writer (non-blocking, buffered).
     * DUMP-tagged lines always go to the dump file; other tags respect LOG_IGNORE.
     */
    public static synchronized void log(TAG tag, String message) {
        final TAG safeTag = (tag == null ? TAG.NULL : tag);

        // Respect ignore list for regular tags (DUMP always writes)
        if (safeTag != TAG.DUMP && runtimeLogIgnore.contains(safeTag)) return;

        final String line = formatLine(safeTag, message);

        if (CONSOLE_ONLY) {
            System.out.print(line);
            return;
        }

        if (SHUTTING_DOWN.get()) {
            System.err.print(line);
            return;
        }

        ensureWorkerStarted();

        // DUMP is always written (ignore LOG_IGNORE for forensic output)
        if (safeTag == TAG.DUMP) {
            enqueue(new LogEntry(Config.DUMP_FILE, line, /*rotateDump=*/true, /*shutdown=*/false));
            return;
        }

        enqueue(new LogEntry(Config.LOG_FILE, line, /*rotateDump=*/false, /*shutdown=*/false));
    }

    // -------------------- Optional helpers --------------------

    public static void logDump(String message) {
        log(TAG.DUMP, message);
    }

    public static void logDump(String heading, Throwable t) {
        StringWriter sw = new StringWriter();
        sw.append(heading == null ? "Exception" : heading).append(System.lineSeparator());
        if (t != null) {
            t.printStackTrace(new PrintWriter(sw));
        } else {
            sw.append("<null throwable>").append(System.lineSeparator());
        }
        log(TAG.DUMP, sw.toString());
    }

    /**
     * Returns up to the most recent log lines held in the in-memory ring buffer.
     *
     * @param maxLines maximum lines to return
     * @return snapshot of recent log lines, oldest-to-newest
     */
    public static List<String> tailSnapshot(int maxLines) {
        if (maxLines <= 0) {
            return List.of();
        }
        synchronized (LOG_RING) {
            int skip = Math.max(0, LOG_RING.size() - maxLines);
            List<String> out = new ArrayList<>(Math.min(maxLines, LOG_RING.size()));
            int index = 0;
            for (String line : LOG_RING) {
                if (index++ < skip) continue;
                out.add(line);
            }
            return List.copyOf(out);
        }
    }

    /**
     * Flushes pending log entries and stops the background writer.
     *
     * <p>After shutdown begins, new log calls are written to stderr only.</p>
     *
     * @return true if shutdown was initiated, false if it was already shutting down
     */
    public static boolean shutdown() {
        if (CONSOLE_ONLY) {
            SHUTTING_DOWN.set(true);
            return true;
        }
        if (!SHUTTING_DOWN.compareAndSet(false, true)) return false;
        if (!WORKER_STARTED.get()) {
            if (!QUEUE.isEmpty()) {
                ensureWorkerStarted();
            } else {
                return true;
            }
        }
        enqueue(POISON);
        return true;
    }

    /**
     * Initiates shutdown (if needed) and waits for the writer to finish.
     *
     * @return true if the writer stopped within the wait window
     */
    public static boolean shutdownWait() {
        return shutdownWait(runtimeShutdownWaitMs);
    }

    /**
     * Initiates shutdown (if needed) and waits for the writer to finish.
     *
     * @param timeoutMs Max time to wait in ms; <= 0 waits indefinitely.
     * @return true if the writer stopped within the wait window
     */
    public static boolean shutdownWait(long timeoutMs) {
        if (CONSOLE_ONLY) {
            SHUTTING_DOWN.set(true);
            return true;
        }
        SHUTTING_DOWN.compareAndSet(false, true);
        if (!WORKER_STARTED.get() && !QUEUE.isEmpty()) {
            ensureWorkerStarted();
        }
        enqueue(POISON);

        CountDownLatch latch = WORKER_STOP_LATCH.get();
        if (latch == null) return true;
        try {
            if (timeoutMs <= 0) {
                latch.await();
                return true;
            }
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // -------------------- Internal IO --------------------

    private static final BlockingQueue<LogEntry> QUEUE = new LinkedBlockingQueue<>();
    private static final AtomicBoolean WORKER_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);
    private static final AtomicReference<Thread> WORKER_THREAD = new AtomicReference<>();
    private static final AtomicReference<CountDownLatch> WORKER_STOP_LATCH = new AtomicReference<>();
    private static final ArrayDeque<String> LOG_RING = new ArrayDeque<>();
    private static final LogEntry POISON = new LogEntry(null, null, false, true);
    private static volatile long lastRewriteNanos = 0L;

    private static final class LogEntry {
        final String filePath;
        final String line;
        final boolean rotateDump;
        final boolean shutdown;
        LogEntry(String filePath, String line, boolean rotateDump, boolean shutdown) {
            this.filePath = filePath;
            this.line = line;
            this.rotateDump = rotateDump;
            this.shutdown = shutdown;
        }
    }

    private static void enqueue(LogEntry entry) {
        if (entry == null) return;
        QUEUE.offer(entry);
    }

    private static void ensureWorkerStarted() {
        if (!WORKER_STARTED.compareAndSet(false, true)) return;
        CountDownLatch latch = new CountDownLatch(1);
        WORKER_STOP_LATCH.set(latch);
        Thread t = new Thread(() -> workerLoop(latch), "Logger");
        t.setDaemon(true);
        WORKER_THREAD.set(t);
        t.start();
    }

    private static void workerLoop(CountDownLatch stoppedLatch) {
        BufferedWriter logWriter = null;
        BufferedWriter dumpWriter = null;
        try {
            logWriter = openWriter(Config.LOG_FILE, /*append=*/true);
            dumpWriter = openWriter(Config.DUMP_FILE, /*append=*/true);
            while (true) {
                LogEntry first = QUEUE.take();
                if (first.shutdown) {
                    drainAndFlush(logWriter, dumpWriter);
                    break;
                }
                BufferedWriter[] writers = handleEntry(first, logWriter, dumpWriter);
                logWriter = writers[0];
                dumpWriter = writers[1];
                int drained = 0;
                LogEntry next;
                while (drained < runtimeWorkerBatchMax && (next = QUEUE.poll()) != null) {
                    if (next.shutdown) {
                        drainAndFlush(logWriter, dumpWriter);
                        return;
                    }
                    writers = handleEntry(next, logWriter, dumpWriter);
                    logWriter = writers[0];
                    dumpWriter = writers[1];
                    drained++;
                }
                safeFlush(logWriter);
                safeFlush(dumpWriter);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            System.err.println("Logger worker failed: " + t.getMessage());
        } finally {
            String stoppedLine = formatLine(TAG.INFO, "Logger worker stopped.");
            if (CONSOLE_ONLY) {
                System.out.print(stoppedLine);
            } else if (logWriter != null) {
                try {
                    logWriter.write(stoppedLine);
                    logWriter.flush();
                } catch (IOException ignore) {
                }
            } else {
                System.err.print(stoppedLine);
            }
            safeClose(logWriter);
            safeClose(dumpWriter);
            WORKER_THREAD.set(null);
            WORKER_STARTED.set(false);
            if (stoppedLatch != null) {
                stoppedLatch.countDown();
            }
        }
    }

    private static BufferedWriter[] handleEntry(LogEntry entry, BufferedWriter logWriter, BufferedWriter dumpWriter) {
        if (entry == null || entry.filePath == null || entry.line == null) {
            return new BufferedWriter[] { logWriter, dumpWriter };
        }

        try {
            if (Config.LOG_FILE.equals(entry.filePath)) {
                boolean overflowed = false;
                if (runtimeLogMaxLines > 0) {
                    synchronized (LOG_RING) {
                        LOG_RING.addLast(entry.line);
                        while (LOG_RING.size() > runtimeLogMaxLines) {
                            LOG_RING.removeFirst();
                            overflowed = true;
                        }
                    }
                }
                if (logWriter == null) {
                    logWriter = openWriter(Config.LOG_FILE, /*append=*/true);
                }
                logWriter.write(entry.line);
                if (overflowed && shouldRewriteMainLog()) {
                    logWriter = rewriteMainLog(logWriter);
                }
            } else {
                if (entry.rotateDump) {
                    try {
                        if (runtimeDumpMaxBytes > 0) {
                            dumpWriter = rotateDumpIfTooLarge(entry.filePath, dumpWriter);
                        }
                    } catch (Throwable rot) {
                        System.err.println("Dump rotation warning: " + rot.getMessage());
                    }
                }
                if (dumpWriter == null) {
                    dumpWriter = openWriter(entry.filePath, /*append=*/true);
                }
                dumpWriter.write(entry.line);
            }
        } catch (IOException e) {
            System.err.println("Failed to write to log file (" + entry.filePath + "): " + e.getMessage());
        }

        return new BufferedWriter[] { logWriter, dumpWriter };
    }

    private static BufferedWriter openWriter(String filePath, boolean append) throws IOException {
        return new BufferedWriter(new FileWriter(filePath, append), runtimeLogBufferBytes);
    }

    private static BufferedWriter rewriteMainLog(BufferedWriter current) {
        safeClose(current);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Config.LOG_FILE, false), runtimeLogBufferBytes)) {
            synchronized (LOG_RING) {
                for (String line : LOG_RING) {
                    writer.write(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Logger rewrite failed: " + e.getMessage());
        }
        try {
            return openWriter(Config.LOG_FILE, /*append=*/true);
        } catch (IOException e) {
            System.err.println("Logger reopen failed: " + e.getMessage());
            return null;
        }
    }

    private static BufferedWriter rotateDumpIfTooLarge(String filePath, BufferedWriter current) throws IOException {
        File f = new File(filePath);
        if (!f.exists()) return current;
        if (f.length() < runtimeDumpMaxBytes) return current;

        File rotated = new File(filePath + "." +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".old");

        safeClose(current);

        // Best-effort rename; if this fails we keep appending to current file.
        if (!f.renameTo(rotated)) {
            // Try copy+truncate as fallback
            try (FileInputStream in = new FileInputStream(f);
                 FileOutputStream out = new FileOutputStream(rotated)) {
                in.transferTo(out);
            }
            try (FileWriter trunc = new FileWriter(f, false)) {
                trunc.write("");
            }
        }
        return openWriter(filePath, /*append=*/true);
    }

    private static boolean shouldRewriteMainLog() {
        if (runtimeLogRewriteMinIntervalMs <= 0) return true;
        long now = System.nanoTime();
        long minDelta = TimeUnit.MILLISECONDS.toNanos(runtimeLogRewriteMinIntervalMs);
        if (now - lastRewriteNanos < minDelta) return false;
        lastRewriteNanos = now;
        return true;
    }

    private static String formatLine(TAG tag, String message) {
        final String timestamp = LocalDateTime.now().format(Config.FORMATTER);
        final String thread = Thread.currentThread().getName();
        final String msg = String.valueOf(message);
        return String.format("[%s] [%s] [%s] %s%n", timestamp, thread, tag, msg);
    }

    private static void drainAndFlush(BufferedWriter logWriter, BufferedWriter dumpWriter) {
        LogEntry next;
        while ((next = QUEUE.poll()) != null) {
            if (next.shutdown) continue;
            BufferedWriter[] writers = handleEntry(next, logWriter, dumpWriter);
            logWriter = writers[0];
            dumpWriter = writers[1];
        }
        safeFlush(logWriter);
        safeFlush(dumpWriter);
    }

    private static void safeFlush(BufferedWriter writer) {
        if (writer == null) return;
        try {
            writer.flush();
        } catch (IOException ignore) {
        }
    }

    private static void safeClose(BufferedWriter writer) {
        if (writer == null) return;
        try {
            writer.flush();
        } catch (IOException ignore) {
        }
        try {
            writer.close();
        } catch (IOException ignore) {
        }
    }

    /**
     * Parses logger ignore tag names into an EnumSet.
     *
     * Invalid names are skipped so malformed config entries do not break logger startup.
     *
     * @param names configured tag names
     * @param fallback fallback ignore set when parsing yields no valid tags
     * @return parsed ignore set
     */
    private static EnumSet<TAG> parseLogIgnore(List<String> names, EnumSet<TAG> fallback) {
        EnumSet<TAG> parsed = EnumSet.noneOf(TAG.class);
        if (names != null) {
            for (String raw : names) {
                if (raw == null || raw.isBlank()) continue;
                try {
                    parsed.add(TAG.valueOf(raw.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // Ignore invalid names; fallback is applied if nothing valid remains.
                }
            }
        }
        return parsed.isEmpty() ? EnumSet.copyOf(fallback) : parsed;
    }
}
