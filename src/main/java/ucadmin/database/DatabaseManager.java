package ucadmin.database;

import org.json.JSONArray;
import org.json.JSONObject;
import ucadmin.exceptions.BatchException;
import ucadmin.exceptions.DatabaseException;
import ucadmin.exceptions.QueueException;
import ucadmin.main.BotConfig;
import ucadmin.util.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Basic file-based database manager for UC_Admin.
 * Handles low-level read/write and filesystem operations.
 * NOT command- or system-specific — purely a general utility layer.
 *
 * RELATIVE PATH:
 * "\UC_Admin\database"
 */
public class DatabaseManager {

    private static final String ROOT_DIR = "database";

// =====================
// Initialization
// =====================

    /**
     * Initializes the Database subsystem and logging hooks.
     * Must be called once before using any other DatabaseManager methods.
     *
     * Responsibilities:
     * - Verifies base directories exist (creating them if missing).
     * - Warms up internal caches and background queue workers.
     * - Ensures logger is initialized and ready to capture DB events.
     *
     * @throws DatabaseException if initialization fails due to I/O errors,
     *                           permission issues, or an irrecoverable configuration problem.
     */
    public static void initialize() throws DatabaseException {
        Logger.log(Logger.TAG.SYSTEM, "Initializing database structure...");

        Path baseDir = Paths.get(ROOT_DIR);
        Path globalDir = baseDir.resolve("global");
        Path serverDir = baseDir.resolve("server");
        Path userDir   = baseDir.resolve("user");

        try {
            // Create all required directories
            Files.createDirectories(globalDir);
            Files.createDirectories(serverDir);
            Files.createDirectories(userDir);

            // Verify they exist and are directories
            if (!Files.isDirectory(globalDir))
                throw new DatabaseException("Expected 'database/global' to be a directory.");
            if (!Files.isDirectory(serverDir))
                throw new DatabaseException("Expected 'database/server' to be a directory.");
            if (!Files.isDirectory(userDir))
                throw new DatabaseException("Expected 'database/user' to be a directory.");

            Logger.log(Logger.TAG.INFO, "Database directory structure verified successfully.");

        } catch (IOException e) {
            Logger.log(Logger.TAG.ERROR, "Failed to initialize database structure: " + e.getMessage());
            throw new DatabaseException("Failed to initialize database structure.", e);
        }
    }

// =====================
// Folder Operations
// =====================

    /**
     * Creates a folder at the given path, creating parent folders as needed.
     *
     * Path rules:
     * - Path is relative to the project root unless absolute is provided.
     * - Normalized to the platform's file separator.
     *
     * @param path folder path to create.
     * @return the normalized absolute path of the created folder.
     * @throws DatabaseException if the folder cannot be created due to I/O error
     *                           or invalid path.
     */
    public static String createFolder(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "createFolder: path is null or blank.");
            throw new DatabaseException("Path cannot be null or empty.");
        }

        Path dirPath = Paths.get(path).toAbsolutePath().normalize();
        Logger.log(Logger.TAG.DEBUG, "Creating folder at: " + dirPath);

        try {
            if (Files.exists(dirPath)) {
                if (Files.isDirectory(dirPath)) {
                    if (!Files.isReadable(dirPath) || !Files.isWritable(dirPath)) {
                        Logger.log(Logger.TAG.ERROR, "Existing folder lacks permissions: " + dirPath);
                        throw new DatabaseException("Folder exists but lacks read/write permissions: " + dirPath);
                    }
                    Logger.log(Logger.TAG.INFO, "Folder already exists and is accessible: " + dirPath);
                    return dirPath.toString();
                } else {
                    Logger.log(Logger.TAG.ERROR, "A file already exists at folder path: " + dirPath);
                    throw new DatabaseException("A file already exists at the folder path: " + dirPath);
                }
            }

            // Attempt to create folder
            Files.createDirectories(dirPath);

            // Post-creation validation
            if (!Files.exists(dirPath))
                throw new DatabaseException("Folder creation failed silently: " + dirPath);
            if (!Files.isDirectory(dirPath))
                throw new DatabaseException("Created path is not a directory: " + dirPath);

            // Test write permissions
            try {
                Path testFile = dirPath.resolve(".permtest.tmp");
                try (BufferedWriter writer = Files.newBufferedWriter(testFile)) {
                    writer.write("test");
                }
                Files.deleteIfExists(testFile);
            } catch (IOException permEx) {
                Logger.log(Logger.TAG.ERROR, "Write permission test failed for folder: " + dirPath);
                throw new DatabaseException("Folder created but lacks write permissions: " + dirPath, permEx);
            }

            Logger.log(Logger.TAG.INFO, "Folder created successfully: " + dirPath);
            return dirPath.toString();

        } catch (IOException e) {
            Logger.log(Logger.TAG.ERROR, "I/O error creating folder at " + dirPath + ": " + e.getMessage());
            throw new DatabaseException("I/O error while creating folder at: " + dirPath, e);
        }
    }

    /**
     * Checks whether a folder exists at the given path.
     *
     * @param path folder path to check.
     * @return true if folder exists, false otherwise.
     * @throws DatabaseException if an I/O error occurs while checking.
     */
    public static boolean folderExists(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "folderExists: invalid folder path (null/empty).");
            throw new DatabaseException("Invalid folder path: path is null or empty.");
        }

        try {
            Path dir = Paths.get(path).toAbsolutePath().normalize();

            if (!Files.exists(dir))
                return false;

            if (!Files.isDirectory(dir)) {
                Logger.log(Logger.TAG.ERROR, "folderExists: expected directory but found file at " + dir);
                throw new DatabaseException("Expected a directory, but found a file at: " + dir);
            }

            // Double-check access permissions
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                // Access test successful
            } catch (IOException e) {
                Logger.log(Logger.TAG.ERROR, "folderExists: folder exists but cannot be accessed: " + dir);
                throw new DatabaseException("Folder exists but cannot be accessed: " + dir, e);
            }

            return true;
        } catch (InvalidPathException e) {
            Logger.log(Logger.TAG.ERROR, "folderExists: malformed folder path: " + path);
            throw new DatabaseException("Malformed folder path: " + path, e);
        }
    }

    /**
     * Deletes an empty folder at the given path.
     *
     * Notes:
     * - This method does not recursively delete contents.
     * - The folder must be empty or deletion will fail.
     *
     * @param path folder path to delete.
     * @return true if the folder was deleted, false if it did not exist.
     * @throws DatabaseException if deletion fails due to I/O error or the folder is not empty.
     */
    public static boolean deleteFolder(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "deleteFolder: path is null or blank.");
            throw new DatabaseException("Invalid folder path: path is null or empty.");
        }

        if (!folderExists(path))
            throw new DatabaseException("Folder not found: " + path);

        Path dir = Paths.get(path).toAbsolutePath().normalize();

        if (dir.equals(Paths.get(ROOT_DIR).toAbsolutePath())) {
            Logger.log(Logger.TAG.WARN, "Attempted to delete database root directory. Operation refused.");
            throw new DatabaseException("Refused to delete the database root directory.");
        }

        Logger.log(Logger.TAG.WARN, "Deleting folder and contents: " + dir);

        try {
            // Verify accessibility
            try (DirectoryStream<Path> check = Files.newDirectoryStream(dir)) {
                // ensures directory can be read
            }

            // Delete in reverse order
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            Logger.log(Logger.TAG.ERROR, "Failed to delete: " + p + " (" + e.getMessage() + ")");
                            throw new UncheckedIOException(e);
                        }
                    });

            // Verify deletion
            if (Files.exists(dir)) {
                Logger.log(Logger.TAG.ERROR, "Folder deletion failed or incomplete: " + dir);
                throw new DatabaseException("Folder deletion failed or partially completed: " + dir);
            }

            Logger.log(Logger.TAG.INFO, "Folder deleted successfully: " + dir);
            return true;

        } catch (UncheckedIOException e) {
            Logger.log(Logger.TAG.ERROR, "Error deleting folder contents: " + dir + " (" + e.getCause().getMessage() + ")");
            throw new DatabaseException("Error deleting contents of folder: " + dir, e.getCause());
        } catch (IOException e) {
            Logger.log(Logger.TAG.ERROR, "I/O error deleting folder: " + dir + " (" + e.getMessage() + ")");
            throw new DatabaseException("I/O error while deleting folder: " + dir, e);
        }
    }

// =====================
// File Operations
// =====================

    /**
     * Creates an empty file at the given path, creating parent folders as needed.
     * If the file already exists, this is a no-op and the existing path is returned.
     *
     * @param path file path to create.
     * @return the normalized absolute path of the created (or existing) file.
     * @throws DatabaseException if the file cannot be created due to I/O error
     *                           or invalid path.
     */
    public static String createFile(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "createFile: file path is null or empty.");
            throw new DatabaseException("File path is null or empty.");
        }

        Path filePath;
        try {
            filePath = Paths.get(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            Logger.log(Logger.TAG.ERROR, "createFile: invalid path syntax: " + path);
            throw new DatabaseException("Invalid file path syntax: " + path, e);
        }

        if (Files.isDirectory(filePath)) {
            Logger.log(Logger.TAG.ERROR, "createFile: attempted to create file at directory path: " + filePath);
            throw new DatabaseException("Cannot create file because the path points to a directory: " + filePath);
        }

        if (Files.exists(filePath)) {
            if (!Files.isRegularFile(filePath)) {
                Logger.log(Logger.TAG.ERROR, "createFile: non-regular file already exists at: " + filePath);
                throw new DatabaseException("A non-regular file already exists at: " + filePath);
            }
            Logger.log(Logger.TAG.DEBUG, "createFile: file already exists: " + filePath);
            return filePath.toString();
        }

        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            String createdParent = createFolder(parent.toString());
            if (createdParent == null) {
                Logger.log(Logger.TAG.ERROR, "createFile: failed to create parent directory: " + parent);
                throw new DatabaseException("Failed to create parent directory for file: " + parent);
            }
        }

        try {
            Files.createFile(filePath);

            try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
                writer.write(""); // verify write access
            }

            if (!Files.isWritable(filePath))
                throw new DatabaseException("File created but not writable: " + filePath);

            Logger.log(Logger.TAG.INFO, "File created successfully: " + filePath);
            return filePath.toString();

        } catch (FileAlreadyExistsException e) {
            Logger.log(Logger.TAG.DEBUG, "createFile: file already existed (race-safe): " + filePath);
            return filePath.toString();
        } catch (IOException e) {
            Logger.log(Logger.TAG.ERROR, "createFile I/O error: " + filePath + " (" + e.getMessage() + ")");
            throw new DatabaseException("I/O error occurred while creating file: " + filePath, e);
        } catch (SecurityException e) {
            Logger.log(Logger.TAG.ERROR, "createFile security exception: " + filePath + " (" + e.getMessage() + ")");
            throw new DatabaseException("Insufficient permissions to create file: " + filePath, e);
        }
    }

    /**
     * Checks whether a file exists at the given path.
     *
     * @param path file path to check.
     * @return true if file exists, false otherwise.
     * @throws DatabaseException if an I/O error occurs while checking.
     */
    public static boolean fileExists(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "fileExists: path is null or empty.");
            throw new DatabaseException("Invalid file path: path is null or empty.");
        }

        try {
            Path filePath = Paths.get(path).toAbsolutePath().normalize();

            // Special case: JSON with journal or cache counts as logically existing
            if (path.toLowerCase().endsWith(".json")) {
                Path base = filePath;
                Path journal = Paths.get(path + ".patch").toAbsolutePath().normalize();

                if (Files.exists(base) && Files.isRegularFile(base)) return true;
                if (Files.exists(journal) && Files.isRegularFile(journal)) return true; // journal-only still logical file
                if (QueueManager.hasCacheEntry(path)) return true;
                return false;
            }

            if (!Files.exists(filePath)) return false;

            if (!Files.isRegularFile(filePath)) {
                Logger.log(Logger.TAG.ERROR, "fileExists: expected regular file but found directory: " + filePath);
                throw new DatabaseException("Path exists but is not a regular file: " + filePath);
            }

            try (BufferedReader br = Files.newBufferedReader(filePath)) {
                // verify readable
            } catch (IOException e) {
                Logger.log(Logger.TAG.ERROR, "fileExists: cannot read file: " + filePath);
                throw new DatabaseException("File exists but cannot be read: " + filePath, e);
            }

            return true;
        } catch (InvalidPathException e) {
            Logger.log(Logger.TAG.ERROR, "fileExists: malformed path: " + path);
            throw new DatabaseException("Malformed file path: " + path, e);
        }
    }

    /**
     * Deletes a file at the given path.
     *
     * @param path file path to delete.
     * @return true if deleted, false if file did not exist.
     * @throws DatabaseException if deletion fails due to I/O error or permission issues.
     */
    public static boolean deleteFile(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "deleteFile: path is null or empty.");
            throw new DatabaseException("Invalid path provided to deleteFile.");
        }

        final boolean isJson = path.toLowerCase().endsWith(".json");

        Path filePath;
        try {
            filePath = Paths.get(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            Logger.log(Logger.TAG.ERROR, "deleteFile: malformed file path: " + path);
            throw new DatabaseException("Malformed file path: " + path, e);
        }

        try {
            if (isJson) {
                // Settle queue for this file before deletion
                try { QueueManager.flushFile(filePath.toString(), true); }
                catch (QueueException qe) { throw new DatabaseException("deleteFile: flush failed before delete: " + filePath, qe); }

                if (Files.isDirectory(filePath))
                    throw new DatabaseException("Path points to a directory, not a file: " + filePath);

                boolean ok = true;

                // Delete base if present
                if (Files.exists(filePath)) {
                    try (BufferedReader reader = Files.newBufferedReader(filePath)) { /* ensure readable */ }
                    Files.delete(filePath);
                    ok &= !Files.exists(filePath);
                }

                // Delete journal if present
                Path journal = Paths.get(path + ".patch").toAbsolutePath().normalize();
                if (Files.exists(journal)) {
                    Files.delete(journal);
                    ok &= !Files.exists(journal);
                }

                if (!ok) throw new DatabaseException("Deletion incomplete for base/journal: " + filePath);
                QueueManager.onExternalDelete(filePath.toString());
                Logger.log(Logger.TAG.INFO, "File (JSON+patch) deleted successfully: " + filePath);
                return true;
            }

            // Non-JSON
            if (!fileExists(path))
                throw new DatabaseException("File does not exist at path: " + filePath);

            if (Files.isDirectory(filePath))
                throw new DatabaseException("Path points to a directory, not a file: " + filePath);

            try (BufferedReader reader = Files.newBufferedReader(filePath)) { /* ensure readable before delete */ }
            Files.delete(filePath);

            if (Files.exists(filePath)) {
                Logger.log(Logger.TAG.ERROR, "deleteFile: file still exists after deletion attempt: " + filePath);
                throw new DatabaseException("Deletion failed: file still exists after delete attempt: " + filePath);
            }

            QueueManager.onExternalDelete(filePath.toString());
            Logger.log(Logger.TAG.INFO, "File deleted successfully: " + filePath);
            return true;

        } catch (DatabaseException e) {
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "deleteFile I/O error: " + filePath + " (" + e.getMessage() + ")");
            throw new DatabaseException("Failed to delete file at path: " + filePath, e);
        }
    }

    /**
     * Renames or moves a file from oldPath to newPath. Parent directories are created as needed.
     *
     * Behavior:
     * - Overwrite is not performed; if the destination exists, the operation fails.
     *
     * @param oldPath current file path.
     * @param newPath target file path.
     * @return true if the file was moved/renamed, false if the source did not exist.
     * @throws DatabaseException if the move fails due to I/O error or destination already exists.
     */
    public static boolean renameFile(String oldPath, String newPath) throws DatabaseException {
        if (oldPath == null || newPath == null || oldPath.isBlank() || newPath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "renameFile: invalid arguments.");
            throw new DatabaseException("Invalid arguments provided to renameFile.");
        }

        final boolean isJson = oldPath.toLowerCase().endsWith(".json")
                && newPath.toLowerCase().endsWith(".json");

        final Path source, target;
        try {
            source = Paths.get(oldPath).toAbsolutePath().normalize();
            target = Paths.get(newPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            Logger.log(Logger.TAG.ERROR, "renameFile: invalid source or destination path.");
            throw new DatabaseException("Invalid source or destination path.", e);
        }

        try {
            if (isJson) {
                // 1) Settle queue first (append any pending patches).
                try {
                    QueueManager.flushFile(source.toString(), true);
                } catch (QueueException qe) {
                    Logger.log(Logger.TAG.ERROR, "renameFile: flush before move failed: " + source + " — " + qe.getMessage());
                    throw new DatabaseException("Failed to flush source before move: " + source, qe);
                }

                // 2) Local MATERIALIZE (no QueueManager API dependency):
                //    If journal exists and has content, persist merged snapshot into base and remove journal.
                final Path oldBase = source;
                final Path oldJ = Paths.get(source.toString() + ".patch").toAbsolutePath().normalize();
                try {
                    if (Files.exists(oldJ) && Files.size(oldJ) > 0L) {
                        org.json.JSONObject snap = readJSONRaw(oldBase.toString()); // replays journal
                        writeJSONRaw(oldBase.toString(), snap);                      // compact base
                        try {
                            Files.deleteIfExists(oldJ);                              // journal consumed
                            Logger.log(Logger.TAG.DEBUG, "renameFile: materialized & deleted journal: " + oldJ);
                        } catch (IOException delJ) {
                            Logger.log(Logger.TAG.WARN, "renameFile: could not delete journal after materialize: " + oldJ + " (" + delJ.getMessage() + ")");
                        }
                    }
                } catch (Exception matEx) {
                    throw new DatabaseException("Failed to materialize JSON before move: " + oldBase, matEx);
                }

                // 3) Ensure destination folder exists.
                Path parent = target.getParent();
                if (parent != null && !Files.exists(parent)) {
                    String createdParent = createFolder(parent.toString());
                    if (createdParent == null)
                        throw new DatabaseException("Failed to create destination folder: " + parent);
                }

                final Path newBase = target;
                final Path newJ = Paths.get(target.toString() + ".patch").toAbsolutePath().normalize();

                // 4) If base still missing (rare journal-only edge), reconstruct base so there is a source.
                if (!Files.exists(oldBase)) {
                    try {
                        org.json.JSONObject snap = readJSONRaw(oldBase.toString());
                        writeJSONRaw(oldBase.toString(), snap);
                    } catch (Exception io) {
                        throw new DatabaseException("Failed to reconstruct base from journal before move: " + oldBase, io);
                    }
                }

                // 5) Move the base file.
                if (Files.exists(oldBase)) {
                    try {
                        Files.move(oldBase, newBase,
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (Exception atomicFail) {
                        Files.move(oldBase, newBase, StandardCopyOption.REPLACE_EXISTING);
                    }
                }

                // 6) Move the journal if it (still) exists. (Likely gone after materialize; this is just safety.)
                if (Files.exists(oldJ)) {
                    if (newJ.getParent() != null) Files.createDirectories(newJ.getParent());
                    try {
                        Files.move(oldJ, newJ,
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (Exception ignoreAtomic) {
                        Files.move(oldJ, newJ, StandardCopyOption.REPLACE_EXISTING);
                    }
                    // Delete zero-byte journal at destination.
                    try {
                        if (Files.exists(newJ) && Files.size(newJ) == 0L) {
                            Files.delete(newJ);
                            Logger.log(Logger.TAG.INFO, "renameFile: deleted empty journal at destination: " + newJ);
                        }
                    } catch (IOException ignore) {
                        Logger.log(Logger.TAG.DEBUG, "renameFile: skip empty journal cleanup: " + ignore.getMessage());
                    }
                }

                // 7) Rebind cache key so the same CacheEntry follows the rename.
                try {
                    QueueManager.onPathRenamed(source.toString(), target.toString());
                } catch (Exception qex) {
                    Logger.log(Logger.TAG.WARN, "renameFile: queue path remap warning for " + source + " → " + target
                            + " — " + qex.getMessage());
                    // Non-fatal: cache will rehydrate on next access if needed.
                }

                Logger.log(Logger.TAG.INFO, "File (JSON+patch) moved successfully: " + source + " → " + target);
                return true;
            }

            // --- Non-JSON files ---
            if (!fileExists(source.toString()))
                throw new DatabaseException("Source file does not exist at: " + source);

            if (Files.isDirectory(source))
                throw new DatabaseException("Source path points to a directory, not a file: " + source);

            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                String createdParent = createFolder(parent.toString());
                if (createdParent == null)
                    throw new DatabaseException("Failed to create destination folder: " + parent);
            }

            try (BufferedReader reader = Files.newBufferedReader(source)) { /* verify readable */ }

            try {
                Files.move(source, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignoreAtomic) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!Files.exists(target) || !Files.isWritable(target))
                throw new DatabaseException("Move operation incomplete: " + target);

            try { QueueManager.onPathRenamed(source.toString(), target.toString()); } catch (Exception ignore) {}

            Logger.log(Logger.TAG.INFO, "File moved successfully: " + source + " → " + target);
            return true;

        } catch (DatabaseException e) {
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "renameFile: I/O error while moving file: " + source + " → " + target + " (" + e.getMessage() + ")");
            throw new DatabaseException("Failed to rename or move file from " + source + " to " + target, e);
        }
    }

    /**
     * Copies a file from sourcePath to destPath. Parent directories are created as needed.
     *
     * Behavior:
     * - Overwrite is not performed; if the destination exists, the operation fails.
     *
     * @param sourcePath source file path.
     * @param destPath destination file path.
     * @return true if the file was copied, false if the source did not exist.
     * @throws DatabaseException if copy fails due to I/O error or destination already exists.
     */
    public static boolean copyFile(String sourcePath, String destPath) throws DatabaseException {
        if (sourcePath == null || destPath == null || sourcePath.isBlank() || destPath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "copyFile: invalid arguments.");
            throw new DatabaseException("Invalid arguments provided to copyFile.");
        }

        final boolean isJson = sourcePath.toLowerCase().endsWith(".json")
                && destPath.toLowerCase().endsWith(".json");

        Path source, dest;
        try {
            source = Paths.get(sourcePath).toAbsolutePath().normalize();
            dest   = Paths.get(destPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            Logger.log(Logger.TAG.ERROR, "copyFile: malformed source or destination path.");
            throw new DatabaseException("Malformed source or destination path.", e);
        }

        try {
            if (isJson) {
                // Ensure the latest logical state is settled (base + journal consistent)
                try { QueueManager.flushFile(source.toString(), true); }
                catch (QueueException qe) { throw new DatabaseException("copyFile: source flush failed: " + source, qe); }

                // Rebuild authoritative snapshot from base + journal (throws on malformed)
                org.json.JSONObject snap = readJSONRaw(source.toString());

                // Ensure destination folder exists
                Path parent = dest.getParent();
                if (parent != null && !Files.exists(parent)) {
                    String createdParent = createFolder(parent.toString());
                    if (createdParent == null) throw new DatabaseException("Failed to create destination folder: " + parent);
                }

                // Materialize a clean base at destination (pretty + verified)
                writeJSONRaw(dest.toString(), snap);

                // Carry over journal, if any (so ongoing deltas remain intact)
                Path srcJ = Paths.get(source.toString() + ".patch");
                Path dstJ = Paths.get(dest.toString()   + ".patch");
                if (Files.exists(srcJ)) {
                    if (dstJ.getParent() != null) Files.createDirectories(dstJ.getParent());
                    Files.copy(srcJ, dstJ, StandardCopyOption.REPLACE_EXISTING);
                }

                Logger.log(Logger.TAG.INFO, "File (JSON+patch) copied successfully: " + source + " → " + dest);
                return true;
            }

            // Non-JSON: raw file copy
            if (!fileExists(source.toString()))
                throw new DatabaseException("Source file does not exist at: " + source);

            if (Files.isDirectory(source))
                throw new DatabaseException("Source path points to a directory: " + source);

            Path parent = dest.getParent();
            if (parent != null && !Files.exists(parent)) {
                String createdParent = createFolder(parent.toString());
                if (createdParent == null) throw new DatabaseException("Failed to create destination folder: " + parent);
            }

            try (BufferedReader br = Files.newBufferedReader(source)) { /* verify readable */ }

            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);

            if (!Files.exists(dest))
                throw new DatabaseException("Copy operation incomplete: destination file missing at: " + dest);

            try (BufferedReader br = Files.newBufferedReader(dest)) { /* verify readable */ }

            Logger.log(Logger.TAG.INFO, "File copied successfully: " + source + " → " + dest);
            return true;

        } catch (DatabaseException e) {
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "copyFile: I/O error while copying file: " + source + " → " + dest + " (" + e.getMessage() + ")");
            throw new DatabaseException("Failed to copy file from " + source + " to " + dest, e);
        }
    }

