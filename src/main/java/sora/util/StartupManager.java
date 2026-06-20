package sora.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import sora.database.QueueManager;
import sora.database.DatabaseManager;
import sora.database.CrashHandler;
import sora.scheduler.TaskExecutor;
import sora.scheduler.SchedulerConsoleReader;
import sora.scheduler.TaskScheduler;

import sora.exceptions.DatabaseException;
import sora.exceptions.QueueException;
import sora.exceptions.TaskException;
import sora.config.ConfigManager;
import sora.config.BootstrapConfig;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Handles all startup-time initialization once the JDA session is ready.
 *
 * StartupManager sets the bot’s activity and status, initializes the
 * logging system, and ensures that the database environment is ready
 * for use. All startup events are recorded to sora/LOGGER.txt.
 */
public class StartupManager extends ListenerAdapter {

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        startCore(event.getJDA());
    }

    /**
     * Boots the non-Discord runtime: dependencies, logger, DBM, crash recovery,
     * scheduler, and network workers. When a JDA instance is provided, Discord
     * presence and identity logging are also updated.
     *
     * @param jda active JDA instance, or null for headless startup
     * @return true if startup completed, false if startup was blocked or aborted
     */
    public static boolean startCore(JDA jda) {
        final boolean discordMode = (jda != null);

        if (discordMode) {
            jda.getPresence().setActivity(Activity.playing("SORA in-dev v0.1"));
            jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.ONLINE);
        }

        try {
            DependencyManager.initializeDependencies();
        } catch (DatabaseException e) {
            System.err.println("[0004] Dependency initialization failed: " + e.getMessage());
            if (discordMode) {
                setBlockedPresence(jda, "Startup blocked: deps failed");
            }
            return false;
        }

        ConfigManager.initialize();

        Logger.init();
        QueueManager.applyRuntimeConfig();
        Logger.log(
                Logger.TAG.SYSTEM,
                "StartupManager: core startup begin mode=" + (discordMode ? "discord" : "headless")
        );

        if (discordMode) {
            Logger.log(Logger.TAG.INFO, "SORA is online as " + jda.getSelfUser().getName());
        } else {
            Logger.log(Logger.TAG.INFO, "StartupManager: headless mode active; Discord startup skipped.");
        }

        ShutdownManager.registerNoExitShutdownHook();

        Logger.log(Logger.TAG.SYSTEM, "StartupManager: binding QueueManager raw I/O delegates...");
        QueueManager.RawIO.bindLoader(DatabaseManager::readJSONRaw);
        QueueManager.RawIO.bindWriter(DatabaseManager::writeJSONRaw);
        QueueManager.RawIO.bindMover(DatabaseManager::moveToCorrupt);
        QueueManager.RawIO.bindPatchAppender(DatabaseManager::appendJSONPatch);
        QueueManager.RawIO.bindMaterializer((path, snapshot, verify, deleteJournal) -> {
            DatabaseManager.writeJSONRaw(path, snapshot);
            if (verify) {
                DatabaseManager.readJSONRaw(path);
            }
            if (deleteJournal) {
                Files.deleteIfExists(Path.of(path + ".patch"));
            }
        });
        Logger.log(Logger.TAG.INFO, "StartupManager: QueueManager raw I/O delegates bound.");

        try {
            Logger.log(Logger.TAG.SYSTEM, "StartupManager: starting database initialization...");
            DatabaseManager.initialize();

            try {
                CrashHandler.Result r = CrashHandler.checkAndRecover();
                if (r.recoveryTriggered) {
                    Logger.log(Logger.TAG.INFO, "Crash recovery summary: " + r);
                } else {
                    Logger.log(Logger.TAG.DEBUG, "CrashHandler: clean previous shutdown.");
                }
            } catch (DatabaseException | QueueException e) {
                Logger.log(
                        Logger.TAG.ERROR,
                        "[0005] Startup aborted by CrashHandler: " + e.getMessage()
                );
                if (discordMode) {
                    setBlockedPresence(jda, "Startup blocked: recovery required");
                }
                return false;
            }

            Logger.log(Logger.TAG.SYSTEM, "StartupManager: database initialization complete.");

            updateStartupCount();

            try {
                Logger.log(Logger.TAG.SYSTEM, "StartupManager: starting TaskScheduler...");
                boolean schedOk = TaskScheduler.start((opKey, opArgs) -> {
                    TaskExecutor.TaskResult result = TaskExecutor.execute(opKey, opArgs);
                    if (!result.ok) {
                        throw new TaskException("TaskExecutor failed: " + result.msg);
                    }
                    return result.msg;
                });
                Logger.log(Logger.TAG.INFO, "TaskScheduler start initiated=" + schedOk);
                if (schedOk) {
                    boolean consoleOk = SchedulerConsoleReader.start();
                    Logger.log(Logger.TAG.INFO,
                            "StartupManager: SchedulerConsoleReader start initiated=" + consoleOk);
                }
            } catch (TaskException e) {
                Logger.log(
                        Logger.TAG.ERROR,
                        "[0007] TaskScheduler failed: " + e.getMessage()
                );
            }

            Logger.log(Logger.TAG.SYSTEM, "StartupManager: starting NetworkManager workers...");
            boolean networkOk = sora.network.NetworkManager.start();
            Logger.log(Logger.TAG.INFO, "StartupManager: NetworkManager start initiated=" + networkOk);
            Logger.log(Logger.TAG.SYSTEM, "StartupManager: core startup complete.");
            return true;

        } catch (DatabaseException e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0008] Database initialization failed: " + e.getMessage()
            );
            return false;
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0009] Unexpected startup failure: " + e.getClass().getSimpleName() + " - " + e.getMessage()
            );
            return false;
        }
    }

    private static void updateStartupCount() throws DatabaseException {
        try {
            Path path = Paths.get(BootstrapConfig.GLOBALPATH, "bot.json").toAbsolutePath().normalize();
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new DatabaseException("Startup count update failed: missing bot.json at " + path);
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            JSONObject data = (content == null || content.isBlank()) ? new JSONObject() : new JSONObject(content);

            int currentCount = 0;
            Object countObj = data.opt("startup_count");
            if (countObj instanceof Number) {
                currentCount = ((Number) countObj).intValue();
            }

            data.put("startup_count", currentCount + 1);
            data.put("last_updated", Instant.now().getEpochSecond());

            Files.writeString(
                    path,
                    data.toString(2),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            Logger.log(Logger.TAG.INFO, "Updated startup count: " + (currentCount + 1));
        } catch (DatabaseException e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0006] Startup count update failed: " + e.getMessage()
            );
            throw e;
        } catch (Exception e) {
            Logger.log(
                    Logger.TAG.ERROR,
                    "[0006] Startup count update failed: " + e.getMessage()
            );
            throw new DatabaseException("Startup count update failed: " + e.getMessage(), e);
        }
    }

    private static void setBlockedPresence(JDA jda, String activityText) {
        if (jda == null) return;
        jda.getPresence().setActivity(Activity.playing(activityText));
        jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.DO_NOT_DISTURB);
    }
}
