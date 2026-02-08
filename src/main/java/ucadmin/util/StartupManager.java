package ucadmin.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import ucadmin.database.QueueManager;
import ucadmin.database.DatabaseManager;
import ucadmin.database.CrashHandler;
import ucadmin.scheduler.TaskExecutor;
import ucadmin.scheduler.TaskScheduler;

import ucadmin.exceptions.DatabaseException;
import ucadmin.exceptions.QueueException;
import ucadmin.exceptions.TaskException;
import ucadmin.main.BotConfig;
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
 * for use. All startup events are recorded to ucadmin/LOGGER.txt.
 */
public class StartupManager extends ListenerAdapter {

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        JDA jda = event.getJDA();

        // Presence
        jda.getPresence().setActivity(Activity.playing("SORA in-dev v0.1"));
        jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.ONLINE);

        // DependencyManager must run before Logger/DB modules.
        try {
            DependencyManager.initializeDependencies();
        } catch (DatabaseException e) {
            System.err.println("Dependency initialization failed: " + e.getMessage());
            jda.getPresence().setActivity(Activity.playing("Startup blocked: deps failed"));
            jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.DO_NOT_DISTURB);
            return;
        }

        // Logger
        Logger.init();
        Logger.log(Logger.TAG.INFO, "SORA is online as " + jda.getSelfUser().getName());

        ShutdownManager.registerNoExitShutdownHook();

        // Bind low-level RawIO (QueueManager <-> DatabaseManager)
        QueueManager.RawIO.bindLoader(DatabaseManager::readJSONRaw);
        QueueManager.RawIO.bindWriter(DatabaseManager::writeJSONRaw);
        QueueManager.RawIO.bindMover(DatabaseManager::moveToCorrupt);
        QueueManager.RawIO.bindPatchAppender(DatabaseManager::appendJSONPatch);
        Logger.log(Logger.TAG.INFO, "Queue-Batch workers binded.");

        try {
            Logger.log(Logger.TAG.SYSTEM, "Starting database initialization...");
            // Bring DB online (folders, defaults, queue worker, etc.)
            DatabaseManager.initialize();

            // ---- Crash/unclean-shutdown recovery BEFORE other subsystems write ----
            try {
                CrashHandler.Result r = CrashHandler.checkAndRecover();
                if (r.recoveryTriggered) {
                    Logger.log(Logger.TAG.INFO, "Crash recovery summary: " + r);
                } else {
                    Logger.log(Logger.TAG.DEBUG, "CrashHandler: clean previous shutdown.");
                }
            } catch (DatabaseException | QueueException e) {
                // Abort further startup per policy (admin action required)
                Logger.log(Logger.TAG.ERROR, "Startup aborted by CrashHandler: " + e.getMessage());
                jda.getPresence().setActivity(Activity.playing("Startup blocked: recovery required"));
                jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.DO_NOT_DISTURB);
                return;
            }
            // ----------------------------------------------------------------------
            Logger.log(Logger.TAG.SYSTEM, "Database initialization complete.");

            updateStartupCount();

            try {
                Logger.log(Logger.TAG.SYSTEM, "Starting TaskScheduler...");
                boolean schedOk = TaskScheduler.start((opKey, opArgs) -> {
                    TaskExecutor.TaskResult result = TaskExecutor.execute(opKey, opArgs);
                    if (!result.ok) {
                        throw new TaskException("TaskExecutor failed: " + result.msg);
                    }
                    return result.msg;
                });
                Logger.log(Logger.TAG.INFO, "TaskScheduler start initiated=" + schedOk);
            } catch (TaskException e) {
                Logger.log(Logger.TAG.ERROR, "TaskScheduler failed: " + e.getMessage());
            }

            ucadmin.network.NetworkManager.start(BotConfig.netWorkerThreads);
            Logger.log(Logger.TAG.INFO, "Network manager Threads binded. Total: " + BotConfig.netWorkerThreads);

        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "Database initialization failed: " + e.getMessage());
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR,
                    "Unexpected startup failure: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private static void updateStartupCount() throws DatabaseException {
        try {
            Path path = Paths.get("database/global/bot.json").toAbsolutePath().normalize();
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
            throw e;
        } catch (Exception e) {
            throw new DatabaseException("Startup count update failed: " + e.getMessage(), e);
        }
    }
}
