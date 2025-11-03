package ucadmin.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ucadmin.database.QueueManager;
import ucadmin.exceptions.DatabaseException;
import ucadmin.database.DatabaseManager;

/**
 * Handles all startup-time initialization once the JDA session is ready.
 *
 * StartupManager sets the bot’s activity and status, initializes the
 * logging system, and ensures that the database environment is ready
 * for use. All startup events are recorded to ucadmin/LOGGER.txt.
 */
public class StartupManager extends ListenerAdapter {

    /**
     * Executes when JDA signals that the bot is fully connected and ready.
     *
     * Initializes presence, starts the Logger, and prepares all database
     * dependencies. Any DatabaseException thrown during this process is
     * automatically logged.
     *
     * @param event the JDA ReadyEvent fired when the bot connects
     */
    @Override
    public void onReady(@NotNull ReadyEvent event) {
        JDA jda = event.getJDA();

        // Set visible activity and status
        jda.getPresence().setActivity(Activity.playing("UC Testing bot..."));
        jda.getPresence().setStatus(net.dv8tion.jda.api.OnlineStatus.ONLINE);

        // Initialize logger and announce startup
        Logger.init();
        Logger.log(Logger.TAG.INFO,
                "UC Admin Bot is online as " + jda.getSelfUser().getName());

        // Binds low-level queue methods
        QueueManager.RawIO.bindLoader(DatabaseManager::readJSONRaw);
        QueueManager.RawIO.bindWriter(DatabaseManager::writeJSONRaw);
        QueueManager.RawIO.bindMover(DatabaseManager::moveToCorrupt);
        QueueManager.RawIO.bindPatchAppender(DatabaseManager::appendJSONPatch); // NEW

        // Initialize database systems
        try {
            Logger.log(Logger.TAG.SYSTEM, "Starting database initialization...");
            DatabaseManager.initialize();
            DependencyManager.initializeDependencies();
            Logger.log(Logger.TAG.SYSTEM, "Database initialization complete.");
        } catch (DatabaseException e) {
            // DatabaseException already logs itself, but we’ll also capture context
            Logger.log(Logger.TAG.ERROR,
                    "Dependency initialization failed: " + e.getMessage());
        } catch (Exception e) {
            // Catch any unexpected startup failures
            Logger.log(Logger.TAG.ERROR,
                    "Unexpected startup failure: " + e.getClass().getSimpleName()
                            + " - " + e.getMessage());
        }
    }
}
