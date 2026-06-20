package sora.scheduler;

import sora.exceptions.TaskException;
import sora.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads immediate console commands and routes them into the scheduler's shared
 * command executor path.
 *
 * Input format:
 * - first token = opKey
 * - remaining space-separated tokens = arguments
 * - quoted tokens preserve spaces
 *
 * Execution behavior:
 * - commands are not persisted or scheduled
 * - they are converted into the scheduler's comma-separated opArgs format
 * - they then execute immediately through TaskScheduler.executeImmediate(...)
 */
public final class SchedulerConsoleReader implements Runnable {

    private static final long POLL_INTERVAL_MS = 100L;
    private static final long SHUTDOWN_JOIN_MS = 1_000L;

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicReference<Thread> READER_THREAD = new AtomicReference<>();
    private static final AtomicReference<BufferedReader> READER = new AtomicReference<>();

    private SchedulerConsoleReader() {}

    /**
     * Starts the console reader thread once for the process.
     *
     * @return true if a new reader thread was started, false if one already exists
     */
    public static boolean start() {
        if (!STARTED.compareAndSet(false, true)) {
            return false;
        }
        RUNNING.set(true);

        Thread t = new Thread(new SchedulerConsoleReader(), "Scheduler Console");
        t.setDaemon(true);
        READER_THREAD.set(t);
        t.start();
        Logger.log(Logger.TAG.INFO, "SchedulerConsoleReader: started.");
        return true;
    }

    /**
     * Stops the console reader thread and waits briefly for it to exit.
     *
     * This mirrors scheduler lifecycle shutdown without blocking the caller on
     * a potentially stuck console close while the reader is waiting on input.
     *
     * @return true if a running reader was asked to stop, false if it was not active
     */
    public static boolean shutdown() {
        if (!STARTED.get()) {
            return false;
        }

        RUNNING.set(false);
        Thread thread = READER_THREAD.get();
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(SHUTDOWN_JOIN_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        return true;
    }

    @Override
    public void run() {
        StringBuilder pendingLine = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            READER.set(reader);
            while (RUNNING.get()) {
                boolean consumedAny = false;

                while (RUNNING.get() && reader.ready()) {
                    int next = reader.read();
                    if (next == -1) {
                        if (RUNNING.get()) {
                            Logger.log(Logger.TAG.WARN, "SchedulerConsoleReader: console input closed.");
                        }
                        return;
                    }
                    consumedAny = true;

                    char c = (char) next;
                    if (c == '\n') {
                        handleLine(pendingLine.toString());
                        pendingLine.setLength(0);
                        continue;
                    }
                    if (c != '\r') {
                        pendingLine.append(c);
                    }
                }

                if (!RUNNING.get()) {
                    break;
                }

                if (!consumedAny) {
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException ignore) {
                        // Re-check RUNNING on the next loop iteration.
                    }
                }
            }
        } catch (IOException e) {
            if (RUNNING.get()) {
                Logger.log(Logger.TAG.ERROR,
                        "SchedulerConsoleReader: console reader failed: " + e.getMessage());
                System.err.println("SchedulerConsoleReader ERROR: " + e.getMessage());
            }
        } finally {
            Logger.log(Logger.TAG.INFO, "SchedulerConsoleReader: stopped.");
            RUNNING.set(false);
            STARTED.set(false);
            READER.set(null);
            READER_THREAD.set(null);
        }
    }

    /**
     * Parses and executes one console input line.
     *
     * @param rawLine console line
     */
    private void handleLine(String rawLine) {
        if (rawLine == null) return;

        String line = rawLine.trim();
        if (line.isEmpty()) {
            return;
        }

        try {
            List<String> tokens = tokenizeConsoleLine(line);
            if (tokens.isEmpty()) {
                return;
            }

            String opKey = tokens.get(0).trim().toUpperCase(Locale.ROOT);
            List<String> args = tokens.subList(1, tokens.size());
            String opArgs = translateArgs(args);

            String result = TaskScheduler.executeImmediate(opKey, opArgs);
            String message = "SchedulerConsoleReader: command succeeded opKey=" +
                    opKey + (result == null || result.isBlank() ? "" : " result=" + result);
            Logger.log(Logger.TAG.INFO, message);
            System.out.println(message);
        } catch (TaskException | IllegalArgumentException e) {
            String message = "SchedulerConsoleReader: command failed: " + e.getMessage();
            Logger.log(Logger.TAG.ERROR, message);
            System.err.println(message);
        } catch (Exception e) {
            String message = "SchedulerConsoleReader: unexpected command failure: " + e.getMessage();
            Logger.log(Logger.TAG.ERROR, message);
            System.err.println(message);
        }
    }

    /**
     * Tokenizes a console command line by spaces while preserving quoted spans.
     *
     * @param line raw console line
     * @return ordered tokens
     */
    private static List<String> tokenizeConsoleLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (!inQuotes && Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (inQuotes) {
            throw new IllegalArgumentException("unclosed quote in console command");
        }

        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    /**
     * Translates space-tokenized console args into the scheduler's existing
     * comma-separated opArgs format.
     *
     * Args containing commas are re-quoted so TaskExecutor.Args.parse(...) sees
     * them as a single argument.
     *
     * @param args console argument tokens
     * @return comma-separated opArgs string
     */
    private static String translateArgs(List<String> args) {
        if (args == null || args.isEmpty()) {
            return "";
        }

        List<String> out = new ArrayList<>(args.size());
        for (String arg : args) {
            if (arg == null) {
                throw new IllegalArgumentException("null console arg");
            }
            if (arg.indexOf('"') >= 0) {
                throw new IllegalArgumentException("double quotes inside an argument are not supported");
            }
            if (arg.contains(",")) {
                out.add("\"" + arg + "\"");
            } else {
                out.add(arg);
            }
        }
        return String.join(",", out);
    }
}
