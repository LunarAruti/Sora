package sora.util;

import net.dv8tion.jda.api.JDA;
import sora.database.BatchManager;
import sora.database.QueueManager;
import sora.network.NetworkManager;
import sora.scheduler.TaskScheduler;
import sora.config.ConfigManager;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralized shutdown coordinator for Sora.
 *
 * Runs a clean, ordered shutdown sequence across modules and
 * closes the logger last.
 */
public final class ShutdownManager {

    private ShutdownManager() {}

    private static final AtomicBoolean NO_EXIT_SHUTTING_DOWN = new AtomicBoolean(false);
    private static final AtomicBoolean CLEAN_SHUTTING_DOWN = new AtomicBoolean(false);
    private static final long DEFAULT_NO_EXIT_LOGGER_WAIT_MS = 2000L;

    /**
     * Performs a full shutdown sequence and exits the process.
     *
     * @param jda active JDA instance (may be null)
     */
    public static void shutdown(JDA jda) {
        if (!CLEAN_SHUTTING_DOWN.compareAndSet(false, true)) {
            Logger.log(
                    Logger.TAG.WARN,
                    "[0013] Shutdown ignored -> already shutting down."
            );
            return;
        }
        if (NO_EXIT_SHUTTING_DOWN.get()) {
            Logger.log(
                    Logger.TAG.WARN,
                    "[0014] Shutdown ignored -> no-exit shutdown already running."
            );
            return;
        }
        Logger.log(Logger.TAG.SYSTEM, "Shutdown command received. Beginning graceful termination...");

        try {
            Logger.log(Logger.TAG.SYSTEM, "Shutdown: stopping NetworkManager (full)...");
            boolean netOk = NetworkManager.shutdown();
            Logger.log(Logger.TAG.INFO, "Shutdown: NetworkManager shutdown initiated=" + netOk);
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0015] Shutdown: NetworkManager failed: " + e.getMessage()
            );
        }

        try {
            Logger.log(Logger.TAG.SYSTEM, "Shutdown: stopping TaskScheduler...");
            boolean schedOk = TaskScheduler.shutdown();
            Logger.log(Logger.TAG.INFO, "Shutdown: TaskScheduler shutdown initiated=" + schedOk);
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0016] Shutdown: TaskScheduler failed: " + e.getMessage()
            );
        }

        try {
            Logger.log(Logger.TAG.SYSTEM, "Shutdown: flushing BatchManager...");
            BatchManager.shutdown();
            Logger.log(Logger.TAG.INFO, "Shutdown: BatchManager flush complete.");
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0017] Shutdown: BatchManager failed: " + e.getMessage()
            );
        }

        try {
            Logger.log(Logger.TAG.SYSTEM, "Shutdown: stopping QueueManager (no flush)...");
            boolean qOk = QueueManager.shutdown(false);
            Logger.log(Logger.TAG.INFO, "Shutdown: QueueManager shutdown initiated=" + qOk);
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0018] Shutdown: QueueManager failed: " + e.getMessage()
            );
        }

        try {
            Logger.log(Logger.TAG.INFO, "Shutdown: graceful steps complete. Entering grace wait (2000 ms).");
            Thread.sleep(2000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Logger.log(
                    Logger.TAG.WARN,
                    "[0019] Shutdown interrupted during grace period."
            );
        }

        if (jda != null) {
            try {
                Logger.log(Logger.TAG.SYSTEM, "Shutdown: shutting down JDA...");
                jda.shutdown();
                Thread.sleep(2000);
                Logger.log(Logger.TAG.INFO, "Shutdown: JDA shutdown complete.");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                Logger.log(
                        Logger.TAG.WARN,
                        "[0020] Shutdown interrupted during JDA shutdown wait."
                );
            } catch (Exception e) {
                Logger.log(
                        Logger.TAG.ERROR,
                        "[0021] Shutdown: JDA shutdown failed: " + e.getMessage()
                );
            }
        } else {
            Logger.log(Logger.TAG.DEBUG, "Shutdown: JDA instance was null; skipping JDA shutdown.");
        }

        Logger.log(Logger.TAG.SYSTEM, "System exiting cleanly.");
        Logger.log(Logger.TAG.INFO, "Shutdown: logger shutdown wait begin.");
        Logger.shutdownWait();
        System.exit(0);
    }

    /**
     * Registers a JVM shutdown hook that performs a no-exit shutdown sequence.
     * Call once during startup.
     */
    public static void registerNoExitShutdownHook() {
        try {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> shutdownNoExit(null), "Shutdown Hook")
            );
            Logger.log(Logger.TAG.INFO, "ShutdownNoExit: JVM shutdown hook registered.");
        } catch (RuntimeException e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0003] ShutdownNoExit: JVM shutdown hook registration failed: " + e.getMessage()
            );
        }
    }

    /**
     * Performs a no-exit shutdown sequence (minimum clean shutdown).
     *
     * @param jda active JDA instance (may be null)
     */
    public static void shutdownNoExit(JDA jda) {
        if (CLEAN_SHUTTING_DOWN.get()) {
            Logger.log(
                    Logger.TAG.WARN,
                    "[0022] ShutdownNoExit skipped -> clean shutdown in progress."
            );
            return;
        }
        if (!NO_EXIT_SHUTTING_DOWN.compareAndSet(false, true)) {
            Logger.log(
                    Logger.TAG.WARN,
                    "[0023] ShutdownNoExit ignored -> already shutting down."
            );
            return;
        }

        Logger.log(Logger.TAG.SYSTEM, "ShutdownNoExit: beginning minimum clean shutdown...");

        try {
            Logger.log(Logger.TAG.SYSTEM, "ShutdownNoExit: stopping NetworkManager (fast)...");
            boolean netOk = NetworkManager.shutdown(false);
            Logger.log(Logger.TAG.INFO, "ShutdownNoExit: NetworkManager shutdown initiated=" + netOk);
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0024] ShutdownNoExit: NetworkManager failed: " + e.getMessage()
            );
        }

        try {
            Logger.log(Logger.TAG.SYSTEM, "ShutdownNoExit: stopping TaskScheduler (fast)...");
            boolean schedOk = TaskScheduler.shutdownNoExit();
            Logger.log(Logger.TAG.INFO, "ShutdownNoExit: TaskScheduler shutdown initiated=" + schedOk);
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0025] ShutdownNoExit: TaskScheduler failed: " + e.getMessage()
            );
        }

        try {
            Logger.log(Logger.TAG.SYSTEM, "ShutdownNoExit: QueueManager flushAll(false) begin...");
            QueueManager.flushAll(false);
            Logger.log(Logger.TAG.INFO, "ShutdownNoExit: QueueManager flushAll(false) complete.");
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0026] ShutdownNoExit: QueueManager flushAll(false) failed: " + e.getMessage()
            );
        }

        try {
            Logger.log(Logger.TAG.SYSTEM, "ShutdownNoExit: stopping QueueManager (no flush)...");
            boolean qOk = QueueManager.shutdown(false);
            Logger.log(Logger.TAG.INFO, "ShutdownNoExit: QueueManager shutdown initiated=" + qOk);
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0027] ShutdownNoExit: QueueManager failed: " + e.getMessage()
            );
        }

        if (jda != null) {
            try {
                Logger.log(Logger.TAG.SYSTEM, "ShutdownNoExit: shutting down JDA...");
                jda.shutdown();
                Logger.log(Logger.TAG.INFO, "ShutdownNoExit: JDA shutdown complete.");
            } catch (Exception e) {
                Logger.log(
                        Logger.TAG.ERROR,
                        "[0028] ShutdownNoExit: JDA shutdown failed: " + e.getMessage()
                );
            }
        } else {
            Logger.log(Logger.TAG.DEBUG, "ShutdownNoExit: JDA instance was null; skipping JDA shutdown.");
        }

        Logger.log(Logger.TAG.INFO, "ShutdownNoExit: minimum clean shutdown steps completed.");
        long loggerWaitMs = noExitLoggerWaitMs();
        Logger.log(Logger.TAG.INFO, "ShutdownNoExit: logger shutdown wait begin (" + loggerWaitMs + " ms).");
        Logger.shutdownWait(loggerWaitMs);
    }

    /** Returns the current no-exit logger wait duration. */
    private static long noExitLoggerWaitMs() {
        return Math.max(0L, ConfigManager.getLong("shutdown_manager.no_exit_logger_wait_ms", DEFAULT_NO_EXIT_LOGGER_WAIT_MS));
    }
}
