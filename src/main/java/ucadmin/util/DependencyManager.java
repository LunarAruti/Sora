package ucadmin.util;

import org.json.JSONObject;
import ucadmin.exceptions.DatabaseException;
import ucadmin.main.BotConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Handles essential database setup before the bot starts.
 *
 * DependencyManager ensures that all required directories and
 * baseline files exist, and initializes global metadata such
 * as bot.json. It performs direct filesystem I/O only (no
 * Logger/DatabaseManager usage) to avoid startup ordering issues.
 * This class runs once at startup from StartupManager.onReady().
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
        try {
            // === BASE FOLDER STRUCTURE ===
            ensureDirectory("database");
            ensureDirectory(GLOBAL_DIR);
            ensureDirectory(SERVER_DIR);
            ensureDirectory(USER_DIR);

            // === BOT FILE SETUP ===
            if (!fileExists(BOT_FILE)) {
                createDefaultBotFile();
            }

            // === ENSURE CORRUPT + LOG PATHS EXIST ===
            String corruptPath = BotConfig.CORRUPTPATH;
            String logPath     = BotConfig.LOGPATH;
            String dumpPath    = BotConfig.DUMPPATH;
            String networkPath = BotConfig.NETWORKPATH;

            ensureDirectory(corruptPath);
            ensureDirectory(networkPath);
            ensureDirectory(Paths.get(networkPath, "diagnostics").toString());
            ensureDirectory(Paths.get(networkPath, "journal").toString());
            ensureParentDirectory(logPath);
            ensureParentDirectory(dumpPath);
            ensureFile(logPath);
            ensureFile(dumpPath);

        } catch (DatabaseException e) {
            throw e;
        } catch (Exception e) {
            throw new DatabaseException("Unexpected error initializing dependencies.", e);
        }
    }

    /**
     * Creates a default global bot.json with baseline metadata.
     *
     * @throws DatabaseException if the file cannot be written
     */
    private static void createDefaultBotFile() throws DatabaseException {
        JSONObject botData = new JSONObject()
                .put("warning", "THIS IS STANDARD FORMAT, DO NOT ADD OR REMOVE FIELDS WITHOUT EDITING DependencyManager.java")
                .put("case_counter", 0)
                .put("last_updated", Instant.now().getEpochSecond())
                .put("build_version", 0)
                .put("total_records_logged", 0)
                .put("startup_count", 0)
                .put("version", 0);

        try {
            writeJsonFile(BOT_FILE, botData);
        } catch (DatabaseException e) {
            throw e;
        }
    }

    private static void ensureDirectory(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            throw new DatabaseException("Directory path is null or blank.");
        }
        try {
            Path dir = Paths.get(path).toAbsolutePath().normalize();
            if (Files.exists(dir) && !Files.isDirectory(dir)) {
                throw new DatabaseException("Path exists but is not a directory: " + dir);
            }
            Files.createDirectories(dir);
        } catch (InvalidPathException e) {
            throw new DatabaseException("Invalid directory path: " + path, e);
        } catch (Exception e) {
            throw new DatabaseException("Failed to create directory: " + path, e);
        }
    }

    private static void ensureParentDirectory(String filePath) throws DatabaseException {
        if (filePath == null || filePath.isBlank()) {
            throw new DatabaseException("File path is null or blank.");
        }
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            Path parent = path.getParent();
            if (parent != null) {
                ensureDirectory(parent.toString());
            }
        } catch (InvalidPathException e) {
            throw new DatabaseException("Invalid file path: " + filePath, e);
        }
    }

    private static void ensureFile(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            throw new DatabaseException("File path is null or blank.");
        }
        try {
            Path file = Paths.get(path).toAbsolutePath().normalize();
            if (Files.exists(file)) {
                if (!Files.isRegularFile(file)) {
                    throw new DatabaseException("Path exists but is not a regular file: " + file);
                }
                return;
            }
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.createFile(file);
        } catch (InvalidPathException e) {
            throw new DatabaseException("Invalid file path: " + path, e);
        } catch (Exception e) {
            throw new DatabaseException("Failed to create file: " + path, e);
        }
    }

    private static boolean fileExists(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            throw new DatabaseException("File path is null or blank.");
        }
        try {
            Path file = Paths.get(path).toAbsolutePath().normalize();
            return Files.exists(file) && Files.isRegularFile(file);
        } catch (InvalidPathException e) {
            throw new DatabaseException("Invalid file path: " + path, e);
        }
    }

    private static void writeJsonFile(String path, JSONObject data) throws DatabaseException {
        if (data == null) {
            throw new DatabaseException("Cannot write null JSON to: " + path);
        }
        try {
            ensureParentDirectory(path);
            String payload = data.toString(2);
            Files.writeString(
                    Paths.get(path),
                    payload,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (DatabaseException e) {
            throw e;
        } catch (Exception e) {
            throw new DatabaseException("Failed to write JSON file: " + path, e);
        }
    }
}