// =====================
// Path Utilities
// =====================

    /**
     * Marks a cached FILE path as TEMP.
     *
     * TEMP means the cache entry exists only in memory and will never be patched or
     * materialized to disk. Disk state is untouched.
     *
     * NOTE: This is cache-only. If the file is not currently cached, this does nothing.
     */
    /**
     * Marks a cached file entry as TEMP.
     *
     * Returns:
     *   true  -> cache entry was found and updated
     *   false -> no cache entry was affected (or disk entry exists)
     *
     * @param path file path to mark temporary
     * @return true if temp flag applied in cache; false otherwise
     * @throws DatabaseException if path is invalid
     */
    public static boolean makeTemporary(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "makeTemporary: path is null or empty.");
            throw new DatabaseException("Path is null or empty.");
        }

        final Path npath;
        try {
            npath = Paths.get(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            Logger.log(Logger.TAG.ERROR, "makeTemporary: invalid path syntax: " + path);
            throw new DatabaseException("Invalid path syntax: " + path, e);
        }

        // Guard: if a base file or journal exists, do NOT allow TEMP.
        // TEMP is intended only for cache-only entries with no disk anchor.
        if (fileExists(npath.toString())) {
            Logger.log(Logger.TAG.WARN, "makeTemporary: refused (disk entry exists) for " + npath);
            return false;
        }

        boolean changed = QueueManager.makeTemporary(npath.toString());
        if (!changed) {
            Logger.log(Logger.TAG.WARN, "makeTemporary: no cached file entry affected for " + npath);
            return false;
        }

        Logger.log(Logger.TAG.INFO, "makeTemporary applied to cached file: " + npath);
        return true;
    }

    /**
     * Marks a cached FILE path as PERMANENT.
     *
     * PERMANENT means the cache entry re-enters the normal patch/materialize lifecycle.
     * Disk state is untouched.
     *
     * NOTE: This is cache-only. If the file is not currently cached, this does nothing.
     */
    /**
     * Marks a cached file entry as PERMANENT.
     *
     * Returns:
     *   true  -> cache entry was found and updated
     *   false -> no cache entry was affected
     *
     * @param path file path to mark permanent
     * @return true if permanent flag applied in cache; false otherwise
     * @throws DatabaseException if path is invalid
     */
    public static boolean makePermanent(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "makePermanent: path is null or empty.");
            throw new DatabaseException("Path is null or empty.");
        }

        final Path npath;
        try {
            npath = Paths.get(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            Logger.log(Logger.TAG.ERROR, "makePermanent: invalid path syntax: " + path);
            throw new DatabaseException("Invalid path syntax: " + path, e);
        }

        boolean changed = QueueManager.makePermanent(npath.toString());
        if (!changed) {
            Logger.log(Logger.TAG.WARN, "makePermanent: no cached file entry affected for " + npath);
            return false;
        }

        Logger.log(Logger.TAG.INFO, "makePermanent applied to cached file: " + npath);
        return true;
    }

    /**
     * Checks whether a path currently exists in the in-memory cache.
     * This does not check disk; it only checks the QueueManager cache map.
     */
    public static boolean cacheExists(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "cacheExists: path is null or empty.");
            throw new DatabaseException("Path is null or empty.");
        }
        try {
            return QueueManager.hasCacheEntry(path);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "cacheExists: error checking cache for " + path + ": " + e.getMessage());
            throw new DatabaseException("cacheExists: failed to check cache for " + path, e);
        }
    }

    /**
     * Lists files directly under the given folder (non-recursive).
     *
     * @param folderPath folder to enumerate.
     * @return list of file names contained directly within the folder (may be empty).
     * @throws DatabaseException if an I/O error occurs or the folder does not exist.
     */
    public static List<String> listFiles(String folderPath) throws DatabaseException {
        Logger.log(Logger.TAG.DEBUG, "Listing files in: " + folderPath);

        if (folderPath == null || folderPath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "listFiles failed: path is null or blank.");
            throw new DatabaseException("Folder path is null or empty");
        }

        File folder = new File(folderPath);
        if (!folder.exists()) {
            Logger.log(Logger.TAG.ERROR, "listFiles failed: folder does not exist (" + folderPath + ")");
            throw new DatabaseException("Folder does not exist: " + folderPath);
        }

        if (!folder.isDirectory()) {
            Logger.log(Logger.TAG.ERROR, "listFiles failed: specified path is not a directory (" + folderPath + ")");
            throw new DatabaseException("Specified path is not a directory: " + folderPath);
        }

        File[] fileList = folder.listFiles();
        if (fileList == null) {
            Logger.log(Logger.TAG.ERROR, "listFiles failed: unable to read folder contents (" + folderPath + ")");
            throw new DatabaseException("Failed to list files for folder: " + folderPath);
        }

        List<String> files = new ArrayList<>();
        for (File file : fileList) {
            if (file.isFile()) files.add(file.getName());
        }

        Logger.log(Logger.TAG.INFO, "Found " + files.size() + " files in folder: " + folderPath);
        return files;
    }

    /**
     * Lists subfolders directly under the given folder (non-recursive).
     *
     * @param folderPath folder to enumerate.
     * @return list of subfolder names contained directly within the folder (may be empty).
     * @throws DatabaseException if an I/O error occurs or the folder does not exist.
     */
    public static List<String> listFolders(String folderPath) throws DatabaseException {
        Logger.log(Logger.TAG.DEBUG, "Listing folders in: " + folderPath);

        if (folderPath == null || folderPath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "listFolders failed: path is null or blank.");
            throw new DatabaseException("Folder path is null or empty");
        }

        File folder = new File(folderPath);
        if (!folder.exists()) {
            Logger.log(Logger.TAG.ERROR, "listFolders failed: folder does not exist (" + folderPath + ")");
            throw new DatabaseException("Folder does not exist: " + folderPath);
        }

        if (!folder.isDirectory()) {
            Logger.log(Logger.TAG.ERROR, "listFolders failed: specified path is not a directory (" + folderPath + ")");
            throw new DatabaseException("Specified path is not a directory: " + folderPath);
        }

        File[] fileList = folder.listFiles();
        if (fileList == null) {
            Logger.log(Logger.TAG.ERROR, "listFolders failed: unable to read directory (" + folderPath + ")");
            throw new DatabaseException("Failed to list folders for directory: " + folderPath);
        }

        List<String> folders = new ArrayList<>();
        for (File file : fileList) {
            if (file.isDirectory()) folders.add(file.getName());
        }

        Logger.log(Logger.TAG.INFO, "Found " + folders.size() + " subfolders in directory: " + folderPath);
        return folders;
    }

    /**
     * Returns the file extension (without the leading dot) for the given path.
     *
     * Examples:
     * - "data/user.json" -> "json"
     * - "archive" -> "" (empty string)
     *
     * @param path file path.
     * @return extension string or empty string if none.
     * @throws DatabaseException if path is invalid.
     */
    public static String getExtension(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "getExtension failed: path is null or blank.");
            throw new DatabaseException("Path is null or empty");
        }

        File file = new File(path);
        if (!file.exists()) {
            Logger.log(Logger.TAG.ERROR, "getExtension failed: file does not exist (" + path + ")");
            throw new DatabaseException("Cannot get extension, file does not exist: " + path);
        }

        String name = file.getName();
        int i = name.lastIndexOf('.');
        String ext = (i > 0 && i < name.length() - 1) ? name.substring(i + 1) : "";

        Logger.log(Logger.TAG.DEBUG, "File extension resolved for " + path + ": " + (ext.isEmpty() ? "[none]" : ext));
        return ext;
    }

    /**
     * Returns the normalized parent folder for the given path.
     *
     * @param path file or folder path.
     * @return the parent path, or null if none exists.
     * @throws DatabaseException if path is invalid.
     */
    public static String getParentPath(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "getParentPath failed: path is null or blank.");
            throw new DatabaseException("Path is null or empty");
        }

        File file = new File(path);
        if (!file.exists()) {
            Logger.log(Logger.TAG.ERROR, "getParentPath failed: target does not exist (" + path + ")");
            throw new DatabaseException("Cannot get parent path, file or folder does not exist: " + path);
        }

        String parent = file.getParent();
        if (parent == null) {
            Logger.log(Logger.TAG.ERROR, "getParentPath failed: no parent directory (" + path + ")");
            throw new DatabaseException("Path has no parent directory: " + path);
        }

        Logger.log(Logger.TAG.DEBUG, "Parent path resolved: " + parent);
        return parent;
    }

    /**
     * Returns the terminal file name or folder name for the given path.
     *
     * @param path file or folder path.
     * @return the name component (not including parent directories).
     * @throws DatabaseException if path is invalid.
     */
    public static String getFileName(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "getFileName failed: path is null or blank.");
            throw new DatabaseException("Path is null or empty");
        }

        File file = new File(path);
        if (!file.exists()) {
            Logger.log(Logger.TAG.ERROR, "getFileName failed: file/folder does not exist (" + path + ")");
            throw new DatabaseException("Cannot get file name, path does not exist: " + path);
        }

        String name = file.getName();
        if (name == null || name.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "getFileName failed: unresolved file name (" + path + ")");
            throw new DatabaseException("Failed to resolve file name from path: " + path);
        }

        Logger.log(Logger.TAG.DEBUG, "Resolved file name for " + path + ": " + name);
        return name;
    }

