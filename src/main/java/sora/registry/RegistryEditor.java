package sora.registry;

import org.json.JSONArray;
import org.json.JSONObject;
import sora.database.DatabaseManager;
import sora.exceptions.DatabaseException;
import sora.config.BootstrapConfig;

import java.nio.file.Path;
import java.util.Locale;

public final class RegistryEditor {

    private static final String USER_DIR = BootstrapConfig.USERPATH;
    private static final String SERVER_DIR = BootstrapConfig.SERVERPATH;
    private static final String GLOBAL_DIR = BootstrapConfig.GLOBALPATH;
    private static final String REGISTRY_DIR = BootstrapConfig.REGISTRYPATH;

    private RegistryEditor() {}

    public static Object readUserData(String fileName, String jsonPath) throws DatabaseException {
        return readValue(USER_DIR, fileName, jsonPath);
    }

    public static boolean writeUserData(String fileName, String jsonPath, Object value) throws DatabaseException {
        return writeValue(USER_DIR, fileName, jsonPath, value);
    }

    public static Object readServerData(String fileName, String jsonPath) throws DatabaseException {
        return readValue(SERVER_DIR, fileName, jsonPath);
    }

    public static boolean writeServerData(String fileName, String jsonPath, Object value) throws DatabaseException {
        return writeValue(SERVER_DIR, fileName, jsonPath, value);
    }

    public static Object readGlobalData(String fileName, String jsonPath) throws DatabaseException {
        return readValue(GLOBAL_DIR, fileName, jsonPath);
    }

    public static boolean writeGlobalData(String fileName, String jsonPath, Object value) throws DatabaseException {
        return writeValue(GLOBAL_DIR, fileName, jsonPath, value);
    }

    public static Object readRegistryData(String fileName, String jsonPath) throws DatabaseException {
        return readValue(REGISTRY_DIR, fileName, jsonPath);
    }

    public static boolean writeRegistryData(String fileName, String jsonPath, Object value) throws DatabaseException {
        return writeValue(REGISTRY_DIR, fileName, jsonPath, value);
    }

    public static boolean deleteUserFile(String fileName) throws DatabaseException {
        return deleteFile(USER_DIR, fileName);
    }

    public static boolean deleteUserPath(String fileName, String jsonPath) throws DatabaseException {
        return deletePath(USER_DIR, fileName, jsonPath);
    }

    public static boolean deleteServerFile(String fileName) throws DatabaseException {
        return deleteFile(SERVER_DIR, fileName);
    }

    public static boolean deleteServerPath(String fileName, String jsonPath) throws DatabaseException {
        return deletePath(SERVER_DIR, fileName, jsonPath);
    }

    public static boolean deleteGlobalFile(String fileName) throws DatabaseException {
        return deleteFile(GLOBAL_DIR, fileName);
    }

    public static boolean deleteGlobalPath(String fileName, String jsonPath) throws DatabaseException {
        return deletePath(GLOBAL_DIR, fileName, jsonPath);
    }

    public static boolean deleteRegistryFile(String fileName) throws DatabaseException {
        return deleteFile(REGISTRY_DIR, fileName);
    }

    public static boolean deleteRegistryPath(String fileName, String jsonPath) throws DatabaseException {
        return deletePath(REGISTRY_DIR, fileName, jsonPath);
    }

    private static Object readValue(String sectionDir, String fileName, String jsonPath) throws DatabaseException {
        String filePath = resolvePath(sectionDir, fileName);
        ensureFileExists(filePath);

        if (!DatabaseManager.containsJSONPath(filePath, jsonPath)) {
            DatabaseManager.writeJSONPath(filePath, jsonPath, JSONObject.NULL, true);
            return null;
        }

        Object value = DatabaseManager.readJSONPath(filePath, jsonPath);
        return normalizeReturn(value);
    }

    private static boolean writeValue(String sectionDir, String fileName, String jsonPath, Object value)
            throws DatabaseException {
        String filePath = resolvePath(sectionDir, fileName);
        ensureFileExists(filePath);
        return DatabaseManager.writeJSONPath(filePath, jsonPath, value, true);
    }

    private static boolean deleteFile(String sectionDir, String fileName) throws DatabaseException {
        String filePath = resolvePath(sectionDir, fileName);
        if (!DatabaseManager.fileExists(filePath)) {
            return false;
        }
        return DatabaseManager.deleteFile(filePath);
    }

    private static boolean deletePath(String sectionDir, String fileName, String jsonPath) throws DatabaseException {
        String filePath = resolvePath(sectionDir, fileName);
        ensureFileExists(filePath);
        if (!DatabaseManager.containsJSONPath(filePath, jsonPath)) {
            return false;
        }
        return DatabaseManager.removeJSONPath(filePath, jsonPath);
    }

    private static String resolvePath(String sectionDir, String fileName) throws DatabaseException {
        String normalizedName = normalizeFileName(fileName);
        Path path = Path.of(sectionDir, normalizedName);
        return path.toString();
    }

    private static void ensureFileExists(String filePath) throws DatabaseException {
        if (!DatabaseManager.fileExists(filePath)) {
            DatabaseManager.createJSON(filePath);
        }
    }

    private static String normalizeFileName(String fileName) throws DatabaseException {
        if (fileName == null || fileName.isBlank()) {
            throw new DatabaseException("RegistryEditor: fileName is null or empty.");
        }
        String trimmed = fileName.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw new DatabaseException("RegistryEditor: fileName must be a plain file name.");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".json")) {
            return trimmed + ".json";
        }
        return trimmed;
    }

    private static Object normalizeReturn(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        return value;
    }
}
