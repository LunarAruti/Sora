package ucadmin.util;

import ucadmin.main.BotConfig;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple file-based logger for UC Admin Bot.
 * Adds millisecond precision, thread name, and structured formatting.
 */
public final class Logger {

    /**
     * When true, logger output is printed to console only and never written to files.
     * Useful for local testing where persistent log files are not wanted.
     */
    public static volatile boolean CONSOLE_ONLY = BotConfig.CONSOLE_ONLY;

    /** Tag categories for log entries. */
    public enum TAG {
        WARN, DEBUG, ERROR, INFO, FLAG, REQUEST, SYSTEM, COMMAND, DUMP, OBJECT_REJECT, ACTION_REJECT, NULL
    }
    // DUMP - Used for corrupt data entries into DUMP.txt

    /** Immutable configuration constants for the logger. */
    private static final class Config {
        static final String LOG_FILE  = BotConfig.LOGPATH;
        static final String DUMP_FILE = BotConfig.DUMPPATH;

        static final EnumSet<TAG> LOG_IGNORE = BotConfig.LOG_IGNORE;
        static final DateTimeFormatter FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        // Buffered writer size (bytes). Larger = fewer system calls.
        static final int LOG_BUFFER_BYTES = 256 * 1024;

        // Max lines to keep in main log (0 = unlimited).
        static final int LOG_MAX_LINES = 200_000;

        // Batch size per worker tick.
        static final int WORKER_BATCH_MAX = 250;

        // Optional: very light rotation guard for DUMP
        static final long DUMP_MAX_BYTES = 8L * 1024 * 1024; // 8 MiB; 0 = disable

        // Min interval between main log rewrites when max line cap is exceeded.
        static final long LOG_REWRITE_MIN_INTERVAL_MS = 250;

        // Default wait for shutdownWait (ms). <= 0 means wait indefinitely.
        static final long SHUTDOWN_WAIT_MS = 15_000;
    }

    private Logger() {}

    /**
     * Clears the main log, ensures dump file exists, and starts the background writer.
     * Call once at startup before heavy logging.
     */
    public static synchronized void init() {
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
        if (safeTag != TAG.DUMP && Config.LOG_IGNORE.contains(safeTag)) return;

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
        return shutdownWait(Config.SHUTDOWN_WAIT_MS);
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
        Thread t = new Thread(() -> workerLoop(latch), "UC-Logger");
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
                while (drained < Config.WORKER_BATCH_MAX && (next = QUEUE.poll()) != null) {
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
                if (Config.LOG_MAX_LINES > 0) {
                    synchronized (LOG_RING) {
                        LOG_RING.addLast(entry.line);
                        while (LOG_RING.size() > Config.LOG_MAX_LINES) {
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
                        if (Config.DUMP_MAX_BYTES > 0) {
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
        return new BufferedWriter(new FileWriter(filePath, append), Config.LOG_BUFFER_BYTES);
    }

    private static BufferedWriter rewriteMainLog(BufferedWriter current) {
        safeClose(current);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Config.LOG_FILE, false), Config.LOG_BUFFER_BYTES)) {
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
        if (f.length() < Config.DUMP_MAX_BYTES) return current;

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
        if (Config.LOG_REWRITE_MIN_INTERVAL_MS <= 0) return true;
        long now = System.nanoTime();
        long minDelta = TimeUnit.MILLISECONDS.toNanos(Config.LOG_REWRITE_MIN_INTERVAL_MS);
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
}