// =====================
// Json Main
// =====================

    /**
     * Creates a JSON file initialized with "{}".
     * Registers the file in the in-memory cache and marks it dirty until flushed.
     *
     * Behavior:
     *   - Validates that the target path is non-null, non-blank, and not a directory.
     *   - Ensures parent directories exist (creates them if necessary).
     *   - If the file already exists, returns its absolute path unchanged.
     *   - Otherwise registers a new cache entry and seeds it with an empty object.
     *   - Physical disk persistence occurs on the next queue flush via delta patch writes.
     *
     * Returns:
     *   The normalized absolute path to the JSON file.
     *
     * Throws:
     *   DatabaseException if the path is invalid, points to a directory, or cache/batch setup fails.
     *
     * @param path the target JSON file path.
     * @return the normalized absolute path to the JSON file.
     * @throws DatabaseException if validation, cache registration, or setup fails.
     */
    public static String createJSON(String path) throws DatabaseException {
        return createJSON(path, new JSONObject());
    }

    /**
     * Creates a JSON file with the provided defaultContent as the root.
     * Registers the file in the in-memory cache and marks it dirty until flushed.
     *
     * Usage:
     *   JSONObject def = new JSONObject().put("case_counter", 0);
     *   String p = DatabaseManager.createJSON("database/global/bot.json", def);
     *
     * Behavior:
     *   - Validates path; ensures parents exist.
     *   - If the file exists, returns its absolute path unchanged.
     *   - Otherwise registers a new cache entry and seeds it with defaultContent.
     *   - Physical disk persistence occurs on the next queue flush via delta patch writes.
     *
     * Returns:
     *   The normalized absolute path to the JSON file.
     *
     * Throws:
     *   DatabaseException if the path is invalid or cache/batch setup fails.
     *
     * @param path the target JSON file path.
     * @param defaultContent the initial root object (non-null; may be empty).
     * @return the normalized absolute path to the JSON file.
     * @throws DatabaseException if validation, cache registration, or setup fails.
     */
    public static String createJSON(String path, JSONObject defaultContent) throws DatabaseException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "createJSON called with null or empty path.");
            throw new DatabaseException("createJSON: path is null or empty.");
        }
        if (defaultContent == null) {
            Logger.log(Logger.TAG.ERROR, "createJSON called with null defaultContent.");
            throw new DatabaseException("createJSON: defaultContent is null.");
        }

        Path jsonPath;
        try {
            jsonPath = Paths.get(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            Logger.log(Logger.TAG.ERROR, "createJSON: malformed path " + path);
            throw new DatabaseException("createJSON: malformed path: " + path, e);
        }

        if (Files.exists(jsonPath) && Files.isDirectory(jsonPath)) {
            Logger.log(Logger.TAG.ERROR, "createJSON: target path points to a directory: " + jsonPath);
            throw new DatabaseException("createJSON: target path is a directory: " + jsonPath);
        }

        // Ensure parent folder hierarchy
        Path parent = jsonPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Logger.log(Logger.TAG.DEBUG, "createJSON: ensuring parent folder exists for " + parent);
            String ensured = createFolder(parent.toString());
            if (ensured == null)
                throw new DatabaseException("createJSON: failed to create parent folder: " + parent);
        }

        try {
            Logger.log(Logger.TAG.DEBUG, "createJSON: registering new JSON file in cache: " + jsonPath);

            QueueManager.Batch batch = BatchManager.buildReplaceRoot(defaultContent);

            Boolean result = QueueManager.enqueueBatchAndGet(
                    jsonPath.toString(),
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result)) {
                Logger.log(Logger.TAG.ERROR, "createJSON: failed to queue initialization batch for " + jsonPath);
                throw new DatabaseException("createJSON: failed to queue initialization batch for: " + jsonPath);
            }

            Logger.log(Logger.TAG.INFO, "createJSON: queued successfully (will write on flush): " + jsonPath);
            return jsonPath.toString();

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "createJSON: batch creation failed for " + jsonPath + ": " + e.getMessage());
            throw new DatabaseException("createJSON: failed to build batch for default content: " + jsonPath, e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "createJSON: queue insertion failed for " + jsonPath + ": " + e.getMessage());
            throw new DatabaseException("createJSON: queue insertion error for: " + jsonPath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "createJSON: unexpected error for " + jsonPath + ": " + e.getMessage());
            throw new DatabaseException("createJSON: unexpected error creating JSON file: " + jsonPath, e);
        }
    }

    /**
     * Validates a JSON file's syntax and (optionally) performs minimal, generic repairs.
     * DOES NOT enforce any specific schema or default structure.
     *
     * Usage:
     *   IntegrityReport r = DatabaseManager.ensureJSONIntegrity("database/global/bot.json", true, true);
     *
     * Behavior:
     *   - Reads via QueueManager cache (no direct disk I/O).
     *   - If missing:
     *       • autoRepair=false → throws DatabaseException.
     *       • autoRepair=true  → creates an empty object and queues it for flush.
     *   - Performs BOM stripping, trailing-comma repair, and empty-file repair in memory.
     *   - Writes repaired content back into cache via BatchManager + QueueManager.
     *
     * Returns:
     *   An IntegrityReport describing whether the JSON was valid, modified, and its top-level type.
     *
     * Throws:
     *   DatabaseException on invalid paths or unrecoverable syntax errors.
     *
     * @param path the JSON file path to validate/repair.
     * @param enforceObject if true, requires/repairs a JSONObject root (subject to autoRepair).
     * @param autoRepair if true, attempts non-destructive in-memory fixes.
     * @return an IntegrityReport with validity, modification, and root-type info.
     * @throws DatabaseException if validation fails or the file cannot be processed.
     */
    public static IntegrityReport ensureJSONIntegrity(String path, boolean enforceObject, boolean autoRepair)
            throws DatabaseException {

        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: path is null or empty.");
            throw new DatabaseException("ensureJSONIntegrity: path is null or empty.");
        }

        Path jsonPath = Paths.get(path).toAbsolutePath().normalize();
        Logger.log(Logger.TAG.DEBUG, "Ensuring JSON integrity for: " + jsonPath);

        // 1) Handle missing file (journal-first reconstruction)
        if (!fileExists(jsonPath.toString())) {
            if (!autoRepair) {
                Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: file not found (no repair): " + jsonPath);
                throw new DatabaseException("ensureJSONIntegrity: file not found: " + jsonPath);
            }

            Logger.log(Logger.TAG.WARN, "ensureJSONIntegrity: file missing; attempting reconstruction from journal/base: " + jsonPath);
            try {
                // Try to let the queue rebuild from journal if present
                try {
                    QueueManager.flushFile(jsonPath.toString(), /*materialize=*/true);
                    Logger.log(Logger.TAG.DEBUG, "ensureJSONIntegrity: flush attempted for missing file → " + jsonPath);
                } catch (QueueException qe) {
                    Logger.log(Logger.TAG.WARN, "ensureJSONIntegrity: flush failed on missing file (continuing): " + qe.getMessage());
                }

                Path base = jsonPath;
                Path journal = Paths.get(jsonPath.toString() + ".patch");
                boolean existsAfterFlush = Files.exists(base) || Files.exists(journal);

                if (existsAfterFlush) {
                    Logger.log(Logger.TAG.DEBUG, "ensureJSONIntegrity: reconstructing snapshot from base+journal for " + jsonPath);
                    // Rebuild authoritative snapshot from base + journal
                    JSONObject snap = readJSONRaw(jsonPath.toString()); // strict parse + replay + validate
                    writeJSONRaw(jsonPath.toString(), snap);            // materialize a clean base atomically
                    // Also ensure journal is cleared via the queue’s compaction path
                    try {
                        QueueManager.flushFile(jsonPath.toString(), /*materialize=*/true);
                    } catch (QueueException qe) {
                        Logger.log(Logger.TAG.WARN, "ensureJSONIntegrity: post-repair materialize failed (continuing): " + qe.getMessage());
                    }
                    Logger.log(Logger.TAG.INFO, "ensureJSONIntegrity: repaired missing file from journal/base: " + jsonPath);
                } else {
                    // No way to reconstruct — create empty JSON as last resort
                    Logger.log(Logger.TAG.WARN, "ensureJSONIntegrity: no base/journal present; initializing empty JSON for " + jsonPath);
                    createJSON(jsonPath.toString(), new JSONObject());
                    try {
                        QueueManager.flushFile(jsonPath.toString(), /*materialize=*/true);
                        Logger.log(Logger.TAG.INFO, "ensureJSONIntegrity: materialized new empty base for " + jsonPath);
                    } catch (QueueException qe) {
                        Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: materialize failed for new file " + jsonPath + " — " + qe.getMessage());
                        throw new DatabaseException("ensureJSONIntegrity: materialize failed for new file.", qe);
                    }
                    return new IntegrityReport(true, true, "object")
                            .add("Created missing file as empty JSON object (materialized).");
                }
            } catch (Exception repairEx) {
                Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: auto-repair reconstruction failed for "
                        + jsonPath + ": " + repairEx.getMessage());
                // Last resort to keep the system usable
                createJSON(jsonPath.toString(), new JSONObject());
                try {
                    QueueManager.flushFile(jsonPath.toString(), /*materialize=*/true);
                } catch (QueueException qe) {
                    Logger.log(Logger.TAG.WARN, "ensureJSONIntegrity: fallback materialize failed: " + qe.getMessage());
                }
                return new IntegrityReport(true, true, "object")
                        .add("Auto-repair reconstruction failed; initialized empty JSON (materialized).");
            }
        }

        // 2) Load current (now guaranteed to exist) through the queue/cache
        JSONObject data;
        try {
            data = QueueManager.readValue(jsonPath.toString(), null, json -> json);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: queue read error (possible corruption): " + jsonPath + " (" + e.getMessage() + ")");

            // Quarantine corrupt on-disk base (best effort)
            try {
                QueueManager.RawIO.moveToCorrupt(jsonPath.toString());
                Logger.log(Logger.TAG.WARN, "ensureJSONIntegrity: moved corrupt file to quarantine: " + jsonPath);
                Logger.logDump("ENSURE_QUARANTINE\npath=" + jsonPath + "\nerr=" + e.getMessage());
            } catch (Throwable quarantineFail) {
                Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: quarantine failed for " + jsonPath + " — " + quarantineFail.getMessage());
            }

            // Re-init cache to {} and materialize clean base
            try {
                QueueManager.Batch b = BatchManager.buildReplaceRoot(new JSONObject());
                QueueManager.enqueueBatchAndGet(jsonPath.toString(), null, b, json -> true);
                QueueManager.flushFile(jsonPath.toString(), /*materialize=*/true);
                Logger.log(Logger.TAG.INFO, "ensureJSONIntegrity: re-initialized and materialized clean base for " + jsonPath);
                return new IntegrityReport(true, true, "object")
                        .add("Corrupt file quarantined; base re-initialized and materialized.");
            } catch (Exception reinitFail) {
                throw new DatabaseException("ensureJSONIntegrity: failed to re-initialize after corruption.", reinitFail);
            }
        }

        if (data == null) {
            Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: cache returned null for " + jsonPath);
            throw new DatabaseException("ensureJSONIntegrity: cache returned null JSON for " + jsonPath);
        }

        boolean modified = false;
        String topType = "object";

        // 3) Detect illegal array root (your project policy keeps object root)
        if (enforceObject && data.opt("__ARRAY__") instanceof JSONArray) {
            if (!autoRepair) {
                Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: array root found but enforceObject=true: " + jsonPath);
                throw new DatabaseException("ensureJSONIntegrity: file has array root but enforceObject=true.");
            }

            try {
                Logger.log(Logger.TAG.WARN, "ensureJSONIntegrity: repairing array root to object: " + jsonPath);
                QueueManager.Batch b = BatchManager.buildReplaceRoot(new JSONObject());
                QueueManager.enqueueBatchAndGet(jsonPath.toString(), null, b, json -> true);
                QueueManager.flushFile(jsonPath.toString(), /*materialize=*/true); // materialize after repair
                modified = true;
                topType = "object";
            } catch (Exception e) {
                Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: failed to replace array root for " + jsonPath + ": " + e.getMessage());
                throw new DatabaseException("ensureJSONIntegrity: failed to replace array root with object.", e);
            }

            return new IntegrityReport(true, true, "object")
                    .add("Replaced array root with empty object per enforceObject=true (materialized).");
        }

        // 4) Lightweight normalization / pretty print (cosmetic; no materialize needed)
        try {
            String normalized = data.toString(4);
            String current = data.toString();
            if (!normalized.equals(current) && autoRepair) {
                Logger.log(Logger.TAG.DEBUG, "ensureJSONIntegrity: normalizing JSON formatting: " + jsonPath);
                QueueManager.Batch normalize = BatchManager.buildReplaceRoot(data);
                QueueManager.enqueueBatchAndGet(jsonPath.toString(), null, normalize, json -> true);
                modified = true;
            }
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "ensureJSONIntegrity: normalization failed for " + jsonPath + ": " + e.getMessage());
            throw new DatabaseException("ensureJSONIntegrity: failed to normalize JSON formatting.", e);
        }

        Logger.log(Logger.TAG.INFO, "ensureJSONIntegrity complete for " + jsonPath +
                (modified ? " (repaired)" : " (valid)"));
        return new IntegrityReport(true, modified, topType)
                .add(modified ? "Normalized JSON formatting in cache." : "JSON valid; no repair needed.");
    }

    /**
     * Reads a deeply nested value from a JSON file using dot + array notation.
     *
     * Examples:
     *   "address.city"
     *   "courses[0].credits"
     *   "metadata.owner.roles[0]"
     *
     * Behavior:
     *   - Reads exclusively from the cached JSON managed by QueueManager (no direct disk I/O).
     *   - Throws DatabaseException for any invalid path, key, or array access.
     *
     * Returns:
     *   The value at the given JSON path (JSONObject, JSONArray, or primitive).
     *
     * Throws:
     *   DatabaseException if file/path invalid, segment missing, wrong type, or index OOB.
     *
     * @param filePath the target JSON file path.
     * @param jsonPath the dot/array path to resolve.
     * @return the resolved value from cache (may be JSONObject, JSONArray, primitive, or null).
     * @throws DatabaseException if traversal fails or the file cannot be read from cache.
     */
    public static Object readJSONPath(String filePath, String jsonPath) throws DatabaseException {
        if (filePath == null || filePath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "readJSONPath: file path is null or empty.");
            throw new DatabaseException("readJSONPath: file path is null or empty.");
        }

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final String finalPath = normalizedPath;
        final boolean isRoot = isRootPath(finalPath);
        String pathForLog = isRoot ? "<root>" : "'" + finalPath + "'";
        Logger.log(Logger.TAG.DEBUG, "Reading JSON path " + pathForLog + " from file: " + filePath);

        try {
            Object result = QueueManager.readValue(
                    filePath,
                    null, // Loader handled internally by QueueManager
                    root -> {
                        if (isRoot) {
                            // Root is always an object in this system
                            return (root == JSONObject.NULL) ? null : root;
                        }
                        try {
                            List<String> tokens = tokenizePath(finalPath);
                            Object current = root;

                            for (String token : tokens) {
                                Segment seg = parseSegment(token);

                                if (seg.baseKey.length() == 0 && !seg.indexes.isEmpty()) {
                                    throw new DatabaseException("Array index without key is not allowed: " + token);
                                }

                                // Object lookup
                                if (seg.baseKey.length() > 0) {
                                    if (!(current instanceof JSONObject obj))
                                        throw new DatabaseException("Expected object for key '" + seg.baseKey + "' in segment '" + token + "'");
                                    if (!obj.has(seg.baseKey))
                                        throw new DatabaseException("Missing key '" + seg.baseKey + "' in segment '" + token + "'");
                                    current = obj.get(seg.baseKey);
                                }

                                // Array indexes
                                for (int idx : seg.indexes) {
                                    if (!(current instanceof JSONArray arr))
                                        throw new DatabaseException("Expected array before index [" + idx + "] in segment '" + token + "'");
                                    if (idx < 0 || idx >= arr.length())
                                        throw new DatabaseException("Index out of bounds [" + idx + "] in segment '" + token + "'");
                                    current = arr.get(idx);
                                }
                            }

                            return (current == JSONObject.NULL) ? null : current;

                        } catch (DatabaseException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new DatabaseException("Unexpected error traversing JSON path: " + jsonPath, e);
                        }
                    }
            );

            Logger.log(Logger.TAG.INFO, "Successfully read JSON path " + pathForLog + " from file: " + filePath);
            return result;

        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue read error for file: " + filePath + " (" + e.getMessage() + ")");
            throw new DatabaseException("Queue read error for file: " + filePath, e);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "Failed to read JSON path " + pathForLog + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Writes or updates a deeply nested JSON value at the specified path.
     * Creates missing objects/arrays only if createMissing == true.
     *
     * Behavior:
     *   - Builds a batch representing the requested write.
     *   - Applies it immediately to the cached JSON (disk updated later via delta flush).
     *
     * Returns:
     *   true if the value was successfully written to the cached JSON structure.
     *
     * Throws:
     *   DatabaseException if the path is invalid, structure mismatched, or queue fails.
     *
     * @param filePath the target JSON file path.
     * @param jsonPath the dot/array path to write.
     * @param value the value to set (JSONObject, JSONArray, or primitive).
     * @param createMissing create missing parents if true; require existing structure if false.
     * @return true if the cached JSON was updated successfully.
     * @throws DatabaseException if the write batch fails or the structure is incompatible.
     */
    public static boolean writeJSONPath(String filePath, String jsonPath, Object value, boolean createMissing)
            throws DatabaseException {

        if (filePath == null || filePath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "writeJSONPath: file path is null or empty.");
            throw new DatabaseException("writeJSONPath: file path is null or empty.");
        }

        final boolean isRoot = isRootPath(jsonPath);
        String pathForLog = isRoot ? "<root>" : "'" + jsonPath + "'";
        Logger.log(Logger.TAG.DEBUG, "Writing value to JSON path " + pathForLog + " in file: " + filePath);

        try {
            QueueManager.Batch batch;

            if (isRoot) {
                // Replace entire document; allow only JSONObject (and null → {}).
                if (value == null) {
                    Logger.log(Logger.TAG.DEBUG, "writeJSONPath(<root>): null → replacing with empty JSONObject {}");
                    batch = BatchManager.buildReplaceRoot(new JSONObject());
                } else if (value instanceof JSONObject obj) {
                    Logger.log(Logger.TAG.DEBUG, "writeJSONPath(<root>): JSONObject replacement, keys=" + obj.keySet().size());
                    batch = BatchManager.buildReplaceRoot(obj);
                } else if (value instanceof org.json.JSONArray) {
                    Logger.log(Logger.TAG.ERROR, "writeJSONPath(<root>): root arrays are not allowed.");
                    throw new DatabaseException("writeJSONPath(<root>): root arrays are not allowed.");
                } else {
                    Logger.log(
                            Logger.TAG.ERROR,
                            "writeJSONPath: root replacement requires JSONObject (got "
                                    + value.getClass().getSimpleName() + ")."
                    );
                    throw new DatabaseException("writeJSONPath(<root>): replacement must be a JSONObject.");
                }

            } else {
                // Normal path write
                batch = BatchManager.buildWriteJSONPath(jsonPath, value, createMissing);
            }

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null, // Loader handled internally
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result)) {
                Logger.log(Logger.TAG.ERROR, "QueueManager reported unsuccessful write for file: " + filePath);
                throw new DatabaseException("writeJSONPath: queue reported unsuccessful application.");
            }

            Logger.log(Logger.TAG.INFO, "Successfully wrote value to JSON path " + pathForLog + " in file: " + filePath);
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "Batch build failed for path " + pathForLog + ": " + e.getMessage());
            throw new DatabaseException("writeJSONPath: batch construction failed for path: " + (isRoot ? "<root>" : jsonPath), e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue error writing file '" + filePath + "': " + e.getMessage());
            throw new DatabaseException("writeJSONPath: queue error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected write error for path " + pathForLog + ": " + e.getMessage());
            throw new DatabaseException("writeJSONPath: unexpected error applying write to cache.", e);
        }
    }

    /**
     * Removes a value at a nested JSON path (supports both object keys and array elements).
     *
     * Behavior:
     *   - Operates on the cached JSON managed by QueueManager.
     *   - Queues a removal batch that executes immediately in memory.
     *   - Disk persistence occurs later during the next flush (delta patch).
     *
     * Returns:
     *   true if removal succeeded and cached JSON updated.
     *
     * Throws:
     *   DatabaseException if invalid path, type mismatch, or queue/batch failure.
     *
     * @param filePath the target JSON file path.
     * @param jsonPath the dot/array path to remove (object key or array element).
     * @return true if the cached JSON was modified.
     * @throws DatabaseException if the path is invalid or the removal batch fails.
     */
    public static boolean removeJSONPath(String filePath, String jsonPath) throws DatabaseException {
        if (filePath == null || filePath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "removeJSONPath: file path is null or empty.");
            throw new DatabaseException("removeJSONPath: file path is null or empty.");
        }

        final boolean isRoot = isRootPath(jsonPath);
        String pathForLog = isRoot ? "<root>" : "'" + jsonPath + "'";
        Logger.log(Logger.TAG.DEBUG, "Removing JSON path " + pathForLog + " from file: " + filePath);

        try {
            QueueManager.Batch batch;

            if (isRoot) {
                // Removing the root → replace entire JSON with an empty object
                Logger.log(Logger.TAG.DEBUG, "removeJSONPath: clearing entire JSON root for file: " + filePath);
                batch = BatchManager.buildReplaceRoot(new JSONObject());
            } else {
                // Standard remove
                batch = BatchManager.buildRemoveJSONPath(jsonPath);
            }

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result)) {
                Logger.log(Logger.TAG.ERROR, "QueueManager failed to apply removal batch for file: " + filePath);
                throw new DatabaseException("removeJSONPath: queue failed to apply removal batch.");
            }

            Logger.log(Logger.TAG.INFO, "Successfully removed JSON path " + pathForLog + " from file: " + filePath);
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "Batch build failed for removal path " + pathForLog + ": " + e.getMessage());
            throw new DatabaseException("removeJSONPath: batch construction failed for path: " + pathForLog, e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue error removing path " + pathForLog + " from file: " + filePath);
            throw new DatabaseException("removeJSONPath: queue error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected error removing JSON path " + pathForLog + ": " + e.getMessage());
            throw new DatabaseException("removeJSONPath: unexpected error applying removal to cache.", e);
        }
    }

    /**
     * Checks whether a JSON path exists inside a file. Works with any nested structure (dot + array syntax).
     *
     * Usage:
     *   boolean exists = DatabaseManager.containsJSONPath("database/global/bot.json", "metadata.owner.name");
     *   boolean valid  = DatabaseManager.containsJSONPath("database/user/123/record.json", "entries[0].notes");
     *
     * Behavior:
     *   - Reads exclusively from the cached JSON object managed by QueueManager.
     *   - If the file is not yet cached, QueueManager loads it once and caches it.
     *   - Traverses the structure according to dot + array notation.
     *   - Returns false for any missing key, invalid type, or malformed path.
     *
     * Returns:
     *   true if the full path exists, false otherwise.
     *
     * Throws:
     *   DatabaseException if the file or path is unreadable, malformed, or cache access fails.
     *
     * @param filePath the JSON file path to inspect.
     * @param jsonPath the dot/array path to check for existence.
     * @return true if the full path exists in the cached structure; false otherwise.
     * @throws DatabaseException if the file cannot be read or the path is malformed.
     */
    public static boolean containsJSONPath(String filePath, String jsonPath) throws DatabaseException {
        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("containsJSONPath: file path is null or empty.");

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final String finalPath = normalizedPath;
        final boolean isRoot = isRootPath(finalPath);
        String pathForLog = isRoot ? "<root>" : finalPath;
        Logger.log(Logger.TAG.DEBUG, "Checking JSON path: " + pathForLog + " in " + filePath);

        try {
            boolean result = QueueManager.readValue(
                    filePath,
                    null, // loader handled internally
                    root -> {
                        try {
                            if (isRoot) {
                                // Root always exists if JSON successfully loaded
                                return root != null;
                            }

                            Object current = root;
                            List<String> tokens = tokenizePath(finalPath);

                            for (String token : tokens) {
                                Segment seg = parseSegment(token);

                                if (seg.baseKey.length() == 0 && !seg.indexes.isEmpty())
                                    return false;

                                // Step 1: descend into object key
                                if (seg.baseKey.length() > 0) {
                                    if (!(current instanceof JSONObject obj))
                                        return false;
                                    if (!obj.has(seg.baseKey))
                                        return false;
                                    current = obj.get(seg.baseKey);
                                }

                                // Step 2: handle any array indexes
                                for (int idx : seg.indexes) {
                                    if (!(current instanceof JSONArray arr))
                                        return false;
                                    if (idx < 0 || idx >= arr.length())
                                        return false;
                                    current = arr.get(idx);
                                }
                            }

                            return true;
                        } catch (Exception e) {
                            return false; // any failure -> not present
                        }
                    }
            );

            Logger.log(Logger.TAG.INFO, "containsJSONPath → " + result + " (" + pathForLog + ")");
            return result;
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue read error in containsJSONPath: " + e.getMessage());
            throw new DatabaseException("containsJSONPath: queue read error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected cache traversal error in containsJSONPath: " + e.getMessage());
            throw new DatabaseException("containsJSONPath: unexpected cache traversal error.", e);
        }
    }

    /**
     * Appends a value to the end of a JSON array at the specified path. Creates the array (and parents) if missing.
     *
     * Usage:
     *   DatabaseManager.appendJSONArray("database/user/123/record.json", "records", newRecord);
     *   DatabaseManager.appendJSONArray("database/user/123/record.json", "records.history",
     *       new JSONObject().put("event", "joined"));
     *
     * Behavior:
     *   - Operates only on the cached JSON copy via QueueManager.
     *   - If the target path does not exist, it is created as an empty array.
     *   - If it exists but is not an array, throws DatabaseException.
     *   - Always appends at the end (no overwrite).
     *   - Disk update occurs during the next queue flush (delta patch).
     *
     * Returns:
     *   true if the append succeeded and the cached copy was updated.
     *
     * Throws:
     *   DatabaseException if file/path invalid, target is not an array, or queue/batch fails.
     *
     * @param filePath the target JSON file path.
     * @param jsonPath the dot/array path of the destination array.
     * @param value the value to append (JSONObject, JSONArray, or primitive).
     * @return true if the cached array was appended successfully.
     * @throws DatabaseException if the path is invalid or the node is not an array.
     */
    public static boolean appendJSONArray(String filePath, String jsonPath, Object value) throws DatabaseException {
        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("appendJSONArray: file path is null or empty.");

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final boolean isRoot = isRootPath(normalizedPath);
        String pathForLog = isRoot ? "<root>" : normalizedPath;

        Logger.log(Logger.TAG.DEBUG, "Appending to JSONArray at path: " + pathForLog + " (" + filePath + ")");

        try {
            if (isRoot)
                throw new DatabaseException("appendJSONArray: root arrays are not allowed.");

            QueueManager.Batch batch = BatchManager.buildAppendJSONArray(normalizedPath, value);

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result)) {
                Logger.log(Logger.TAG.ERROR, "appendJSONArray queue failed for: " + filePath);
                throw new DatabaseException("appendJSONArray: queue failed to apply append batch for: " + filePath);
            }

            Logger.log(Logger.TAG.INFO, "appendJSONArray succeeded for: " + filePath);
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "Batch construction failed in appendJSONArray: " + e.getMessage());
            throw new DatabaseException("appendJSONArray: batch construction failed for path: " + pathForLog, e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue error in appendJSONArray for file: " + filePath);
            throw new DatabaseException("appendJSONArray: queue error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected cache append error in appendJSONArray: " + e.getMessage());
            throw new DatabaseException("appendJSONArray: unexpected error applying append to cache.", e);
        }
    }

    /**
     * Counts the number of elements in a JSON array located at the given path.
     * Supports nested object/array traversal via dot + index notation.
     *
     * Usage:
     *   int count  = DatabaseManager.countJSONArray("database/user/123/record.json", "entries");
     *   int nested = DatabaseManager.countJSONArray("database/user/123/record.json", "entries[0].changes");
     *
     * Behavior:
     *   - Reads exclusively from the cached JSON object managed by QueueManager.
     *   - If the file is not yet cached, QueueManager loads it once and caches it.
     *   - Traverses the structure safely using dot + array syntax.
     *   - Returns 0 for any missing path, negative index, or out-of-bounds index.
     *   - Throws DatabaseException if the final node exists but is not an array.
     *
     * Returns:
     *   int – number of elements (0 if missing or invalid).
     *
     * Throws:
     *   DatabaseException if the file or cache cannot be read, or the final node is not an array.
     *
     * @param filePath the JSON file path.
     * @param jsonPath the dot/array path pointing to the array to count.
     * @return the number of elements in the array (0 if path missing/invalid).
     * @throws DatabaseException if the final node exists but is not an array, or cache access fails.
     */
    public static int countJSONArray(String filePath, String jsonPath) throws DatabaseException {
        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("countJSONArray: file path is null or empty.");

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final String finalPath = normalizedPath;
        final boolean isRoot = isRootPath(finalPath);
        String pathForLog = isRoot ? "<root>" : finalPath;
        Logger.log(Logger.TAG.DEBUG, "Counting JSONArray elements at: " + pathForLog + " (" + filePath + ")");

        try {
            if (isRoot)
                throw new DatabaseException("countJSONArray: root arrays are not allowed.");

            int count = QueueManager.readValue(
                    filePath,
                    null,
                    root -> {
                        try {
                            Object current = root;

                            if (!isRoot) {
                                List<String> tokens = tokenizePath(finalPath);
                                for (String token : tokens) {
                                    Segment seg = parseSegment(token);

                                    if (seg.baseKey.length() == 0 && !seg.indexes.isEmpty())
                                        return 0;

                                    if (seg.baseKey.length() > 0) {
                                        if (!(current instanceof JSONObject obj))
                                            return 0;
                                        if (!obj.has(seg.baseKey))
                                            return 0;
                                        current = obj.get(seg.baseKey);
                                    }

                                    for (int idx : seg.indexes) {
                                        if (!(current instanceof JSONArray arr))
                                            return 0;
                                        if (idx < 0 || idx >= arr.length())
                                            return 0;
                                        current = arr.get(idx);
                                    }
                                }
                            }

                            if (current instanceof JSONArray arr)
                                return arr.length();
                            else
                                throw new DatabaseException("countJSONArray: target is not an array (" + pathForLog + ")");

                        } catch (DatabaseException e) {
                            throw e;
                        } catch (Exception e) {
                            return 0;
                        }
                    }
            );

            Logger.log(Logger.TAG.INFO, "countJSONArray result = " + count + " (" + pathForLog + ")");
            return count;

        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue read error in countJSONArray: " + e.getMessage());
            throw new DatabaseException("countJSONArray: queue read error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected traversal error in countJSONArray: " + e.getMessage());
            throw new DatabaseException("countJSONArray: unexpected cache traversal error.", e);
        }
    }

    /**
     * Lists all immediate keys or indices within the JSON structure at the given path.
     * Works for both objects and arrays.
     *
     * Usage:
     *   List<String> keys = DatabaseManager.listJSONKeys("database/global/bot.json", "metadata");
     *   List<String> idx  = DatabaseManager.listJSONKeys("database/user/123/record.json", "entries[0].details");
     *
     * Behavior:
     *   - Operates only on the cached JSON copy managed by QueueManager.
     *   - If the file is not yet cached, QueueManager loads it from disk once.
     *   - If the path points to an object → returns its field names.
     *   - If the path points to an array  → returns stringified indices.
     *   - Throws DatabaseException if missing or pointing to a primitive.
     *
     * Returns:
     *   A list of field names (object) or indices (array) at the specified path.
     *
     * Throws:
     *   DatabaseException if the path is missing, points to a primitive, or cache access fails.
     *
     * @param filePath the JSON file path.
     * @param jsonPath the dot/array path whose children to enumerate.
     * @return list of immediate keys (for object) or indices (for array).
     * @throws DatabaseException if traversal fails or the node type is unsupported.
     */
    public static List<String> listJSONKeys(String filePath, String jsonPath) throws DatabaseException {
        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("listJSONKeys: file path is null or empty.");

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final String finalPath = normalizedPath;
        final boolean isRoot = isRootPath(finalPath);
        String pathForLog = isRoot ? "<root>" : finalPath;

        Logger.log(Logger.TAG.DEBUG, "Listing JSON keys for file=" + filePath + " path=" + pathForLog);

        try {
            List<String> result = QueueManager.readValue(
                    filePath,
                    null,
                    root -> {
                        Object current = root;

                        if (!isRoot) {
                            List<String> tokens = tokenizePath(finalPath);
                            for (String token : tokens) {
                                Segment seg = parseSegment(token);

                                if (seg.baseKey.length() == 0 && !seg.indexes.isEmpty())
                                    throw new DatabaseException("Array index without key is not allowed: " + token);

                                if (seg.baseKey.length() > 0) {
                                    if (!(current instanceof JSONObject obj))
                                        throw new DatabaseException("Expected object at segment: " + token);
                                    if (!obj.has(seg.baseKey))
                                        throw new DatabaseException("Missing key at segment: " + seg.baseKey);
                                    current = obj.get(seg.baseKey);
                                }

                                for (int idx : seg.indexes) {
                                    if (!(current instanceof JSONArray arr))
                                        throw new DatabaseException("Expected array before index [" + idx + "] in segment: " + token);
                                    if (idx < 0 || idx >= arr.length())
                                        throw new DatabaseException("Index out of bounds [" + idx + "] in segment: " + token);
                                    current = arr.get(idx);
                                }
                            }
                        }

                        List<String> out = new ArrayList<>();
                        if (current instanceof JSONObject obj) {
                            for (String key : obj.keySet()) out.add(key);
                        } else if (current instanceof JSONArray arr) {
                            for (int i = 0; i < arr.length(); i++) out.add(String.valueOf(i));
                        } else {
                            throw new DatabaseException("listJSONKeys: target is not an object or array (" + pathForLog + ")");
                        }

                        return out;
                    }
            );

            Logger.log(Logger.TAG.INFO, "Listed " + result.size() + " keys for " + filePath + "@" + pathForLog);
            return result;

        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue read error in listJSONKeys for " + filePath + ": " + e.getMessage());
            throw new DatabaseException("listJSONKeys: queue read error for file: " + filePath, e);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "listJSONKeys failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected error in listJSONKeys: " + e.getMessage());
            throw new DatabaseException("listJSONKeys: unexpected error traversing cache.", e);
        }
    }

    /**
     * Determines the type of the value at a given JSON path.
     *
     * Usage:
     *   String t1 = DatabaseManager.getTypeAtPath("database/global/bot.json", "case_counter");
     *   String t2 = DatabaseManager.getTypeAtPath("database/global/bot.json", "metadata.logs[0]");
     *
     * Returns:
     *   "object", "array", "string", "number", "boolean", "null", or "unknown".
     *
     * Throws:
     *   DatabaseException if the file cannot be read or the path is malformed.
     *
     * @param filePath the JSON file path.
     * @param jsonPath the dot/array path to inspect.
     * @return the logical type name of the value at that path, or "unknown".
     * @throws DatabaseException if the cache cannot be read or traversal fails.
     */
    public static String getTypeAtPath(String filePath, String jsonPath) throws DatabaseException {
        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("getTypeAtPath: file path is null or empty.");

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final String finalPath = normalizedPath;
        final boolean isRoot = isRootPath(finalPath);
        String pathForLog = isRoot ? "<root>" : finalPath;
        Logger.log(Logger.TAG.DEBUG, "Determining type at " + filePath + "@" + pathForLog);

        try {
            String type = QueueManager.readValue(
                    filePath,
                    null,
                    root -> {
                        Object current = root;

                        if (!isRoot) {
                            List<String> tokens = tokenizePath(finalPath);
                            for (String token : tokens) {
                                Segment seg = parseSegment(token);

                                if (seg.baseKey.length() == 0 && !seg.indexes.isEmpty())
                                    throw new DatabaseException("Array index without key is not allowed: " + token);

                                if (seg.baseKey.length() > 0) {
                                    if (!(current instanceof JSONObject obj))
                                        throw new DatabaseException("Expected object before segment: " + token);
                                    if (!obj.has(seg.baseKey))
                                        throw new DatabaseException("Missing key: " + seg.baseKey);
                                    current = obj.get(seg.baseKey);
                                }

                                for (int idx : seg.indexes) {
                                    if (!(current instanceof JSONArray arr))
                                        throw new DatabaseException("Expected array before index [" + idx + "] in: " + token);
                                    if (idx < 0 || idx >= arr.length())
                                        throw new DatabaseException("Index out of bounds [" + idx + "] in: " + token);
                                    current = arr.get(idx);
                                }
                            }
                        }

                        if (current == null || current == JSONObject.NULL) return "null";
                        if (current instanceof JSONObject) return "object";
                        if (current instanceof JSONArray) return "array";
                        if (current instanceof String) return "string";
                        if (current instanceof Number) return "number";
                        if (current instanceof Boolean) return "boolean";
                        return "unknown";
                    }
            );

            Logger.log(Logger.TAG.INFO, "Detected JSON type at " + pathForLog + " in " + filePath + ": " + type);
            return type;

        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue read error in getTypeAtPath for " + filePath + ": " + e.getMessage());
            throw new DatabaseException("getTypeAtPath: queue read error for file: " + filePath, e);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "getTypeAtPath failed: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected error in getTypeAtPath: " + e.getMessage());
            throw new DatabaseException("getTypeAtPath: unexpected cache traversal error.", e);
        }
    }

    /**
     * Renames a JSON object key at a given path.
     * Operates exclusively on the cached JSON copy managed by QueueManager.
     *
     * Behavior:
     *   - Validates that parentPath resolves to an object.
     *   - Verifies oldKey exists and newKey does not.
     *   - Performs in-cache rename and queues for flush (delta).
     *
     * Returns:
     *   true if rename succeeded in cache.
     *
     * Throws:
     *   DatabaseException for invalid file/path, key conflicts, or queue/batch errors.
     *
     * @param filePath the JSON file path.
     * @param parentPath the object path containing the key to rename ("" or null for root).
     * @param oldKey the existing key name to rename.
     * @param newKey the new key name to assign.
     * @return true if the cached object was modified.
     * @throws DatabaseException if parentPath is not an object, oldKey missing, or newKey exists.
     */
    public static boolean renameJSONKey(String filePath, String parentPath, String oldKey, String newKey)
            throws DatabaseException {

        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("renameJSONKey: file path is null or empty.");
        if (oldKey == null || oldKey.isBlank() || newKey == null || newKey.isBlank())
            throw new DatabaseException("renameJSONKey: invalid key name(s).");
        if (oldKey.equals(newKey))
            throw new DatabaseException("renameJSONKey: oldKey and newKey are identical.");

        final boolean isRoot = isRootPath(parentPath);
        String pathForLog = isRoot ? "<root>" : parentPath;

        Logger.log(Logger.TAG.DEBUG, "Renaming key in file=" + filePath + " path=" + pathForLog
                + " (" + oldKey + " → " + newKey + ")");

        try {
            QueueManager.Batch batch = BatchManager.buildRenameJSONKey(isRoot ? "" : parentPath, oldKey, newKey);

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result)) {
                Logger.log(Logger.TAG.ERROR, "Queue failed to apply rename batch for " + filePath);
                throw new DatabaseException("renameJSONKey: queue failed to apply rename batch for: " + filePath);
            }

            Logger.log(Logger.TAG.INFO, "Renamed key " + oldKey + " → " + newKey + " at " + pathForLog + " in " + filePath);
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "Batch creation failed for renameJSONKey at " + pathForLog + ": " + e.getMessage());
            throw new DatabaseException("renameJSONKey: batch creation failed for path: " + pathForLog, e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue error during renameJSONKey for " + filePath + ": " + e.getMessage());
            throw new DatabaseException("renameJSONKey: queue error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected renameJSONKey error: " + e.getMessage());
            throw new DatabaseException("renameJSONKey: unexpected error during rename operation.", e);
        }
    }

    /**
     * Moves a JSON value from one path to another inside the same file.
     * Safe "cut and paste" performed in-memory on the cached JSON; queued for flush.
     *
     * Behavior:
     *   - Resolves fromPath, reads value, removes it from source.
     *   - Writes the value to toPath (creating parents if needed).
     *   - Ensures type correctness and no collisions for object keys/array indices.
     *
     * Returns:
     *   true if the cached structure was modified.
     *
     * Throws:
     *   DatabaseException for invalid file/path, structural conflicts, or queue/batch errors.
     *
     * @param filePath the JSON file path.
     * @param fromPath the source dot/array path to move from.
     * @param toPath the destination dot/array path to move into.
     * @return true if the move succeeded in cache.
     * @throws DatabaseException if source/destination invalid or batch operations fail.
     */
    public static boolean moveJSONPath(String filePath, String fromPath, String toPath) throws DatabaseException {
        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("moveJSONPath: file path is null or empty.");
        if (fromPath == null || toPath == null)
            throw new DatabaseException("moveJSONPath: fromPath or toPath is null.");

        final boolean fromRoot = isRootPath(fromPath);
        final boolean toRoot = isRootPath(toPath);

        if (fromRoot && toRoot)
            throw new DatabaseException("moveJSONPath: both fromPath and toPath refer to root (invalid).");
        if (fromPath.equals(toPath))
            throw new DatabaseException("moveJSONPath: source and destination paths are identical.");

        Logger.log(Logger.TAG.DEBUG, "moveJSONPath started (" +
                (fromRoot ? "<root>" : fromPath) + " → " + (toRoot ? "<root>" : toPath) +
                ") in file: " + filePath);

        try {
            Object value = QueueManager.readValue(
                    filePath,
                    null,
                    root -> {
                        try {
                            return fromRoot ? root : readJSONPath(filePath, fromPath);
                        } catch (Exception e) {
                            Logger.log(Logger.TAG.ERROR, "Failed to read value at source path: " + fromPath);
                            throw new DatabaseException("moveJSONPath: failed to read value from source path: " + fromPath, e);
                        }
                    }
            );

            if (value == null || value == JSONObject.NULL)
                throw new DatabaseException("moveJSONPath: no value found at source path: " +
                        (fromRoot ? "<root>" : fromPath));

            QueueManager.Batch batch = BatchManager.buildMoveJSONPath(
                    fromRoot ? "" : fromPath,
                    toRoot ? "" : toPath,
                    value
            );

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result)) {
                Logger.log(Logger.TAG.ERROR, "Queue failed to apply move batch for: " + filePath);
                throw new DatabaseException("moveJSONPath: queue failed to apply move batch for: " + filePath);
            }

            Logger.log(Logger.TAG.INFO, "moveJSONPath successful (" +
                    (fromRoot ? "<root>" : fromPath) + " → " + (toRoot ? "<root>" : toPath) +
                    ") in file: " + filePath);
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "Batch construction failed (" +
                    (fromRoot ? "<root>" : fromPath) + " → " + (toRoot ? "<root>" : toPath) +
                    "): " + e.getMessage());
            throw new DatabaseException("moveJSONPath: batch construction failed for fromPath: " +
                    (fromRoot ? "<root>" : fromPath) + " → " + (toRoot ? "<root>" : toPath), e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue operation failed for file: " + filePath + " (" + e.getMessage() + ")");
            throw new DatabaseException("moveJSONPath: queue operation failed for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected cache-level error during move operation: " + e.getMessage());
            throw new DatabaseException("moveJSONPath: unexpected cache-level error during move operation.", e);
        }
    }

    /**
     * Sanitizes a JSON file by removing null, empty, or redundant fields.
     * Operates exclusively on the cached JSON copy; enqueues changes if any.
     *
     * Behavior:
     *   - Walks the tree and removes: nulls, empty arrays, empty objects (configurable for arrays).
     *   - If fixArrays=true, also compacts arrays where appropriate.
     *   - Only touches cache; disk changes occur on flush (delta patch).
     *
     * Returns:
     *   true if any changes were applied to cache.
     *
     * Throws:
     *   DatabaseException if file invalid or queue/batch operations fail.
     *
     * @param filePath the JSON file path to sanitize.
     * @param fixArrays whether to compact/normalize arrays during sanitation.
     * @return true if sanitation modified the cached structure.
     * @throws DatabaseException if cache access or write-back batch fails.
     */
    public static boolean sanitizeJSON(String filePath, boolean fixArrays) throws DatabaseException {
        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("sanitizeJSON: file path is null or empty.");

        Logger.log(Logger.TAG.DEBUG, "sanitizeJSON started for file: " + filePath);

        try {
            return QueueManager.readValue(
                    filePath,
                    null,
                    root -> {
                        // Work on a defensive clone so we can diff safely
                        final JSONObject original = (root instanceof JSONObject)
                                ? new JSONObject(root.toString())
                                : new JSONObject(); // enforce object root for sanitation

                        SanitizeResult sr = trySanitizeSnapshot(original, fixArrays);

                        if (sr.modified) {
                            Logger.log(Logger.TAG.DEBUG, "sanitizeJSON detected changes, queuing replaceRoot for: " + filePath);
                            try {
                                QueueManager.Batch batch = BatchManager.buildReplaceRoot(sr.snapshot);
                                QueueManager.enqueueBatchAndGet(filePath, null, batch, json -> true);

                                // Materialize immediately after a successful sanitation repair
                                try {
                                    QueueManager.flushFile(filePath, /*materialize=*/true);
                                    Logger.log(Logger.TAG.INFO, "sanitizeJSON: materialized repaired base for " + filePath);
                                } catch (QueueException qe) {
                                    Logger.log(Logger.TAG.ERROR, "sanitizeJSON: materialize failed after repair for " + filePath + " — " + qe.getMessage());
                                    throw new DatabaseException("sanitizeJSON: materialize failed after repair.", qe);
                                }

                            } catch (Exception e) {
                                Logger.log(Logger.TAG.ERROR, "Failed to enqueue sanitized structure for: " + filePath);
                                throw new DatabaseException("sanitizeJSON: failed to enqueue sanitized structure.", e);
                            }
                        } else {
                            Logger.log(Logger.TAG.DEBUG, "sanitizeJSON found no changes for: " + filePath);
                        }

                        return sr.modified;
                    }
            );
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue read error during sanitizeJSON for: " + filePath);
            throw new DatabaseException("sanitizeJSON: queue read error for file: " + filePath, e);
        } catch (DatabaseException e) {
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected cache traversal error during sanitizeJSON for: " + filePath);
            throw new DatabaseException("sanitizeJSON: unexpected cache traversal error.", e);
        }
    }

    /**
     * INTERNAL — Recursively sanitizes a JSON node in memory.
     * Does not perform any disk I/O or queue interaction.
     */
    @SuppressWarnings("unchecked")
    private static boolean sanitizeNode(Object node, boolean fixArrays) {
        boolean modified = false;

        if (node instanceof JSONObject obj) {
            List<String> removeKeys = new ArrayList<>();

            for (String key : obj.keySet()) {
                Object val = obj.get(key);
                if (val == null || val == JSONObject.NULL ||
                        (val instanceof String s && s.isBlank())) {
                    removeKeys.add(key);
                    modified = true;
                    continue;
                }

                boolean subChanged = sanitizeNode(val, fixArrays);
                if (subChanged) modified = true;

                if (val instanceof JSONObject subObj && subObj.isEmpty()) {
                    removeKeys.add(key);
                    modified = true;
                }
                if (val instanceof JSONArray subArr && subArr.isEmpty()) {
                    removeKeys.add(key);
                    modified = true;
                }
            }

            for (String k : removeKeys) obj.remove(k);

        } else if (node instanceof JSONArray arr) {
            List<Object> cleaned = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                Object val = arr.get(i);
                if (val == null || val == JSONObject.NULL) {
                    modified = true;
                    continue;
                }
                boolean subChanged = sanitizeNode(val, fixArrays);
                if (subChanged) modified = true;
                cleaned.add(val);
            }

            if (fixArrays && cleaned.size() != arr.length()) {
                // org.json.JSONArray has no clear(); rebuild safely
                for (int i = arr.length() - 1; i >= 0; i--) arr.remove(i);
                for (Object v : cleaned) arr.put(v);
                modified = true;
            }
        }

        return modified;
    }

    /**
     * INTERNAL HELPER — Ensures a JSON path exists, otherwise throws DatabaseException
     * with a detailed trace of where traversal failed.
     *
     * Returns:
     *   Object (the resolved value at that path).
     *
     * Throws:
     *   DatabaseException if path or structure invalid.
     *
     * @param root the JSONObject root to traverse.
     * @param jsonPath the dot/array path to resolve.
     * @return the resolved node (JSONObject, JSONArray, primitive, or null).
     * @throws DatabaseException if any segment is missing or type constraints are violated.
     */
    public static Object pathExistsOrThrow(JSONObject root, String jsonPath) throws DatabaseException {
        if (root == null)
            throw new DatabaseException("pathExistsOrThrow: root JSONObject is null.");

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final boolean isRoot = isRootPath(normalizedPath);
        String pathForLog = isRoot ? "<root>" : normalizedPath;

        Logger.log(Logger.TAG.DEBUG, "pathExistsOrThrow checking path: " + pathForLog);

        if (isRoot) {
            // Root itself always exists
            Logger.log(Logger.TAG.DEBUG, "pathExistsOrThrow: root path valid by default.");
            return root;
        }

        Object current = root;
        List<String> tokens = tokenizePath(jsonPath);
        StringBuilder trace = new StringBuilder();

        for (String token : tokens) {
            Segment seg = parseSegment(token);
            trace.append(token).append(".");

            if (seg.baseKey.length() > 0) {
                if (!(current instanceof JSONObject obj))
                    throw new DatabaseException("Expected object before segment '" + token + "' at path: " + trace);
                if (!obj.has(seg.baseKey))
                    throw new DatabaseException("Missing key '" + seg.baseKey + "' at path: " + trace);
                current = obj.get(seg.baseKey);
            }

            for (int idx : seg.indexes) {
                if (!(current instanceof JSONArray arr))
                    throw new DatabaseException("Expected array before index [" + idx + "] at path: " + trace);
                if (idx < 0 || idx >= arr.length())
                    throw new DatabaseException("Index out of bounds [" + idx + "] at path: " + trace);
                current = arr.get(idx);
            }
        }

        Logger.log(Logger.TAG.DEBUG, "pathExistsOrThrow validated successfully: " + pathForLog);
        return current;
    }

    /**
     * Recursively prints all accessible paths in a JSON structure with indentation.
     * Useful for debugging and introspection of arbitrary nested data.
     *
     * Usage:
     *   DatabaseManager.printJSONTree("database/global/bot.json");
     *   DatabaseManager.printJSONTree("database/user/123/record.json");
     *
     * Behavior:
     *   - Operates entirely on the cached JSON copy (no direct file reads).
     *   - Prints each key path; supports arrays and nested objects.
     *   - Never modifies the data.
     *
     * Throws:
     *   DatabaseException if cache cannot be read or JSON structure malformed.
     *
     * @param filePath the JSON file path to visualize.
     * @throws DatabaseException if the cached document cannot be read or is malformed.
     */
    public static void printJSONTree(String filePath) throws DatabaseException {
        Logger.log(Logger.TAG.DEBUG, "printJSONTree called for: " + filePath);

        String output = buildJSONTree(filePath, false);
        System.out.print(output);

        Logger.log(Logger.TAG.INFO, "Printed JSON tree for " + filePath);
    }

    /**
     * Returns a visualized tree as a formatted String, without printing.
     *
     * Behavior:
     *   • Reads exclusively from cached JSON via QueueManager.
     *   • Loads if uncached.
     *   • Generates a formatted hierarchical view.
     *
     * Throws:
     *   DatabaseException if cache read fails or JSON invalid.
     */
    public static String buildJSONTree(String filePath, boolean compact) throws DatabaseException {
        if (filePath == null || filePath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "buildJSONTree: file path null or empty.");
            throw new DatabaseException("buildJSONTree: file path is null or empty.");
        }

        Logger.log(Logger.TAG.DEBUG, "Building JSON tree for: " + filePath);

        try {
            String result = QueueManager.readValue(
                    filePath,
                    null,
                    root -> {
                        StringBuilder sb = new StringBuilder();
                        traverseJSONNode(root, "", sb, compact ? "  " : "│  ");
                        return sb.toString();
                    }
            );
            Logger.log(Logger.TAG.INFO, "JSON tree built successfully for: " + filePath);
            return result;

        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "Queue read error while building JSON tree for " + filePath);
            throw new DatabaseException("buildJSONTree: queue read error for file: " + filePath, e);
        } catch (DatabaseException e) {
            Logger.log(Logger.TAG.ERROR, "DatabaseException while building JSON tree: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Unexpected error during buildJSONTree: " + e.getMessage());
            throw new DatabaseException("buildJSONTree: unexpected error while generating JSON tree.", e);
        }
    }

    /**
     * INTERNAL — Recursively traverses a JSON node and appends a tree-style representation.
     * Purely in-memory operation (no I/O).
     */
    private static void traverseJSONNode(Object node, String prefix, StringBuilder sb, String indent) {
        if (node instanceof JSONObject obj) {
            for (String key : obj.keySet()) {
                Object val = obj.get(key);
                String path = prefix.isEmpty() ? key : prefix + "." + key;

                if (val instanceof JSONObject || val instanceof JSONArray) {
                    sb.append(path).append("\n");
                    traverseJSONNode(val, path, sb, indent + "  ");
                } else {
                    sb.append(path).append(" : ").append(previewValue(val)).append("\n");
                }
            }
        } else if (node instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) {
                Object val = arr.get(i);
                String path = prefix + "[" + i + "]";
                if (val instanceof JSONObject || val instanceof JSONArray) {
                    sb.append(path).append("\n");
                    traverseJSONNode(val, path, sb, indent + "  ");
                } else {
                    sb.append(path).append(" : ").append(previewValue(val)).append("\n");
                }
            }
        }
    }

    /**
     * INTERNAL — Produces a short printable preview for a primitive JSON value.
     */
    private static String previewValue(Object val) {
        if (val == null || val == JSONObject.NULL) return "null";
        if (val instanceof String s) return "\"" + s + "\"";
        if (val instanceof Number || val instanceof Boolean) return val.toString();
        return String.valueOf(val);
    }

    /**
     * Clears all elements from a JSON array located at the given path.
     *
     * Behavior:
     *   - Operates only on the cached JSON via QueueManager.
     *   - If path invalid or type mismatch, throws DatabaseException.
     *   - Queues the change for deferred disk flush.
     *
     * Returns:
     *   true if the array was cleared in cache.
     *
     * Throws:
     *   DatabaseException if file/path invalid, type mismatch, or queue/batch fails.
     *
     * @param filePath the JSON file path.
     * @param jsonPath the dot/array path of the array to clear.
     * @return true if the cached array was cleared.
     * @throws DatabaseException if the node is missing or not an array.
     */
    public static boolean clearJSONArray(String filePath, String jsonPath) throws DatabaseException {
        if (filePath == null || filePath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "clearJSONArray: file path null or empty.");
            throw new DatabaseException("clearJSONArray: file path is null or empty.");
        }

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final boolean isRoot = isRootPath(normalizedPath);
        String pathForLog = isRoot ? "<root>" : normalizedPath;

        Logger.log(Logger.TAG.DEBUG, "Clearing JSONArray at " + pathForLog + " in file " + filePath);

        try {
            if (isRoot)
                throw new DatabaseException("clearJSONArray: root arrays are not allowed.");

            QueueManager.Batch batch = BatchManager.buildWriteJSONPath(jsonPath, new JSONArray(), false);

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result)) {
                Logger.log(Logger.TAG.ERROR, "clearJSONArray: queue failed for " + filePath);
                throw new DatabaseException("clearJSONArray: queue failed to apply array clear batch for: " + filePath);
            }

            Logger.log(Logger.TAG.INFO, "JSONArray cleared successfully at " + pathForLog + " (" + filePath + ")");
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "clearJSONArray: batch creation failed (" + pathForLog + ")");
            throw new DatabaseException("clearJSONArray: batch creation failed for path: " + pathForLog, e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "clearJSONArray: queue error for file " + filePath);
            throw new DatabaseException("clearJSONArray: queue error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "clearJSONArray: unexpected cache-level error: " + e.getMessage());
            throw new DatabaseException("clearJSONArray: unexpected cache-level error during array clear.", e);
        }
    }

    /**
     * Clears all keys from a JSON object located at the given path.
     *
     * Behavior:
     *   - Operates only on cached JSON copy via QueueManager.
     *   - If path invalid or not an object, throws DatabaseException.
     *   - Queues a batch that replaces the object with {} (empty object).
     *   - File is updated on next queue flush.
     *
     * Returns:
     *   true if the object was cleared in cache.
     *
     * Throws:
     *   DatabaseException if file/path invalid, type mismatch, or queue/batch fails.
     *
     * @param filePath the JSON file path.
     * @param jsonPath the dot path of the object to clear.
     * @return true if the cached object was emptied.
     * @throws DatabaseException if the node is missing or not an object.
     */
    public static boolean clearJSONObject(String filePath, String jsonPath) throws DatabaseException {
        if (filePath == null || filePath.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "clearJSONObject: file path null or empty.");
            throw new DatabaseException("clearJSONObject: file path is null or empty.");
        }

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final boolean isRoot = isRootPath(normalizedPath);
        String pathForLog = isRoot ? "<root>" : normalizedPath;

        Logger.log(Logger.TAG.DEBUG, "Clearing JSONObject at " + pathForLog + " in file " + filePath);

        try {
            QueueManager.Batch batch;

            if (isRoot) {
                Logger.log(Logger.TAG.DEBUG, "clearJSONObject: clearing root-level object for file: " + filePath);
                batch = BatchManager.buildReplaceRoot(new JSONObject());
            } else {
                batch = BatchManager.buildWriteJSONPath(jsonPath, new JSONObject(), false);
            }

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result)) {
                Logger.log(Logger.TAG.ERROR, "clearJSONObject: queue failed for " + filePath);
                throw new DatabaseException("clearJSONObject: queue failed to apply object clear batch for: " + filePath);
            }

            Logger.log(Logger.TAG.INFO, "JSONObject cleared successfully at " + pathForLog + " (" + filePath + ")");
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "clearJSONObject: batch creation failed (" + pathForLog + ")");
            throw new DatabaseException("clearJSONObject: batch creation failed for path: " + pathForLog, e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "clearJSONObject: queue error for file " + filePath);
            throw new DatabaseException("clearJSONObject: queue error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "clearJSONObject: unexpected cache-level error: " + e.getMessage());
            throw new DatabaseException("clearJSONObject: unexpected cache-level error during object clear.", e);
        }
    }

    /**
     * Inserts a value into a JSON array at a specific index. Shifts elements right to make room.
     *
     * Usage:
     *   DatabaseManager.insertJSONArray("database/user/123/record.json", "entries", 1, newEntry);
     *
     * Behavior:
     *   - Operates entirely on the cached JSON copy via QueueManager.
     *   - If the target path does not exist, it is created as an empty array.
     *   - If the target exists but is not an array, throws DatabaseException.
     *   - If index > current length, throws DatabaseException (no sparse inserts).
     *   - Enqueues the change and updates the cached JSON immediately.
     *   - Disk is updated later during flush.
     *
     * Returns:
     *   true if the insertion succeeded and the cached copy was updated.
     *
     * Throws:
     *   DatabaseException if file/path invalid, type mismatch, index invalid, or queue/batch fails.
     *
     * @param filePath the JSON file path.
     * @param jsonPath the dot/array path of the destination array.
     * @param index the position at which to insert (0..length).
     * @param value the value to insert.
     * @return true if the array was modified in cache.
     * @throws DatabaseException if the node is not an array or index is invalid.
     */
    public static boolean insertJSONArray(String filePath, String jsonPath, int index, Object value)
            throws DatabaseException {

        Logger.log(Logger.TAG.DEBUG,
                "insertJSONArray called on file: " + filePath + " at path: " + (jsonPath == null || jsonPath.isBlank() ? "<root>" : jsonPath) +
                        " index=" + index);

        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("insertJSONArray: file path is null or empty.");
        if (index < 0)
            throw new DatabaseException("insertJSONArray: index cannot be negative.");

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final boolean isRoot = isRootPath(normalizedPath);
        String pathForLog = isRoot ? "<root>" : normalizedPath;

        try {
            if (isRoot)
                throw new DatabaseException("insertJSONArray: root arrays are not allowed.");

            QueueManager.Batch batch = BatchManager.buildInsertJSONArray(jsonPath, index, value);

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result))
                throw new DatabaseException("insertJSONArray: queue failed to apply insert batch for: " + filePath);

            Logger.log(Logger.TAG.INFO, "insertJSONArray succeeded for file: " + filePath + " at " + pathForLog + " index " + index);
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "insertJSONArray batch construction failed: " + e.getMessage());
            throw new DatabaseException("insertJSONArray: batch construction failed for path: " + pathForLog, e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "insertJSONArray queue error: " + e.getMessage());
            throw new DatabaseException("insertJSONArray: queue error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "insertJSONArray unexpected error: " + e.getMessage());
            throw new DatabaseException("insertJSONArray: unexpected cache-level error during array insert.", e);
        }
    }

    /**
     * Replaces an existing element in a JSON array at the specified index.
     *
     * Usage:
     *   DatabaseManager.replaceJSONArray("database/user/123/record.json", "entries", 0, updatedEntry);
     *
     * Behavior:
     *   - Operates on the cached JSON copy via QueueManager.
     *   - If the target path does not exist or is not an array, throws DatabaseException.
     *   - If index is out of bounds, throws DatabaseException.
     *   - Replaces the existing element in cache immediately and enqueues for flush.
     *
     * Returns:
     *   true if replacement succeeded and cached copy updated.
     *
     * Throws:
     *   DatabaseException if file/path invalid, index invalid, type mismatch, or queue/batch fails.
     *
     * @param filePath the JSON file path.
     * @param jsonPath the dot/array path of the destination array.
     * @param index the array index to replace (0..length-1).
     * @param value the new element to place at the index.
     * @return true if the array element was replaced in cache.
     * @throws DatabaseException if the node is not an array or index is invalid.
     */
    public static boolean replaceJSONArray(String filePath, String jsonPath, int index, Object value)
            throws DatabaseException {

        Logger.log(Logger.TAG.DEBUG,
                "replaceJSONArray called on file: " + filePath + " at path: " + (jsonPath == null || jsonPath.isBlank() ? "<root>" : jsonPath) +
                        " index=" + index);

        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("replaceJSONArray: file path is null or empty.");
        if (index < 0)
            throw new DatabaseException("replaceJSONArray: index cannot be negative.");

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final boolean isRoot = isRootPath(normalizedPath);
        String pathForLog = isRoot ? "<root>" : normalizedPath;

        try {
            if (isRoot)
                throw new DatabaseException("replaceJSONArray: root arrays are not allowed.");

            QueueManager.Batch batch = BatchManager.buildReplaceJSONArray(jsonPath, index, value);

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result))
                throw new DatabaseException("replaceJSONArray: queue failed to apply replace batch for: " + filePath);

            Logger.log(Logger.TAG.INFO, "replaceJSONArray succeeded for file: " + filePath + " at " + pathForLog + " index " + index);
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "replaceJSONArray batch construction failed: " + e.getMessage());
            throw new DatabaseException("replaceJSONArray: batch construction failed for path: " + pathForLog, e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "replaceJSONArray queue error: " + e.getMessage());
            throw new DatabaseException("replaceJSONArray: queue error for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "replaceJSONArray unexpected error: " + e.getMessage());
            throw new DatabaseException("replaceJSONArray: unexpected cache-level error during array replace.", e);
        }
    }

    /**
     * Searches for an element inside a JSON array located at the given path.
     * Returns the first element matching the provided key/value pair.
     *
     * Usage:
     *   Object match = DatabaseManager.findJSONArray("database/user/123/record.json", "entries", "id", 42);
     *
     * Behavior:
     *   - Operates only on the cached JSON copy via QueueManager.
     *   - Throws DatabaseException if the path does not exist or is not an array.
     *   - Iterates through elements and returns first matching element.
     *
     * Returns:
     *   The first matching element, or null if not found.
     *
     * Throws:
     *   DatabaseException if file/path invalid, not an array, or cache traversal fails.
     *
     * @param filePath the JSON file path.
     * @param jsonPath the dot/array path of the array to search.
     * @param keyName the key to check within each array element (object elements expected).
     * @param targetValue the value to match against keyName.
     * @return the first matching element, or null if none.
     * @throws DatabaseException if the node is missing or not an array.
     */
    public static Object findJSONArray(String filePath, String jsonPath, String keyName, Object targetValue)
            throws DatabaseException {

        Logger.log(Logger.TAG.DEBUG, "findJSONArray called on file: " + filePath + " at path: " +
                (jsonPath == null || jsonPath.isBlank() ? "<root>" : jsonPath));

        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("findJSONArray: file path is null or empty.");

        String normalizedPath = jsonPath;
        if (normalizedPath != null) {
            String trimmed = normalizedPath.trim();
            if (trimmed.endsWith("[]")) {
                normalizedPath = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        final boolean isRoot = isRootPath(normalizedPath);
        String pathForLog = isRoot ? "<root>" : normalizedPath;

        try {
            if (isRoot)
                throw new DatabaseException("findJSONArray: root arrays are not allowed.");

            Object result = QueueManager.readValue(
                    filePath,
                    null,
                    root -> {
                        Object current = root;

                        if (!isRoot) {
                            List<String> tokens = tokenizePath(jsonPath);
                            for (String token : tokens) {
                                Segment seg = parseSegment(token);

                                if (seg.baseKey.length() > 0) {
                                    if (!(current instanceof JSONObject obj))
                                        throw new DatabaseException("Expected object at segment: " + token);
                                    if (!obj.has(seg.baseKey))
                                        throw new DatabaseException("Missing key: " + seg.baseKey);
                                    current = obj.get(seg.baseKey);
                                }

                                for (int idx : seg.indexes) {
                                    if (!(current instanceof JSONArray arr))
                                        throw new DatabaseException("Expected array before index [" + idx + "] in: " + token);
                                    if (idx < 0 || idx >= arr.length())
                                        throw new DatabaseException("Index out of bounds [" + idx + "] in: " + token);
                                    current = arr.get(idx);
                                }
                            }
                        }

                        if (!(current instanceof JSONArray arr))
                            throw new DatabaseException("findJSONArray: target path is not an array: " + pathForLog);

                        for (int i = 0; i < arr.length(); i++) {
                            Object element = arr.get(i);

                            if (keyName == null || keyName.isBlank()) {
                                if (Objects.equals(element, targetValue))
                                    return element;
                            } else if (element instanceof JSONObject obj && obj.has(keyName)) {
                                Object val = obj.get(keyName);
                                if (Objects.equals(val, targetValue))
                                    return element;
                            }
                        }
                        return null;
                    }
            );

            if (result != null)
                Logger.log(Logger.TAG.INFO, "findJSONArray found matching element in " + filePath + "@" + pathForLog);
            else
                Logger.log(Logger.TAG.DEBUG, "findJSONArray no match found in " + filePath + "@" + pathForLog);

            return result;

        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "findJSONArray queue read error: " + e.getMessage());
            throw new DatabaseException("findJSONArray: queue read error for file: " + filePath, e);
        } catch (DatabaseException e) {
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "findJSONArray unexpected error: " + e.getMessage());
            throw new DatabaseException("findJSONArray: unexpected error during cache search.", e);
        }
    }

    /**
     * Copies a value from one JSON path to another inside the same file.
     * Unlike moveJSONPath, the source value remains intact.
     *
     * Usage:
     *   DatabaseManager.copyJSONPath("database/global/bot.json",
     *       "metadata.owner.name", "metadata.backup_name");
     *
     * Behavior:
     *   - Operates entirely on cached JSON via QueueManager.
     *   - Reads the value from fromPath and writes it to toPath.
     *   - Does not remove the source.
     *   - Throws DatabaseException if structure invalid, types mismatch, or queue fails.
     *   - Disk updated later at flush.
     *
     * Returns:
     *   true if copy succeeded and cached JSON updated.
     *
     * Throws:
     *   DatabaseException if paths invalid, value missing, or queue/batch fails.
     *
     * @param filePath the JSON file path.
     * @param fromPath the source dot/array path to copy from.
     * @param toPath the destination dot/array path to copy into (parents may be created).
     * @return true if the destination was written in cache.
     * @throws DatabaseException if traversal fails or the write batch cannot be applied.
     */
    public static boolean copyJSONPath(String filePath, String fromPath, String toPath) throws DatabaseException {
        Logger.log(Logger.TAG.DEBUG, "copyJSONPath called on file: " + filePath + " from " +
                (fromPath == null || fromPath.isBlank() ? "<root>" : fromPath) +
                " → " + (toPath == null || toPath.isBlank() ? "<root>" : toPath));

        if (filePath == null || filePath.isBlank())
            throw new DatabaseException("copyJSONPath: file path is null or empty.");
        if (fromPath == null || toPath == null)
            throw new DatabaseException("copyJSONPath: fromPath or toPath is null.");

        final boolean fromRoot = isRootPath(fromPath);
        final boolean toRoot = isRootPath(toPath);

        if (fromRoot && toRoot)
            throw new DatabaseException("copyJSONPath: both source and destination paths refer to root (invalid).");
        if (fromPath.equals(toPath))
            throw new DatabaseException("copyJSONPath: source and destination paths are identical.");

        try {
            Object value = QueueManager.readValue(
                    filePath,
                    null,
                    root -> {
                        try {
                            return fromRoot ? root : readJSONPath(filePath, fromPath);
                        } catch (Exception e) {
                            throw new DatabaseException("copyJSONPath: failed to read value from source path: " +
                                    (fromRoot ? "<root>" : fromPath), e);
                        }
                    }
            );

            if (value == null || value == JSONObject.NULL)
                throw new DatabaseException("copyJSONPath: no value found at source path: " +
                        (fromRoot ? "<root>" : fromPath));

            QueueManager.Batch batch = BatchManager.buildWriteJSONPath(toRoot ? "" : toPath, value, true);

            Boolean result = QueueManager.enqueueBatchAndGet(
                    filePath,
                    null,
                    batch,
                    json -> true
            );

            if (!Boolean.TRUE.equals(result))
                throw new DatabaseException("copyJSONPath: queue failed to apply copy batch for: " + filePath);

            Logger.log(Logger.TAG.INFO, "copyJSONPath succeeded for " + filePath + " (" +
                    (fromRoot ? "<root>" : fromPath) + " → " + (toRoot ? "<root>" : toPath) + ")");
            return true;

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "copyJSONPath batch build failed: " + e.getMessage());
            throw new DatabaseException("copyJSONPath: batch construction failed (" +
                    (fromRoot ? "<root>" : fromPath) + " → " + (toRoot ? "<root>" : toPath) + ")", e);
        } catch (QueueException e) {
            Logger.log(Logger.TAG.ERROR, "copyJSONPath queue error: " + e.getMessage());
            throw new DatabaseException("copyJSONPath: queue operation failed for file: " + filePath, e);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "copyJSONPath unexpected error: " + e.getMessage());
            throw new DatabaseException("copyJSONPath: unexpected cache-level error during copy operation.", e);
        }
    }

