package sora.simulation.editor;

import org.json.JSONObject;
import sora.database.DatabaseManager;
import sora.exceptions.DatabaseException;
import sora.util.Logger;

public final class ArtifactStore {
    private static final String ARTIFACT_DIR = "database/artifacts";
    private static final String ARTIFACT_FILE = ARTIFACT_DIR + "/artifacts.json";

    private ArtifactStore() {}

    public static String getArtifactFilePath() {
        return ARTIFACT_FILE;
    }

    public static void save(ArtifactTemplateDraft draft, int canvasWidth, int canvasHeight)
            throws DatabaseException {
        if (draft == null) {
            Logger.log(Logger.TAG.ERROR, "[A0011] ArtifactStore: cannot save null draft.");
            throw new DatabaseException("ArtifactStore: draft cannot be null.");
        }

        ensureStore();
        removeStaleNameEntry(draft);
        JSONObject artifact = draft.toArtifactJson(canvasWidth, canvasHeight);
        DatabaseManager.writeJSONPath(
                ARTIFACT_FILE,
                "artifacts." + draft.getId(),
                artifact,
                true
        );
        DatabaseManager.writeJSONPath(
                ARTIFACT_FILE,
                "artifact_names." + sanitizeNameKey(draft.getName()),
                draft.getId(),
                true
        );
        Logger.log(Logger.TAG.INFO, "ArtifactStore: saved artifact id=" + draft.getId()
                + " name=" + draft.getName());
    }

    public static ArtifactTemplateDraft load(String idOrName, int fallbackCanvasWidth, int fallbackCanvasHeight)
            throws DatabaseException {
        if (idOrName == null || idOrName.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "[A0022] ArtifactStore: load id/name is blank.");
            throw new DatabaseException("ArtifactStore: artifact id or name cannot be blank.");
        }

        ensureStore();
        String artifactId = resolveArtifactId(idOrName.trim());
        Object raw = DatabaseManager.readJSONPath(ARTIFACT_FILE, "artifacts." + artifactId);
        if (!(raw instanceof JSONObject artifact)) {
            Logger.log(Logger.TAG.ERROR, "[A0023] ArtifactStore: loaded artifact is not a JSON object: " + artifactId);
            throw new DatabaseException("ArtifactStore: artifact is not a JSON object: " + artifactId);
        }

        Logger.log(Logger.TAG.INFO, "ArtifactStore: loaded artifact id=" + artifactId);
        return ArtifactTemplateDraft.fromArtifactJson(artifact, fallbackCanvasWidth, fallbackCanvasHeight);
    }

    public static String findArtifactIdByName(String name) throws DatabaseException {
        if (name == null || name.isBlank()) {
            return null;
        }
        ensureStore();
        String namePath = "artifact_names." + sanitizeNameKey(name);
        if (!DatabaseManager.containsJSONPath(ARTIFACT_FILE, namePath)) {
            return null;
        }
        Object id = DatabaseManager.readJSONPath(ARTIFACT_FILE, namePath);
        if (id instanceof String foundId && !foundId.isBlank()) {
            return foundId;
        }
        return null;
    }

    public static void delete(ArtifactTemplateDraft draft) throws DatabaseException {
        if (draft == null || draft.getId() == null || draft.getId().isBlank()) {
            Logger.log(Logger.TAG.ERROR, "[A0028] ArtifactStore: cannot delete null draft/id.");
            throw new DatabaseException("ArtifactStore: cannot delete artifact without an id.");
        }

        deleteById(draft.getId(), draft.getName());
    }

    public static void deleteById(String artifactId, String fallbackName) throws DatabaseException {
        if (artifactId == null || artifactId.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "[A0028] ArtifactStore: cannot delete blank artifact id.");
            throw new DatabaseException("ArtifactStore: cannot delete artifact without an id.");
        }

        ensureStore();
        String artifactPath = "artifacts." + artifactId;
        if (DatabaseManager.containsJSONPath(ARTIFACT_FILE, artifactPath)) {
            Object raw = DatabaseManager.readJSONPath(ARTIFACT_FILE, artifactPath);
            if (raw instanceof JSONObject artifact) {
                removeNameEntryIfMatches(artifact.optString("name", null), artifactId);
            }
            DatabaseManager.removeJSONPath(ARTIFACT_FILE, artifactPath);
        }
        removeNameEntryIfMatches(fallbackName, artifactId);
        Logger.log(Logger.TAG.INFO, "ArtifactStore: deleted artifact id=" + artifactId);
    }

    private static void ensureStore() throws DatabaseException {
        if (!DatabaseManager.folderExists(ARTIFACT_DIR)) {
            DatabaseManager.createFolder(ARTIFACT_DIR);
        }
        if (!DatabaseManager.fileExists(ARTIFACT_FILE)) {
            DatabaseManager.createJSON(
                    ARTIFACT_FILE,
                    new JSONObject()
                            .put("format", "sora-artifacts-v1")
                            .put("artifacts", new JSONObject())
                            .put("artifact_names", new JSONObject())
            );
        }
    }

    private static String resolveArtifactId(String idOrName) throws DatabaseException {
        String directPath = "artifacts." + idOrName;
        if (DatabaseManager.containsJSONPath(ARTIFACT_FILE, directPath)) {
            return idOrName;
        }

        String namePath = "artifact_names." + sanitizeNameKey(idOrName);
        if (DatabaseManager.containsJSONPath(ARTIFACT_FILE, namePath)) {
            Object id = DatabaseManager.readJSONPath(ARTIFACT_FILE, namePath);
            if (id instanceof String foundId && !foundId.isBlank()) {
                return foundId;
            }
        }

        Logger.log(Logger.TAG.ERROR, "[A0024] ArtifactStore: artifact not found: " + idOrName);
        throw new DatabaseException("ArtifactStore: artifact not found: " + idOrName);
    }

    private static void removeStaleNameEntry(ArtifactTemplateDraft draft) throws DatabaseException {
        String artifactPath = "artifacts." + draft.getId();
        if (!DatabaseManager.containsJSONPath(ARTIFACT_FILE, artifactPath)) {
            return;
        }

        Object raw = DatabaseManager.readJSONPath(ARTIFACT_FILE, artifactPath);
        if (!(raw instanceof JSONObject previous)) {
            return;
        }

        String oldName = previous.optString("name", null);
        if (oldName == null || oldName.isBlank()) {
            return;
        }
        if (!sanitizeNameKey(oldName).equals(sanitizeNameKey(draft.getName()))) {
            removeNameEntryIfMatches(oldName, draft.getId());
        }
    }

    private static void removeNameEntryIfMatches(String name, String artifactId) throws DatabaseException {
        if (name == null || name.isBlank()) {
            return;
        }
        String namePath = "artifact_names." + sanitizeNameKey(name);
        if (!DatabaseManager.containsJSONPath(ARTIFACT_FILE, namePath)) {
            return;
        }
        Object mappedId = DatabaseManager.readJSONPath(ARTIFACT_FILE, namePath);
        if (mappedId instanceof String id && id.equals(artifactId)) {
            DatabaseManager.removeJSONPath(ARTIFACT_FILE, namePath);
        }
    }

    private static String sanitizeNameKey(String name) {
        String source = (name == null || name.isBlank()) ? "untitled" : name.trim().toLowerCase();
        String key = source.replaceAll("[^a-z0-9_-]+", "_");
        while (key.contains("__")) {
            key = key.replace("__", "_");
        }
        if (key.startsWith("_")) key = key.substring(1);
        if (key.endsWith("_")) key = key.substring(0, key.length() - 1);
        return key.isBlank() ? "untitled" : key;
    }
}
