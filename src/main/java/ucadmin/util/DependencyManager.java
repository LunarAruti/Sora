package ucadmin.util;

import org.json.JSONObject;
import ucadmin.database.DatabaseManager;
import ucadmin.exceptions.DatabaseException;
import ucadmin.main.BotConfig;

import java.time.Instant;

/**
 * Handles essential database setup before the bot starts.
 *
 * DependencyManager ensures that all required directories and
 * baseline files exist, and initializes global metadata such
 * as bot.json. This class runs once at startup from
 * StartupManager.onReady().
 */
public class DependencyManager {

    /** Directory paths for all database categories. */
    private static final String GLOBAL_DIR = "database/global";
    private static final String SERVER_DIR = "database/server";
    private static final String USER_DIR   = "database/user";

    /** Path to the global bot metadata file. */
    private static final String BOT_FILE = GLOBAL_DIR + "/bot.json";

    /**
     * Runs all startup checks and creates any missing folders or files.
     * Called once on bot startup.
     *
     * @throws DatabaseException if a folder or file creation fails
     */
    public static void initializeDependencies() throws DatabaseException {
        Logger.log(Logger.TAG.SYSTEM, "DependencyManager: initializing database dependencies...");

        try {
            // === BASE FOLDER STRUCTURE ===
            DatabaseManager.createFolder("database");
            DatabaseManager.createFolder(GLOBAL_DIR);
            DatabaseManager.createFolder(SERVER_DIR);
            DatabaseManager.createFolder(USER_DIR);
            Logger.log(Logger.TAG.DEBUG, "Verified base folder structure.");

            // === BOT FILE SETUP ===
            if (!DatabaseManager.fileExists(BOT_FILE)) {
                createDefaultBotFile();
                Logger.log(Logger.TAG.INFO, "Created new bot.json with default values.");
            } else {
                Logger.log(Logger.TAG.DEBUG, "bot.json already exists. Skipping creation.");
            }

            // === ENSURE CORRUPT + LOG PATHS EXIST ===
            String corruptPath = ucadmin.main.BotConfig.CORRUPTPATH;
            String logPath = ucadmin.main.BotConfig.LOGPATH;
            String dumpPath = ucadmin.main.BotConfig.DUMPPATH;

            Logger.log(Logger.TAG.DEBUG, "Using configured paths from BotConfig:");
            Logger.log(Logger.TAG.DEBUG, "CORRUPTPATH=" + corruptPath);
            Logger.log(Logger.TAG.DEBUG, "LOGPATH=" + logPath);
            Logger.log(Logger.TAG.DEBUG, "DUMPPATH=" + dumpPath);

            // Ensure parent directories exist
            String corruptDir = new java.io.File(corruptPath).getParent();
            if (corruptDir != null) DatabaseManager.createFolder(corruptDir);

            String logDir = new java.io.File(logPath).getParent();
            if (logDir != null) DatabaseManager.createFolder(logDir);

            String dumpDir = new java.io.File(dumpPath).getParent();
            if (dumpDir != null) DatabaseManager.createFolder(dumpDir);

            // === ENSURE GLOBAL LOG FILE EXISTS ===
            if (!DatabaseManager.fileExists(logPath)) {
                DatabaseManager.createFile(logPath);
                Logger.log(Logger.TAG.INFO, "Created main log file: " + logPath);
            }

            // === UPDATE STARTUP COUNT ===
            updateStartupCount();

            Logger.log(Logger.TAG.SYSTEM, "DependencyManager initialization complete.");

        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "DependencyManager initialization failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected error during dependency initialization: " + e.getMessage());
            throw new DatabaseException("Unexpected error initializing dependencies.", e);
        }
    }

    /**
     * Creates a default global bot.json with baseline metadata.
     *
     * @throws DatabaseException if the file cannot be written
     */
    private static void createDefaultBotFile() throws DatabaseException {
        Logger.log(Logger.TAG.DEBUG, "Creating default bot.json file...");

        JSONObject botData = new JSONObject()
                .put("warning", "THIS IS STANDARD FORMAT, DO NOT ADD OR REMOVE FIELDS WITHOUT EDITING DependencyManager.java")
                .put("case_counter", 0)
                .put("last_updated", Instant.now().getEpochSecond())
                .put("build_version", 0)
                .put("total_records_logged", 0)
                .put("startup_count", 0)
                .put("version", 0);

        try {
            DatabaseManager.createJSON(BOT_FILE, botData);
            Logger.log(Logger.TAG.INFO, "Default bot.json created successfully.");
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "Failed to create default bot.json: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Increments startup_count in bot.json each time the bot boots.
     *
     * @throws DatabaseException if bot.json cannot be read or written
     */
    private static void updateStartupCount() throws DatabaseException {
        Logger.log(Logger.TAG.DEBUG, "Updating startup count in bot.json...");

        try {
            // Read startup_count from cached JSON
            Object currentCountObj = DatabaseManager.readJSONPath(BOT_FILE, "startup_count");
            int currentCount = (currentCountObj instanceof Number)
                    ? ((Number) currentCountObj).intValue()
                    : 0;

            int newCount = currentCount + 1;
            long timestamp = Instant.now().getEpochSecond();

            // Write new values back to the cached JSON
            DatabaseManager.writeJSONPath(BOT_FILE, "startup_count", newCount, true);
            DatabaseManager.writeJSONPath(BOT_FILE, "last_updated", timestamp, true);

            Logger.log(Logger.TAG.INFO, "Updated startup count: " + newCount);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "Failed to update startup count: " + e.getMessage());
            throw e;
        }
    }
}