// =====================
// Helper Methods
// =====================

    private static boolean isRootPath(String jsonPath) {
        return jsonPath == null || jsonPath.isBlank() || "x".equalsIgnoreCase(jsonPath.trim());
    }

    /** Regular expression for parsing array indexes in JSON path segments. */
    private static final java.util.regex.Pattern IDX = java.util.regex.Pattern.compile("\\[(\\d+)]");

    /**
     * Tokenizes a JSON path into segments while supporting:
     * - dot separators: a.b.c
     * - array indexes: a[0].b
     * - bracketed keys: a['key.with.dots'] or a["key"]
     * - escaped characters: a\\.b -> key "a.b"
     *
     * This is a strict parser; malformed paths throw DatabaseException.
     */
    private static List<String> tokenizePath(String path) throws DatabaseException {
        if (path == null || path.isBlank()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < path.length()) {
            char c = path.charAt(i);

            if (c == '\\') {
                if (i + 1 >= path.length()) {
                    throw new DatabaseException("Malformed path: trailing escape in '" + path + "'");
                }
                current.append(path.charAt(i + 1));
                i += 2;
                continue;
            }

            if (c == '.') {
                if (current.length() == 0) {
                    throw new DatabaseException("Malformed path: empty segment in '" + path + "'");
                }
                tokens.add(current.toString());
                current.setLength(0);
                i++;
                continue;
            }

            if (c == '[') {
                if (i + 1 >= path.length()) {
                    throw new DatabaseException("Malformed path: unterminated bracket in '" + path + "'");
                }

                char next = path.charAt(i + 1);
                if (next == '\'' || next == '"') {
                    char quote = next;
                    i += 2;
                    StringBuilder key = new StringBuilder();
                    boolean closed = false;
                    while (i < path.length()) {
                        char ch = path.charAt(i);
                        if (ch == '\\') {
                            if (i + 1 >= path.length()) {
                                throw new DatabaseException("Malformed path: bad escape in '" + path + "'");
                            }
                            key.append(path.charAt(i + 1));
                            i += 2;
                            continue;
                        }
                        if (ch == quote) {
                            closed = true;
                            i++;
                            break;
                        }
                        key.append(ch);
                        i++;
                    }
                    if (!closed || i >= path.length() || path.charAt(i) != ']') {
                        throw new DatabaseException("Malformed path: unterminated quoted key in '" + path + "'");
                    }
                    i++; // consume ]

                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    current.append(key);
                    continue;
                }

                int j = i + 1;
                while (j < path.length() && Character.isWhitespace(path.charAt(j))) j++;
                int start = j;
                while (j < path.length() && Character.isDigit(path.charAt(j))) j++;
                if (start == j) {
                    throw new DatabaseException("Malformed path: non-numeric index in '" + path + "'");
                }
                String idx = path.substring(start, j);
                while (j < path.length() && Character.isWhitespace(path.charAt(j))) j++;
                if (j >= path.length() || path.charAt(j) != ']') {
                    throw new DatabaseException("Malformed path: unterminated index in '" + path + "'");
                }
                current.append('[').append(idx).append(']');
                i = j + 1;
                continue;
            }

            current.append(c);
            i++;
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /**
     * Parses an individual path segment into a base key and optional array indexes.
     *
     * Example:
     *   "records[0][1]" → baseKey="records", indexes=[0,1]
     *
     * @param token a single JSON path segment
     * @return a Segment representing this path segment
     * @throws DatabaseException if the segment is malformed or contains invalid indexes
     */
    private static Segment parseSegment(String token) throws DatabaseException {
        if (token == null || token.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "parseSegment: malformed path (empty segment)");
            throw new DatabaseException("Malformed path: empty segment");
        }

        String base = token.split("\\[")[0];
        List<Integer> idxs = new ArrayList<>();
        java.util.regex.Matcher m = IDX.matcher(token);
        while (m.find()) {
            try {
                idxs.add(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException nfe) {
                Logger.log(Logger.TAG.ERROR, "Malformed array index in segment '" + token + "'");
                throw new DatabaseException("Malformed array index in segment '" + token + "'", nfe);
            }
        }

        Logger.log(Logger.TAG.DEBUG, "Parsed segment: " + base + " (" + idxs.size() + " indexes)");
        return new Segment(base, idxs);
    }

    /**
     * Ensures that a JSONArray is large enough to contain a target index.
     *
     * Automatically fills missing positions with JSONObject.NULL.
     *
     * @param array       the JSONArray to expand
     * @param targetIndex the minimum index to ensure
     */
    public static void ensureArraySize(JSONArray array, int targetIndex) {
        while (array.length() <= targetIndex) array.put(JSONObject.NULL);
    }

    /**
     * Wraps Java null values into JSONObject.NULL for explicit JSON representation.
     *
     * @param v value to normalize
     * @return the normalized JSON-compatible value
     */
    private static Object wrapValue(Object v) {
        return (v == null) ? JSONObject.NULL : v;
    }

    /** Represents a parsed JSON path segment. */
    private static final class Segment {
        final String baseKey;
        final List<Integer> indexes;
        Segment(String baseKey, List<Integer> indexes) {
            this.baseKey = baseKey == null ? "" : baseKey;
            this.indexes = indexes;
        }
    }

    /** Holds the result of integrity validation for a JSON structure. */
    public static final class IntegrityReport {
        public final boolean valid;
        public final boolean modified;
        public final String topType; // "object" or "array"
        public final List<String> messages = new ArrayList<>();

        public IntegrityReport(boolean valid, boolean modified, String topType) {
            this.valid = valid; this.modified = modified; this.topType = topType;
        }

        public IntegrityReport add(String msg) { this.messages.add(msg); return this; }
    }

    /** Result holder for in-memory sanitation attempts. */
    public static final class SanitizeResult {
        public final JSONObject snapshot; // sanitized clone
        public final boolean modified;

        public SanitizeResult(JSONObject snapshot, boolean modified) {
            this.snapshot = snapshot;
            this.modified = modified;
        }
    }

    /**
     * Pure helper: sanitize a snapshot entirely in memory WITHOUT using QueueManager.
     * - Returns a deep-cloned, sanitized JSONObject and whether it changed.
     * - Safe to call from Queue worker repair paths (no enqueue).
     */
    public static SanitizeResult trySanitizeSnapshot(JSONObject snapshot, boolean fixArrays) {
        // Deep clone to avoid mutating the caller’s object
        JSONObject clone = (snapshot == null) ? new JSONObject() : new JSONObject(snapshot.toString());
        boolean changed = sanitizeNode(clone, fixArrays);
        return new SanitizeResult(clone, changed);
    }

    /** Represents the outcome of a JSON parse attempt. */
    private static final class ParseResult {
        final boolean success;
        final String errorMessage;
        final String topType;
        final JSONObject obj;
        final JSONArray arr;
        ParseResult(boolean ok, String msg, String type, JSONObject o, JSONArray a) {
            success = ok; errorMessage = msg; topType = type; obj = o; arr = a;
        }
        JSONObject asObjectOrEmpty() { return (obj != null) ? obj : new JSONObject().put("_root", arr); }
    }

    /**
     * Attempts to parse a JSON string as either an object or array.
     *
     * Automatically enforces top-level object type when required.
     */
    private static ParseResult tryParseJSON(String content, boolean enforceObject) {
        try {
            JSONObject o = new JSONObject(content);
            return new ParseResult(true, null, "object", o, null);
        } catch (Exception e1) {
            try {
                JSONArray a = new JSONArray(content);
                if (enforceObject)
                    return new ParseResult(false, "Top-level is array but object required.", null, null, null);
                return new ParseResult(true, null, "array", null, a);
            } catch (Exception e2) {
                String msg = (e1.getMessage() != null ? e1.getMessage() : "Unknown parse error");
                Logger.log(Logger.TAG.ERROR, "tryParseJSON failed: " + msg);
                return new ParseResult(false, msg, null, null, null);
            }
        }
    }

    /**
     * Clears all elements from a JSONArray in-place.
     *
     * Behavior:
     *   • Removes all array elements without deleting the array object itself.
     *   • Useful for safe reuse of cached array nodes.
     */
    public static void clearJSONArray(JSONArray arr) {
        if (arr == null) return;
        int removed = arr.length();
        while (arr.length() > 0) arr.remove(0);
        Logger.log(Logger.TAG.DEBUG, "Cleared JSONArray (" + removed + " elements removed)");
    }

    /**
     * Clears all keys from a JSONObject in-place.
     *
     * Behavior:
     *   • Removes all keys and values from the given object.
     *   • Operates purely in memory (no disk writes).
     */
    public static void clearJSONObject(JSONObject obj) {
        if (obj == null) return;
        int count = obj.keySet().size();
        for (String key : new ArrayList<>(obj.keySet()))
            obj.remove(key);
        Logger.log(Logger.TAG.DEBUG, "Cleared JSONObject (" + count + " keys removed)");
    }

    /* ============================================================
     * RAW DISK OPERATIONS — Used by QueueManager.RawIO
     * ------------------------------------------------------------
     * These bypass the cache/queue for direct file-level access.
     * ============================================================ */

    /**
     * Directly reads a JSON file from disk (bypasses cache and queue).
     *
     * Returns an empty JSONObject if the file does not exist or is empty.
     * Throws on malformed JSON (corrupt bytes should never pass a read).
     */
    public static JSONObject readJSONRaw(String path) throws Exception {
        long t0 = System.nanoTime();
        Logger.log(Logger.TAG.SYSTEM, "readJSONRaw(begin): path=" + path);

        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "readJSONRaw: path is null or empty.");
            throw new IllegalArgumentException("readJSONRaw: path is null or empty");
        }

        final java.nio.file.Path base    = java.nio.file.Path.of(path).toAbsolutePath().normalize();
        final java.nio.file.Path journal = java.nio.file.Path.of(base.toString() + ".patch");

        final boolean baseExists    = java.nio.file.Files.exists(base);
        final boolean journalExists = java.nio.file.Files.exists(journal);
        final long journalBytes     = (journalExists ? java.nio.file.Files.size(journal) : 0L);

        JSONObject parsed = new JSONObject(); // start empty; may be replaced by base or root patch
        long baseBytes = 0L;

        try {
            // ---------------- Base JSON (if present) ----------------
            if (baseExists) {
                try {
                    baseBytes = java.nio.file.Files.size(base);
                    Logger.log(Logger.TAG.DEBUG, "readJSONRaw: base sizeBytes=" + baseBytes + " path=" + path);
                } catch (Exception sz) {
                    Logger.log(Logger.TAG.DEBUG, "readJSONRaw: could not stat base size (" +
                            sz.getClass().getSimpleName() + "): " + sz.getMessage());
                }

                String content = java.nio.file.Files.readString(base, java.nio.charset.StandardCharsets.UTF_8);
                Logger.log(Logger.TAG.DEBUG, "readJSONRaw: base contentLen=" + (content == null ? -1 : content.length()) +
                        " path=" + path);

                if (content != null && !content.isEmpty()) {
                    parsed = new JSONObject(content); // strict parse
                    Logger.log(Logger.TAG.DEBUG, "readJSONRaw: base parsed length=" + parsed.length() + " path=" + path);

                    validateJsonTree(parsed);
                    Logger.log(Logger.TAG.DEBUG, "readJSONRaw: base validateJsonTree(ok) path=" + path);

                    roundTripCheck(parsed, path + " [read-base]");
                    Logger.log(Logger.TAG.DEBUG, "readJSONRaw: base roundTripCheck(ok) path=" + path);
                } else {
                    // base exists but is empty (e.g., newly created) → start empty and let journal fill it
                    Logger.log(Logger.TAG.INFO, "readJSONRaw: base file empty; will apply journal if present: " + path);
                    parsed = new JSONObject();
                }
            } else {
                // If we have a journal with content, this is normal during first-creation flow—log as INFO, not WARN
                if (journalExists && journalBytes > 0) {
                    Logger.log(Logger.TAG.INFO, "readJSONRaw: base not found; reconstructing from journal: " + path);
                } else {
                    Logger.log(Logger.TAG.WARN, "readJSONRaw: base file not found, starting from {}: " + path);
                }
                parsed = new JSONObject();
            }

            // ---------------- Patch journal replay (if present) ----------------
            if (journalExists && journalBytes > 0) {
                Logger.log(Logger.TAG.DEBUG, "readJSONRaw: journal sizeBytes=" + journalBytes + " path=" + journal);

                try (java.io.BufferedReader jr = java.nio.file.Files.newBufferedReader(
                        journal, java.nio.charset.StandardCharsets.UTF_8)) {

                    String line;
                    long ln = 0;
                    while ((line = jr.readLine()) != null) {
                        ln++;
                        String raw = line.trim();
                        if (raw.isEmpty()) continue;

                        org.json.JSONObject rec;
                        try {
                            rec = new org.json.JSONObject(raw);
                        } catch (org.json.JSONException je) {
                            Logger.log(Logger.TAG.ERROR, "readJSONRaw: bad journal JSON at line " + ln +
                                    " of " + journal + " — " + je.getMessage());

                            // >>> DUMP: malformed journal line (truncate to 512 chars)
                            try {
                                final String shortLine = raw.length() > 512 ? raw.substring(0, 512) : raw;
                                Logger.logDump(
                                        "JOURNAL_BAD_LINE\n"
                                                + "journal=" + journal + "\n"
                                                + "lineNum=" + ln + "\n"
                                                + "err=" + je.getClass().getName() + ": " + (je.getMessage() == null ? "<none>" : je.getMessage()) + "\n"
                                                + "content=" + shortLine
                                );
                            } catch (Throwable ignore) { /* never block */ }

                            throw new Exception("Invalid patch record (line " + ln + ") in " + journal, je);
                        }

                        if (!rec.has("patch")) continue; // ignore unknown records
                        org.json.JSONObject p = rec.getJSONObject("patch");

                        // Root replacement short-circuit
                        if (p.has("root")) {
                            Object rootVal = p.get("root");
                            if (rootVal instanceof org.json.JSONObject ro) {
                                parsed = new org.json.JSONObject(ro.toString());
                            } else if (rootVal instanceof org.json.JSONArray ra) {
                                // keep wrapper consistent with writer/util expectations
                                parsed = new org.json.JSONObject().put("root", ra);
                            } else if (rootVal == org.json.JSONObject.NULL) {
                                parsed = new org.json.JSONObject();
                            } else {
                                parsed = new org.json.JSONObject().put("root", rootVal);
                            }
                            continue;
                        }

                        // Dot-path patches
                        java.util.Iterator<String> it = p.keys();
                        while (it.hasNext()) {
                            String patchPath = it.next();
                            Object val = p.get(patchPath); // may be JSONObject.NULL

                            if (patchPath == null || patchPath.isBlank() || ".".equals(patchPath)) {
                                // Treat empty path as root replacement
                                if (val instanceof org.json.JSONObject ro) {
                                    parsed = new org.json.JSONObject(ro.toString());
                                } else if (val instanceof org.json.JSONArray ra) {
                                    parsed = new org.json.JSONObject().put("root", ra);
                                } else if (val == org.json.JSONObject.NULL) {
                                    parsed = new org.json.JSONObject();
                                } else {
                                    parsed = new org.json.JSONObject().put("root", val);
                                }
                                continue;
                            }

                            String[] tokens = patchPath.split("\\.");
                            org.json.JSONObject target = parsed;
                            for (int i = 0; i < tokens.length - 1; i++) {
                                String t = tokens[i];
                                if (!target.has(t) || !(target.get(t) instanceof org.json.JSONObject)) {
                                    target.put(t, new org.json.JSONObject());
                                }
                                target = target.getJSONObject(t);
                            }
                            String last = tokens[tokens.length - 1];

                            if (val == org.json.JSONObject.NULL) {
                                target.remove(last);
                            } else {
                                target.put(last, val);
                            }
                        }
                    }
                }

                // Validate merged result after replay
                Logger.log(Logger.TAG.DEBUG, "readJSONRaw: validateJsonTree(post-replay/start) path=" + path);
                validateJsonTree(parsed);
                Logger.log(Logger.TAG.DEBUG, "readJSONRaw: validateJsonTree(post-replay/ok) path=" + path);

                roundTripCheck(parsed, path + " [post-replay]");
                Logger.log(Logger.TAG.DEBUG, "readJSONRaw: roundTripCheck(post-replay/ok) path=" + path);
            } else {
                Logger.log(Logger.TAG.DEBUG, "readJSONRaw: no journal to replay for " + path);
            }

            Logger.log(Logger.TAG.INFO, "readJSONRaw: successfully read & validated path=" + path +
                    " jsonSize=" + parsed.length());
            Logger.log(Logger.TAG.SYSTEM, "readJSONRaw(end): path=" + path +
                    " bytes=" + baseBytes + " elapsedMs=" + ((System.nanoTime() - t0) / 1_000_000));
            return parsed;

        } catch (org.json.JSONException je) {
            Logger.log(Logger.TAG.ERROR, "readJSONRaw: invalid JSON in base or journal merge: " + path + " — " + je.getMessage());

            // >>> DUMP: base/merged invalid JSON (include first 512 bytes of file if possible)
            try {
                final int MAX = 512;
                String head = null;
                try {
                    byte[] b = java.nio.file.Files.readAllBytes(base);
                    head = new String(b, 0, Math.min(b.length, MAX), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Throwable ignore) {}
                Logger.logDump(
                        "READ_INVALID_JSON\n"
                                + "path=" + path + "\n"
                                + "err=" + je.getClass().getName() + ": " + (je.getMessage() == null ? "<none>" : je.getMessage()) + "\n"
                                + "head=" + (head == null ? "<unavailable>" : head.replace("\n", "\\n"))
                );
            } catch (Throwable ignore) { /* never block */ }

            throw new Exception("Invalid JSON while reading/merging " + path, je);

        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "readJSONRaw: I/O or replay error for " + path + ": " + e.getMessage());

            // >>> DUMP: overall replay failure
            try {
                Logger.logDump(
                        "JOURNAL_REPLAY_FAILED\n"
                                + "path=" + path + "\n"
                                + "errClass=" + e.getClass().getName() + "\n"
                                + "errMsg=" + (e.getMessage() == null ? "<none>" : e.getMessage())
                );
            } catch (Throwable ignore) { /* never block */ }

            throw new Exception("I/O or replay error while reading " + path, e);
        }
    }

    /**
     * Directly writes a JSONObject to disk (bypasses cache and queue).
     *
     * Overwrites existing content with pretty-printed JSON.
     * Guarantees atomicity (temp file + fsync + atomic move) and verifies
     * the persisted bytes by reading them back and re-validating.
     */
    public static void writeJSONRaw(String path, JSONObject data) throws Exception {
        final long t0 = System.nanoTime();
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "writeJSONRaw: path is null or empty");
            throw new IllegalArgumentException("writeJSONRaw: path is null or empty");
        }
        if (data == null) data = new JSONObject();

        Logger.log(Logger.TAG.SYSTEM, "writeJSONRaw(begin): " + path);

        // ── Phase 0: Validate in memory ─────────────────────────────────────────────
        final long v0 = System.nanoTime();
        validateJsonTree(data);
        roundTripCheck(data, path + " [prewrite]");
        Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: pre-validate ok ("
                + ((System.nanoTime() - v0) / 1_000_000) + " ms)");

        // Paths
        final java.nio.file.Path target = java.nio.file.Path.of(path).toAbsolutePath().normalize();
        final java.nio.file.Path parent = target.getParent();
        if (parent != null && !java.nio.file.Files.exists(parent)) {
            Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: creating parent dirs: " + parent);
            try {
                java.nio.file.Files.createDirectories(parent);
                Logger.log(Logger.TAG.INFO, "writeJSONRaw: parent dirs created: " + parent);
            } catch (Exception mk) {
                Logger.log(Logger.TAG.ERROR, "writeJSONRaw: failed to create parent dir: " + parent + " (" + mk.getClass().getSimpleName() + ")");
                throw new Exception("writeJSONRaw: failed to create parent directories for " + path, mk);
            }
        }

        final String tmpName = target.getFileName().toString() + ".tmp";
        final java.nio.file.Path tmp = target.resolveSibling(tmpName);

        // Prepare bytes once (true byte length, charset-safe)
        final String out = data.toString(4);
        final byte[] outBytes = out.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: payload bytes=" + outBytes.length);

        try {
            // ── Phase 1: Write temp ─────────────────────────────────────────────────
            final long w0 = System.nanoTime();
            java.nio.file.Files.write(tmp, outBytes,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE);
            Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: temp write ok (" + tmp + ", "
                    + ((System.nanoTime() - w0) / 1_000_000) + " ms)");

            // ── Phase 2: fsync temp ────────────────────────────────────────────────
            final long f0 = System.nanoTime();
            try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(
                    tmp, java.nio.file.StandardOpenOption.WRITE)) {
                ch.force(true);
            }
            Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: temp fsync ok ("
                    + ((System.nanoTime() - f0) / 1_000_000) + " ms)");

            // ── Phase 3: Move temp -> target (atomic if possible) ──────────────────
            final long m0 = System.nanoTime();
            try {
                java.nio.file.Files.move(tmp, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: moved with ATOMIC_MOVE ("
                        + ((System.nanoTime() - m0) / 1_000_000) + " ms)");
            } catch (java.nio.file.AtomicMoveNotSupportedException amnse) {
                Logger.log(Logger.TAG.WARN, "writeJSONRaw: ATOMIC_MOVE not supported; using REPLACE_EXISTING (" + amnse.getMessage() + ")");
                java.nio.file.Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: moved with REPLACE_EXISTING ("
                        + ((System.nanoTime() - m0) / 1_000_000) + " ms)");
            }

            // ── Phase 4: fsync parent dir (best-effort) ────────────────────────────
            if (parent != null) {
                final long d0 = System.nanoTime();
                try (java.nio.channels.FileChannel dirCh = java.nio.channels.FileChannel.open(
                        parent, java.nio.file.StandardOpenOption.READ)) {
                    dirCh.force(true);
                } catch (Exception dirSyncFail) {
                    // Non-fatal on Windows; directory handles can be weird.
                    Logger.log(Logger.TAG.WARN, "writeJSONRaw: dir fsync skipped (" + dirSyncFail.getClass().getSimpleName() + ")");
                }
                Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: dir fsync done ("
                        + ((System.nanoTime() - d0) / 1_000_000) + " ms)");
            }

            // ── Phase 5: Read-back verify ───────────────────────────────────────────
            final long r0 = System.nanoTime();
            Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: verify begin → " + path);
            JSONObject verify = readJSONRaw(path); // strict read + validate + round-trip
            validateJsonTree(verify);
            roundTripCheck(verify, path + " [postwrite]");
            Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: verify ok (jsonSize=" + verify.length()
                    + ", " + ((System.nanoTime() - r0) / 1_000_000) + " ms)");

            Logger.log(Logger.TAG.INFO, "writeJSONRaw: wrote & verified: " + path
                    + " (elapsed=" + ((System.nanoTime() - t0) / 1_000_000) + " ms)");
            Logger.log(Logger.TAG.SYSTEM, "writeJSONRaw(end): " + path);

        } catch (Exception e) {
            // Best-effort cleanup of leftover temp
            try {
                boolean del = java.nio.file.Files.deleteIfExists(tmp);
                Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: temp cleanup delete=" + del + " (" + tmp + ")");
            } catch (Exception ignore) {
                Logger.log(Logger.TAG.DEBUG, "writeJSONRaw: temp cleanup failed (" + ignore.getClass().getSimpleName() + "): " + ignore.getMessage());
            }
            Logger.log(Logger.TAG.ERROR, "writeJSONRaw: failure for " + path + " — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new Exception("I/O or validation error while writing JSON to " + path, e);
        }
    }

    /**
     * Moves a corrupted JSON file to the "database/corrupt" directory.
     *
     * Attempts to rename with a .corrupt timestamp suffix if move fails.
     */
    public static void moveToCorrupt(String path) throws Exception {
        long t0 = System.nanoTime();
        Logger.log(Logger.TAG.SYSTEM, "moveToCorrupt(begin): path=" + path);
        Logger.logDump("CORRUPT_QUARANTINE_BEGIN\npath=" + path);

        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "moveToCorrupt: path is null or empty.");
            Logger.logDump("CORRUPT_QUARANTINE_ABORT\npath=" + path + "\nreason=null_or_blank");
            throw new IllegalArgumentException("moveToCorrupt: path is null or empty");
        }

        java.nio.file.Path src = java.nio.file.Path.of(path).toAbsolutePath().normalize();
        java.io.File srcFile = src.toFile();

        if (!srcFile.exists()) {
            Logger.log(Logger.TAG.WARN, "moveToCorrupt: attempted to move nonexistent file: " + src);
            Logger.logDump("CORRUPT_QUARANTINE_SKIP_NONEXISTENT\npath=" + src);
            Logger.log(Logger.TAG.SYSTEM, "moveToCorrupt(end): path=" + path + " elapsedMs=" + ((System.nanoTime()-t0)/1_000_000));
            return;
        }

        // Build destination path preserving relative structure under CORRUPTPATH
        java.nio.file.Path corruptRoot = java.nio.file.Path.of(BotConfig.CORRUPTPATH).toAbsolutePath().normalize();
        java.nio.file.Path rel = src.getParent() != null
                ? src.getParent().relativize(src)
                : src.getFileName();

        java.nio.file.Path destDir = corruptRoot; // or corruptRoot.resolve(dbRelativeParent);
        java.nio.file.Files.createDirectories(destDir);
        Logger.log(Logger.TAG.DEBUG, "moveToCorrupt: destDir=" + destDir + " rel=" + rel);

        String baseName = src.getFileName().toString();
        String stem = baseName;
        String ext = "";
        int dot = baseName.lastIndexOf('.');
        if (dot > 0 && dot < baseName.length() - 1) {
            stem = baseName.substring(0, dot);
            ext  = baseName.substring(dot); // includes '.'
        }

        long ts = System.currentTimeMillis();
        java.nio.file.Path dest = destDir.resolve(stem + ext); // first try plain name

        // Ensure uniqueness if a prior corrupt of same name exists
        int seq = 0;
        while (java.nio.file.Files.exists(dest)) {
            seq++;
            dest = destDir.resolve(stem + "." + ts + "." + seq + ext);
        }
        Logger.log(Logger.TAG.DEBUG, "moveToCorrupt: resolved dest=" + dest + " seq=" + seq);

        Logger.log(Logger.TAG.SYSTEM, "moveToCorrupt: quarantining corrupt file");
        Logger.log(Logger.TAG.INFO, "moveToCorrupt src=" + src + " dest=" + dest);

        Exception lastEx = null;
        long srcBytes = 0L;
        try { srcBytes = srcFile.length(); } catch (Throwable ignore) {}

        // Try atomic move first (same-volume + supported FS)
        try {
            java.nio.file.Files.move(src, dest,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Logger.log(Logger.TAG.WARN, "Corrupt file moved (atomic): " + src.getFileName() + " → " + dest);

            // DUMP: success via atomic
            Logger.logDump(
                    "CORRUPT_QUARANTINE_MOVED\n"
                            + "src=" + src + "\n"
                            + "dst=" + dest + "\n"
                            + "bytes=" + srcBytes + "\n"
                            + "mode=ATOMIC_MOVE"
            );

        } catch (Exception atomicFail) {
            lastEx = atomicFail;
            Logger.log(Logger.TAG.DEBUG, "moveToCorrupt: ATOMIC_MOVE failed (" + atomicFail.getClass().getSimpleName()
                    + "): " + atomicFail.getMessage() + " — attempting copy+delete");

            // Fallback: copy → fsync → delete
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(src);
                 java.io.OutputStream out = java.nio.file.Files.newOutputStream(
                         dest,
                         java.nio.file.StandardOpenOption.CREATE,
                         java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                         java.nio.file.StandardOpenOption.WRITE)) {

                long copied = in.transferTo(out);
                Logger.log(Logger.TAG.DEBUG, "moveToCorrupt: copied bytes=" + copied);

                out.flush();

                // fsync dest to be safe
                try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(dest, java.nio.file.StandardOpenOption.WRITE)) {
                    ch.force(true);
                }
                Logger.log(Logger.TAG.DEBUG, "moveToCorrupt: dest fsync ok");

                // Try to delete source (may fail on Windows if locked)
                try {
                    java.nio.file.Files.delete(src);
                    Logger.log(Logger.TAG.WARN, "Corrupt file copied & deleted: " + src.getFileName() + " → " + dest);

                    // DUMP: success via copy+delete
                    Logger.logDump(
                            "CORRUPT_QUARANTINE_MOVED\n"
                                    + "src=" + src + "\n"
                                    + "dst=" + dest + "\n"
                                    + "bytes=" + srcBytes + "\n"
                                    + "mode=COPY_DELETE"
                    );

                } catch (Exception delFail) {
                    Logger.log(Logger.TAG.ERROR, "moveToCorrupt: source delete failed after copy (likely locked). "
                            + "Left original in place. " + delFail.getClass().getSimpleName() + ": " + delFail.getMessage());

                    // DUMP: copy succeeded but delete failed
                    Logger.logDump(
                            "CORRUPT_QUARANTINE_COPY_ONLY\n"
                                    + "src=" + src + "\n"
                                    + "dst=" + dest + "\n"
                                    + "bytes=" + srcBytes + "\n"
                                    + "mode=COPY_ONLY\n"
                                    + "deleteErr=" + delFail.getClass().getName() + ": " + (delFail.getMessage() == null ? "<none>" : delFail.getMessage())
                    );
                }

            } catch (Exception copyFail) {
                Logger.log(Logger.TAG.ERROR, "moveToCorrupt: copy+delete fallback failed: "
                        + copyFail.getClass().getSimpleName() + ": " + copyFail.getMessage());

                // DUMP: fallback failed
                Logger.logDump(
                        "CORRUPT_QUARANTINE_FAILED\n"
                                + "src=" + src + "\n"
                                + "dst=" + dest + "\n"
                                + "bytes=" + srcBytes + "\n"
                                + "mode=COPY_DELETE\n"
                                + "err=" + copyFail.getClass().getName() + ": " + (copyFail.getMessage() == null ? "<none>" : copyFail.getMessage())
                );

                throw new Exception("moveToCorrupt: unable to quarantine corrupt file (copy fallback failed)", copyFail);
            }
        }

        // Write a tiny .meta.json for forensics (best effort)
        try {
            java.nio.file.Path meta = dest.resolveSibling(dest.getFileName().toString() + ".meta.json");
            String metaJson = new org.json.JSONObject()
                    .put("source", src.toString())
                    .put("quarantined_at", ts)
                    .put("size_bytes", srcBytes)
                    .put("note", "File quarantined by moveToCorrupt; prior error: " + (lastEx != null ? lastEx.getClass().getSimpleName() : "none"))
                    .toString(2);
            java.nio.file.Files.writeString(meta, metaJson,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE);
            Logger.log(Logger.TAG.DEBUG, "moveToCorrupt: wrote metadata " + meta.getFileName());
        } catch (Exception ignoreMeta) {
            Logger.log(Logger.TAG.DEBUG, "moveToCorrupt: metadata write skipped: " + ignoreMeta.getMessage());
            // DUMP (non-fatal)
            Logger.logDump(
                    "CORRUPT_QUARANTINE_META_SKIPPED\n"
                            + "path=" + dest + "\n"
                            + "err=" + ignoreMeta.getClass().getName() + ": " + (ignoreMeta.getMessage() == null ? "<none>" : ignoreMeta.getMessage())
            );
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        Logger.logDump(
                "CORRUPT_QUARANTINE_END\n"
                        + "path=" + path + "\n"
                        + "dest=" + dest + "\n"
                        + "elapsedMs=" + elapsedMs
        );
        Logger.log(Logger.TAG.SYSTEM, "moveToCorrupt(end): path=" + path + " elapsedMs=" + elapsedMs);
    }

    public static void appendJSONPatch(String path, Map<String,Object> diff) throws Exception {
        if (path == null || path.isBlank())
            throw new IllegalArgumentException("appendJSONPatch: path is null/blank");
        if (diff == null || diff.isEmpty()) return;

        final java.nio.file.Path base    = java.nio.file.Path.of(path).toAbsolutePath().normalize();
        final java.nio.file.Path parent  = base.getParent();
        if (parent != null && !java.nio.file.Files.exists(parent)) {
            java.nio.file.Files.createDirectories(parent);
        }
        final java.nio.file.Path journal = java.nio.file.Path.of(base.toString() + ".patch");

        // --- Journal auto-rotation guard (size and/or record count) ---
        try {
            boolean rotate = false;

            if (QueueManager.Config.JOURNAL_MAX_BYTES > 0 && java.nio.file.Files.exists(journal)) {
                long size = java.nio.file.Files.size(journal);
                if (size >= QueueManager.Config.JOURNAL_MAX_BYTES) {
                    Logger.log(Logger.TAG.WARN, "appendJSONPatch: journal size " + size +
                            " ≥ cap " + QueueManager.Config.JOURNAL_MAX_BYTES + " → rotating: " + journal);
                    rotate = true;
                }
            }

            if (!rotate && QueueManager.Config.JOURNAL_MAX_RECORDS > 0 && java.nio.file.Files.exists(journal)) {
                long lines = 0L;
                try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(
                        journal, java.nio.charset.StandardCharsets.UTF_8)) {
                    while (br.readLine() != null) lines++;
                }
                if (lines >= QueueManager.Config.JOURNAL_MAX_RECORDS) {
                    Logger.log(Logger.TAG.WARN, "appendJSONPatch: journal lines " + lines +
                            " ≥ cap " + QueueManager.Config.JOURNAL_MAX_RECORDS + " → rotating: " + journal);
                    rotate = true;
                }
            }

            if (rotate) {
                // Collapse: read merged snapshot (base + journal), write to base, truncate journal.
                try {
                    Logger.log(Logger.TAG.DEBUG, "appendJSONPatch: rotating by materializing snapshot → " + base);
                    org.json.JSONObject snap = readJSONRaw(base.toString()); // replays journal
                    writeJSONRaw(base.toString(), snap);                     // durable base
                    // Truncate the journal in a crash-safe way
                    try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(
                            journal,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.WRITE,
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                        ch.force(true);
                    }
                    Logger.log(Logger.TAG.INFO, "appendJSONPatch: rotation complete (journal truncated): " + journal);
                } catch (Exception rotEx) {
                    Logger.log(Logger.TAG.ERROR, "appendJSONPatch: rotation failed for " + journal +
                            " — " + rotEx.getMessage() + " (continuing without rotation)");
                }
            }
        } catch (Exception rotGateEx) {
            // Rotation gate failure must not block forward progress
            Logger.log(Logger.TAG.ERROR, "appendJSONPatch: rotation gate check failed — " + rotGateEx.getMessage());
        }

        // --- Build one NDJSON record: {"ts":..., "patch":{ "a.b": <val or null>, ... }} ---
        org.json.JSONObject record = new org.json.JSONObject().put("ts", System.currentTimeMillis());
        org.json.JSONObject patchObj = new org.json.JSONObject();
        for (Map.Entry<String,Object> e : diff.entrySet()) {
            Object v = (e.getValue() == null) ? org.json.JSONObject.NULL : e.getValue();
            patchObj.put(e.getKey(), v);
        }
        record.put("patch", patchObj);
        String line = record.toString() + System.lineSeparator();

        // --- Append + fsync ---
        try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(
                journal,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.APPEND)) {

            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(
                    line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
    }

    /**
     * Replaces the journal with a single root-patch entry that captures
     * the current merged state. Does NOT rewrite the base JSON file — stays
     * compliant with "delta-only on flush".
     */
    private static void rotateJournalToSingleRoot(String path,
                                                  java.nio.file.Path base,
                                                  java.nio.file.Path journal) throws Exception {
        // 1) Obtain live merged state (reads base + replays current journal)
        JSONObject live = readJSONRaw(path); // already validates/round-trips

        // 2) Build a single NDJSON root record
        org.json.JSONObject rec = new org.json.JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("patch", new org.json.JSONObject().put("root", live));
        String line = rec.toString() + System.lineSeparator();

        // 3) Write to temp, fsync, then atomically replace journal
        java.nio.file.Path tmp = java.nio.file.Path.of(journal.toString() + ".tmp");
        try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(
                tmp,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE)) {

            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(
                    line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }

        try {
            java.nio.file.Files.move(tmp, journal,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            Logger.log(Logger.TAG.INFO, "rotateJournalToSingleRoot: journal rotated (root snapshot) → " + journal);
        } catch (Exception atomicFail) {
            Logger.log(Logger.TAG.DEBUG, "rotateJournalToSingleRoot: ATOMIC_MOVE not available (" +
                    atomicFail.getClass().getSimpleName() + "): " + atomicFail.getMessage() +
                    " — falling back to non-atomic REPLACE");
            java.nio.file.Files.move(tmp, journal, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Logger.log(Logger.TAG.INFO, "rotateJournalToSingleRoot: journal rotated (replace) → " + journal);
        }
    }

    /** Rejects NaN/Infinity and non-JSON-native types anywhere in the tree. */
    private static void validateJsonTree(Object node) throws Exception {
        if (node == null || node == JSONObject.NULL) return;

        if (node instanceof JSONObject obj) {
            for (String k : obj.keySet()) validateJsonTree(obj.get(k));
            return;
        }
        if (node instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) validateJsonTree(arr.get(i));
            return;
        }
        if (node instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new Exception("Invalid numeric value in JSON: " + n);
            }
            return;
        }
        if (node instanceof String || node instanceof Boolean) return;

        // Anything else is not JSON-native (e.g., Date, Map, custom objects)
        throw new Exception("Unsupported JSON type: " + node.getClass().getName());
    }

    /** Serialize → parse again to catch subtle serializer/encoding issues. */
    private static void roundTripCheck(JSONObject obj, String ctx) throws Exception {
        try {
            String s = obj.toString();
            JSONObject re = new JSONObject(s);
            // Coarse structural probe to catch truncation; deep compare not required here.
            if (obj.length() != re.length()) {
                throw new Exception("Round-trip size mismatch: " + ctx + " (" + obj.length() + " vs " + re.length() + ")");
            }
        } catch (Exception t) {
            throw new Exception("Round-trip parse failed: " + ctx + " — " + t.getMessage(), t);
        }
    }
}
