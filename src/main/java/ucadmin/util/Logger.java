package ucadmin.util;

import ucadmin.main.BotConfig;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;

/**
 * Simple file-based logger for UC Admin Bot.
 * Adds millisecond precision, thread name, and structured formatting.
 */
public final class Logger {

    /** Tag categories for log entries. */
    public enum TAG {
        WARN, DEBUG, ERROR, INFO, FLAG, REQUEST, SYSTEM, COMMAND, DUMP, NULL
    }
    // DUMP - Used for corrupt data entries into DUMP.txt

    /** Immutable configuration constants for the logger. */
    private static final class Config {
        static final String LOG_FILE  = BotConfig.LOGPATH;
        static final String DUMP_FILE = BotConfig.DUMPPATH;

        static final EnumSet<TAG> LOG_IGNORE = BotConfig.LOG_IGNORE;
        static final DateTimeFormatter FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        // Optional: very light rotation guard for DUMP
        static final long DUMP_MAX_BYTES = 8L * 1024 * 1024; // 8 MiB; 0 = disable
    }

    private Logger() {}

    /** Clears the main log and ensures dump file exists. */
    public static synchronized void init() {
        try {
            // Main log prep (clear)
            File logFile = new File(Config.LOG_FILE);
            File parent = logFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter fw = new FileWriter(logFile, false)) {
                fw.write(""); // clear old log
            }

            // Dump file prep (do NOT clear by default; just ensure folder/file)
            File dumpFile = new File(Config.DUMP_FILE);
            File dumpParent = dumpFile.getParentFile();
            if (dumpParent != null && !dumpParent.exists()) dumpParent.mkdirs();
            if (!dumpFile.exists()) {
                try (FileWriter fw = new FileWriter(dumpFile, true)) {
                    fw.write(""); // create empty
                }
            }

            log(TAG.SYSTEM, "Logger initialized. Previous log cleared. Dump file ready.");
        } catch (IOException e) {
            System.err.println("Logger initialization failed: " + e.getMessage());
        }
    }

    /** Writes a line to the appropriate log. DUMP-tagged lines are routed to DUMP.txt. */
    public static synchronized void log(TAG tag, String message) {
        final String timestamp = LocalDateTime.now().format(Config.FORMATTER);
        final String thread = Thread.currentThread().getName();
        final String line = String.format("[%s] [%s] [%s] %s%n", timestamp, thread, tag, message);

        // DUMP is always written (ignore LOG_IGNORE for forensic output)
        if (tag == TAG.DUMP) {
            writeLineSafely(Config.DUMP_FILE, line, /*rotateIfNeeded=*/true);
            return;
        }

        // Respect ignore list for regular tags
        if (Config.LOG_IGNORE.contains(tag)) return;

        writeLineSafely(Config.LOG_FILE, line, /*rotateIfNeeded=*/false);
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

    // -------------------- Internal IO --------------------

    private static void writeLineSafely(String filePath, String line, boolean rotateDump) {
        try {
            if (rotateDump && Config.DUMP_MAX_BYTES > 0) {
                rotateDumpIfTooLarge(filePath);
            }
        } catch (Throwable rot) {
            // Rotation issues should never block logging; fall through to append anyway.
            System.err.println("Dump rotation warning: " + rot.getMessage());
        }

        try (FileWriter writer = new FileWriter(filePath, true)) {
            writer.write(line);
        } catch (IOException e) {
            System.err.println("Failed to write to log file (" + filePath + "): " + e.getMessage());
        }
    }

    private static void rotateDumpIfTooLarge(String filePath) throws IOException {
        File f = new File(filePath);
        if (!f.exists()) return;
        if (f.length() < Config.DUMP_MAX_BYTES) return;

        File rotated = new File(filePath + "." +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".old");

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
    }
}
