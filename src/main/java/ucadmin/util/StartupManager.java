package ucadmin.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import ucadmin.database.QueueManager;
import ucadmin.database.DatabaseManager;
import ucadmin.database.CrashHandler;

import ucadmin.exceptions.DatabaseException;
import ucadmin.exceptions.QueueException;

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
        jda.getPresence().setActivity(Activity.playing("UC Testing bot..."));
        jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.ONLINE);

        // Logger
        Logger.init();
        Logger.log(Logger.TAG.INFO, "UC Admin Bot is online as " + jda.getSelfUser().getName());

        // Bind low-level RawIO (QueueManager <-> DatabaseManager)
        QueueManager.RawIO.bindLoader(DatabaseManager::readJSONRaw);
        QueueManager.RawIO.bindWriter(DatabaseManager::writeJSONRaw);
        QueueManager.RawIO.bindMover(DatabaseManager::moveToCorrupt);
        QueueManager.RawIO.bindPatchAppender(DatabaseManager::appendJSONPatch);

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

            // Initialize higher-level deps (may write safely now)
            DependencyManager.initializeDependencies();
            Logger.log(Logger.TAG.SYSTEM, "Database initialization complete.");

        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "Dependency initialization failed: " + e.getMessage());
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR,
                    "Unexpected startup failure: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}
