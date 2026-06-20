package sora.config;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import sora.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central runtime configuration loader and in-memory config state holder.
 *
 * Purpose:
 * - Load runtime config exactly once from `database/utility/config.json`.
 * - Preserve a fully in-memory runtime config snapshot after startup load.
 * - Provide defensive read access to config values and metadata flags.
 * - Fall back to coded defaults when the config file is missing, malformed, or
 *   contains invalid entries.
 *
 * Startup behavior:
 * - This class is intended to run before logger initialization.
 * - For that reason, all diagnostics here are written directly to console.
 * - Logger must not be used from this class during pre-logger bootstrap flow.
 *
 * File format expectations (v1):
 * {
 *   "version": 1,
 *   "values": {
 *     "module_name": {
 *       "constant_name": {
 *         "value": <json value>,
 *         "flags": 0
 *       }
 *     }
 *   }
 * }
 *
 * Flags bitmap (v1):
 * - 0 = no flags
 * - 1 = value does not update live runtime
 * - 2 = value requires explicit manual apply
 * - values above 2 are invalid in v1
 *
 * Safety rules:
 * - The buffered JSON reader is always closed immediately after use.
 * - Reader shutdown occurs even if file read, parse, or validation fails.
 * - Returned JSON objects/arrays are always defensive copies.
 *
 * Version 1 scope:
 * - Read-only runtime config loading and in-memory storage.
 * - No file writes.
 * - No editor logic yet.
 */
public final class ConfigManager {

    /** Version number expected for the initial runtime config schema. */
    public static final int VERSION_1 = 1;

    /** Root key holding grouped runtime config values. */
    public static final String VALUES_KEY = "values";

    /** Leaf key storing the actual config value payload. */
    public static final String VALUE_KEY = "value";

    /** Leaf key storing the bitmap flags for a config value. */
    public static final String FLAGS_KEY = "flags";

    /** Root key storing runtime config schema version. */
    public static final String VERSION_KEY = "version";

    /** Flag: value may be stored in memory, but changing it does not update runtime. */
    public static final int FLAG_NO_RUNTIME_UPDATE = 1;

    /** Flag: value change requires explicit manual apply rather than automatic effect. */
    public static final int FLAG_REQUIRES_EXPLICIT_APPLY = 2;

    /** Canonical runtime config file path. */
    private static final Path CONFIG_PATH = Path.of(BootstrapConfig.CONFIGPATH).resolve("config.json");

    /** Whether runtime config has been initialized at least once for this process. */
    private static volatile boolean initialized = false;

    /** Whether the last load used a real config file instead of pure coded defaults. */
    private static volatile boolean loadedFromFile = false;

    /** Current in-memory runtime config root. Always object-root. */
    private static volatile JSONObject stateRoot = buildDefaultRoot();

    private ConfigManager() {}

    /**
     * Initializes the runtime config state for this process.
     *
     * Behavior:
     * - Builds coded defaults first.
     * - Attempts to read and validate the runtime config file.
     * - Falls back to defaults for any invalid or missing content.
     * - Stores the final merged object in memory.
     *
     * Return:
     * - true if the runtime config file was successfully loaded and used.
     * - false if defaults were used fully or partially due to missing/invalid file content.
     *
     * Diagnostics:
     * - Console-only output, because logger may not exist yet.
     *
     * @return true if a config file load succeeded, false if defaults were used/fallback occurred
     */
    public static synchronized boolean initialize() {
        LoadResult result = loadFromDiskWithFallback();
        stateRoot = result.root;
        loadedFromFile = result.loadedFromFile;
        initialized = true;
        consoleInfo("ConfigManager: initialization complete. source=" +
                (loadedFromFile ? "file" : "coded-defaults") +
                " path=" + CONFIG_PATH);
        return loadedFromFile;
    }

    /**
     * Reloads runtime config from disk using the same rules as initialize().
     *
     * Notes:
     * - This only refreshes ConfigManager's in-memory state.
     * - It does not automatically apply manual-update module changes.
     * - Later editor/apply flows may call this as part of a broader config refresh path.
     *
     * @return true if the config file was successfully loaded, false if defaults/fallback were used
     */
    public static synchronized boolean reload() {
        return initialize();
    }

    /**
     * Returns whether runtime config has been initialized in this process.
     *
     * @return true if initialize() or reload() has completed
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns whether the most recent config load succeeded from the on-disk file.
     *
     * @return true if the last load used the config file, false if defaults were used/fallback occurred
     */
    public static boolean wasLoadedFromFile() {
        return loadedFromFile;
    }

    /**
     * Returns the canonical runtime config file path.
     *
     * @return config.json path under the bootstrap database root
     */
    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    /**
     * Returns a defensive copy of the full in-memory runtime config root.
     *
     * @return full runtime config root copy
     */
    public static JSONObject snapshot() {
        return new JSONObject(stateRoot.toString());
    }

    /**
     * Returns the runtime config schema version currently stored in memory.
     *
     * @return version number, or VERSION_1 fallback if absent/invalid
     */
    public static int getVersion() {
        Object raw = stateRoot.opt(VERSION_KEY);
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return VERSION_1;
    }

    /**
     * Returns a defensive copy of the entire grouped values object.
     *
     * @return copy of the root `values` object
     */
    public static JSONObject snapshotValues() {
        JSONObject values = stateRoot.optJSONObject(VALUES_KEY);
        if (values == null) {
            return new JSONObject();
        }
        return new JSONObject(values.toString());
    }

    /**
     * Reads a runtime config value by dot path under the root `values` object.
     *
     * Example:
     * - `logger.log_max_lines`
     * - `network.max_queue_size`
     *
     * The path resolves to a leaf object with:
     * - `value`
     * - `flags`
     *
     * Return:
     * - defensive copy for JSONObject / JSONArray values
     * - primitive wrapper / String / Boolean / Number as-is
     * - null if the path is missing or invalid
     *
     * @param valuePath dot path under `values`
     * @return config value copy or null
     */
    public static Object getValue(String valuePath) {
        JSONObject entry = getEntry(valuePath);
        if (entry == null || !entry.has(VALUE_KEY)) {
            return null;
        }
        return defensiveCopyJsonValue(entry.opt(VALUE_KEY));
    }

    /**
     * Reads a runtime config string value with coded-default fallback.
     *
     * @param valuePath dot path under `values`
     * @param fallback fallback value when missing or invalid
     * @return configured string or fallback
     */
    public static String getString(String valuePath, String fallback) {
        Object value = getValue(valuePath);
        return (value instanceof String s) ? s : fallback;
    }

    /**
     * Reads a runtime config boolean value with coded-default fallback.
     *
     * @param valuePath dot path under `values`
     * @param fallback fallback value when missing or invalid
     * @return configured boolean or fallback
     */
    public static boolean getBoolean(String valuePath, boolean fallback) {
        Object value = getValue(valuePath);
        return (value instanceof Boolean b) ? b : fallback;
    }

    /**
     * Reads a runtime config int value with coded-default fallback.
     *
     * @param valuePath dot path under `values`
     * @param fallback fallback value when missing or invalid
     * @return configured int or fallback
     */
    public static int getInt(String valuePath, int fallback) {
        Object value = getValue(valuePath);
        return (value instanceof Number n) ? n.intValue() : fallback;
    }

    /**
     * Reads a runtime config long value with coded-default fallback.
     *
     * @param valuePath dot path under `values`
     * @param fallback fallback value when missing or invalid
     * @return configured long or fallback
     */
    public static long getLong(String valuePath, long fallback) {
        Object value = getValue(valuePath);
        return (value instanceof Number n) ? n.longValue() : fallback;
    }

    /**
     * Reads a runtime config double value with coded-default fallback.
     *
     * @param valuePath dot path under `values`
     * @param fallback fallback value when missing or invalid
     * @return configured double or fallback
     */
    public static double getDouble(String valuePath, double fallback) {
        Object value = getValue(valuePath);
        return (value instanceof Number n) ? n.doubleValue() : fallback;
    }

    /**
     * Reads a runtime config string-array value with coded-default fallback.
     *
     * Non-string elements are ignored.
     *
     * @param valuePath dot path under `values`
     * @param fallback fallback list when missing or invalid
     * @return immutable configured list or immutable fallback copy
     */
    public static List<String> getStringList(String valuePath, List<String> fallback) {
        Object value = getValue(valuePath);
        if (!(value instanceof JSONArray arr)) {
            return List.copyOf(fallback);
        }

        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            Object item = arr.opt(i);
            if (item instanceof String s) {
                out.add(s);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Returns the bitmap flags for a runtime config value path.
     *
     * Missing or invalid entries fall back to 0.
     *
     * @param valuePath dot path under `values`
     * @return flags bitmap in the range 0..2 for v1
     */
    public static int getFlags(String valuePath) {
        JSONObject entry = getEntry(valuePath);
        if (entry == null) {
            return 0;
        }
        Object raw = entry.opt(FLAGS_KEY);
        if (raw instanceof Number n) {
            int flags = n.intValue();
            return isValidFlagBitmap(flags) ? flags : 0;
        }
        return 0;
    }

    /**
     * Returns true when the value path is marked as not updating live runtime.
     *
     * @param valuePath dot path under `values`
     * @return true if FLAG_NO_RUNTIME_UPDATE is set
     */
    public static boolean doesNotUpdateRuntime(String valuePath) {
        return (getFlags(valuePath) & FLAG_NO_RUNTIME_UPDATE) != 0;
    }

    /**
     * Returns true when the value path requires explicit manual apply.
     *
     * @param valuePath dot path under `values`
     * @return true if FLAG_REQUIRES_EXPLICIT_APPLY is set
     */
    public static boolean requiresExplicitApply(String valuePath) {
        return (getFlags(valuePath) & FLAG_REQUIRES_EXPLICIT_APPLY) != 0;
    }

    /**
     * Updates an in-memory runtime config value by dot path under `values`.
     *
     * Notes:
     * - This changes ConfigManager memory only.
     * - No file write occurs.
     * - No module apply/update behavior occurs here.
     * - Missing intermediate objects are created automatically.
     *
     * @param valuePath dot path under `values`
     * @param value replacement value for the leaf entry
     * @throws IllegalArgumentException if the path is invalid or the value is not JSON-safe
     */
    public static synchronized void setValueInMemory(String valuePath, Object value) {
        if (valuePath == null || valuePath.isBlank()) {
            throw new IllegalArgumentException("[14007] ConfigManager.setValueInMemory: value path is null or blank.");
        }
        validateJsonCompatible(value);

        JSONObject rootCopy = new JSONObject(stateRoot.toString());
        JSONObject values = rootCopy.optJSONObject(VALUES_KEY);
        if (values == null) {
            values = new JSONObject();
            rootCopy.put(VALUES_KEY, values);
        }

        JSONObject parent = descendOrCreateParent(values, tokenize(valuePath));
        String leaf = lastToken(valuePath);

        JSONObject entry = parent.optJSONObject(leaf);
        if (entry == null) {
            entry = new JSONObject();
            entry.put(FLAGS_KEY, 0);
            parent.put(leaf, entry);
        }

        Object oldValue = defensiveCopyJsonValue(entry.opt(VALUE_KEY));
        int flags = (entry.opt(FLAGS_KEY) instanceof Number n && isValidFlagBitmap(n.intValue()))
                ? n.intValue() : 0;
        entry.put(VALUE_KEY, value == null ? JSONObject.NULL : value);
        stateRoot = rootCopy;
        Logger.log(Logger.TAG.INFO,
                "ConfigManager.setValueInMemory: path=" + valuePath +
                        " old=" + stringifyValueForLog(oldValue) +
                        " new=" + stringifyValueForLog(value) +
                        " flags=" + flags);
    }

    /**
     * Updates an in-memory flags bitmap by dot path under `values`.
     *
     * Notes:
     * - No runtime apply behavior occurs here.
     * - Missing intermediate objects are created automatically.
     *
     * @param valuePath dot path under `values`
     * @param flags bitmap flags in the v1-supported range 0..2
     * @throws IllegalArgumentException if path or flags are invalid
     */
    public static synchronized void setFlagsInMemory(String valuePath, int flags) {
        if (valuePath == null || valuePath.isBlank()) {
            throw new IllegalArgumentException("[14008] ConfigManager.setFlagsInMemory: value path is null or blank.");
        }
        if (!isValidFlagBitmap(flags)) {
            throw new IllegalArgumentException("[14009] ConfigManager.setFlagsInMemory: invalid flags bitmap=" + flags);
        }

        JSONObject rootCopy = new JSONObject(stateRoot.toString());
        JSONObject values = rootCopy.optJSONObject(VALUES_KEY);
        if (values == null) {
            values = new JSONObject();
            rootCopy.put(VALUES_KEY, values);
        }

        JSONObject parent = descendOrCreateParent(values, tokenize(valuePath));
        String leaf = lastToken(valuePath);

        JSONObject entry = parent.optJSONObject(leaf);
        if (entry == null) {
            entry = new JSONObject();
            entry.put(VALUE_KEY, JSONObject.NULL);
            parent.put(leaf, entry);
        }
        int oldFlags = (entry.opt(FLAGS_KEY) instanceof Number n && isValidFlagBitmap(n.intValue()))
                ? n.intValue() : 0;
        entry.put(FLAGS_KEY, flags);
        stateRoot = rootCopy;
        Logger.log(Logger.TAG.INFO,
                "ConfigManager.setFlagsInMemory: path=" + valuePath +
                        " oldFlags=" + oldFlags +
                        " newFlags=" + flags);
    }

    /**
     * Builds the coded default runtime config root.
     *
     * Version 1:
     * - Runtime defaults object is intentionally minimal until the full current-code
     *   values are migrated into the manual config file in a later pass.
     * - The structure is still fully valid and ready for merge/load behavior now.
     *
     * @return valid config root object with version and empty values section
     */
    private static JSONObject buildDefaultRoot() {
        return new JSONObject()
                .put(VERSION_KEY, VERSION_1)
                .put(VALUES_KEY, new JSONObject());
    }

    /**
     * Loads runtime config from disk with per-root and per-entry fallback to coded defaults.
     *
     * Strict resource rule:
     * - Buffered reader is always closed in the finally block, even when load fails.
     *
     * @return final merged root plus whether file load succeeded
     */
    private static LoadResult loadFromDiskWithFallback() {
        JSONObject defaults = buildDefaultRoot();
        BufferedReader reader = null;

        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent == null) {
                consoleError("[14001] ConfigManager: runtime config path has no parent: " + CONFIG_PATH);
                return new LoadResult(defaults, false);
            }

            if (!Files.exists(CONFIG_PATH)) {
                consoleWarn("[14002] ConfigManager: runtime config file missing, using coded defaults: " + CONFIG_PATH);
                return new LoadResult(defaults, false);
            }

            if (!Files.isRegularFile(CONFIG_PATH)) {
                consoleWarn("[14003] ConfigManager: runtime config path is not a file, using coded defaults: " + CONFIG_PATH);
                return new LoadResult(defaults, false);
            }

            reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8);
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                content.append(buffer, 0, read);
            }

            String raw = content.toString();
            if (raw.isBlank()) {
                consoleWarn("[14004] ConfigManager: runtime config file is blank, using coded defaults: " + CONFIG_PATH);
                return new LoadResult(defaults, false);
            }

            JSONObject parsed = new JSONObject(raw);
            JSONObject merged = mergeRoot(parsed, defaults);
            return new LoadResult(merged, true);

        } catch (InvalidPathException e) {
            consoleError("[14005] ConfigManager: invalid runtime config path: " + e.getMessage());
            return new LoadResult(defaults, false);
        } catch (IOException e) {
            consoleWarn("[14006] ConfigManager: runtime config read failed, using coded defaults: " + e.getMessage());
            return new LoadResult(defaults, false);
        } catch (JSONException e) {
            consoleWarn("[14010] ConfigManager: runtime config JSON parse failed, using coded defaults: " + e.getMessage());
            return new LoadResult(defaults, false);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException closeEx) {
                    consoleWarn("[14011] ConfigManager: runtime config reader close failed: " + closeEx.getMessage());
                }
            }
        }
    }

    /**
     * Merges the loaded root into coded defaults while validating version and `values` shape.
     *
     * Rules:
     * - Invalid/missing root version falls back to default version with console warning.
     * - Invalid/missing `values` object falls back to default empty values object.
     * - Individual leaf entries are validated recursively and may fall back/skip independently.
     *
     * @param loaded parsed runtime config file root
     * @param defaults coded default root
     * @return merged valid root
     */
    private static JSONObject mergeRoot(JSONObject loaded, JSONObject defaults) {
        JSONObject merged = new JSONObject(defaults.toString());

        int version = VERSION_1;
        Object loadedVersion = loaded.opt(VERSION_KEY);
        if (loadedVersion instanceof Number n) {
            version = n.intValue();
            if (version != VERSION_1) {
                consoleWarn("[14012] ConfigManager: unsupported config version " + version +
                        ", falling back to default version " + VERSION_1 + ".");
                version = VERSION_1;
            }
        } else if (loaded.has(VERSION_KEY)) {
            consoleWarn("[14013] ConfigManager: invalid version field type, falling back to default version " + VERSION_1 + ".");
        }
        merged.put(VERSION_KEY, version);

        JSONObject defaultValues = defaults.optJSONObject(VALUES_KEY);
        if (defaultValues == null) {
            defaultValues = new JSONObject();
        }

        JSONObject loadedValues = loaded.optJSONObject(VALUES_KEY);
        if (loadedValues == null) {
            if (loaded.has(VALUES_KEY)) {
                consoleWarn("[14014] ConfigManager: invalid `values` root type, using default values object.");
            }
            merged.put(VALUES_KEY, new JSONObject(defaultValues.toString()));
            return merged;
        }

        JSONObject mergedValues = mergeSectionRecursive(
                loadedValues,
                defaultValues,
                new ArrayList<>()
        );
        merged.put(VALUES_KEY, mergedValues);
        return merged;
    }

    /**
     * Recursively validates and merges a config section tree.
     *
     * Section rules:
     * - Intermediate nodes must be JSONObject containers.
     * - Leaf nodes must be objects containing:
     *   - `value`
     *   - `flags` (0..2 in v1)
     * - Invalid loaded leaves fall back to default leaf if available; otherwise they are skipped.
     *
     * @param loadedSection loaded subtree
     * @param defaultSection coded-default subtree
     * @param pathTokens current logical path
     * @return merged subtree
     */
    private static JSONObject mergeSectionRecursive(
            JSONObject loadedSection,
            JSONObject defaultSection,
            List<String> pathTokens
    ) {
        JSONObject result = new JSONObject(defaultSection.toString());

        for (String key : loadedSection.keySet()) {
            Object loadedChild = loadedSection.opt(key);
            Object defaultChild = defaultSection.opt(key);

            List<String> childPath = new ArrayList<>(pathTokens);
            childPath.add(key);
            String pathForLog = String.join(".", childPath);

            if (!(loadedChild instanceof JSONObject loadedObj)) {
                consoleWarn("[14015] ConfigManager: non-object config node ignored at `" + pathForLog + "`.");
                continue;
            }

            boolean looksLikeLeaf = loadedObj.has(VALUE_KEY) || loadedObj.has(FLAGS_KEY);
            boolean defaultIsLeaf = defaultChild instanceof JSONObject defObj &&
                    (defObj.has(VALUE_KEY) || defObj.has(FLAGS_KEY));

            if (looksLikeLeaf || defaultIsLeaf) {
                JSONObject defaultLeaf = defaultIsLeaf ? (JSONObject) defaultChild : null;
                JSONObject mergedLeaf = validateLeafOrFallback(loadedObj, defaultLeaf, pathForLog);
                if (mergedLeaf != null) {
                    result.put(key, mergedLeaf);
                }
                continue;
            }

            JSONObject defaultSubsection = (defaultChild instanceof JSONObject obj) ? obj : new JSONObject();
            JSONObject mergedSubsection = mergeSectionRecursive(loadedObj, defaultSubsection, childPath);
            result.put(key, mergedSubsection);
        }

        return result;
    }

    /**
     * Validates a single leaf entry or falls back to the default leaf when invalid.
     *
     * Leaf validity rules:
     * - Must contain `value`
     * - Must contain numeric `flags`
     * - Flags must be a valid v1 bitmap (0..2)
     *
     * @param loadedLeaf loaded leaf candidate
     * @param defaultLeaf default fallback leaf, may be null
     * @param pathForLog logical path for diagnostics
     * @return valid merged leaf or null if invalid and no fallback exists
     */
    private static JSONObject validateLeafOrFallback(JSONObject loadedLeaf, JSONObject defaultLeaf, String pathForLog) {
        try {
            if (!loadedLeaf.has(VALUE_KEY)) {
                throw new IllegalArgumentException("missing `value`");
            }
            if (!loadedLeaf.has(FLAGS_KEY)) {
                throw new IllegalArgumentException("missing `flags`");
            }

            Object flagsRaw = loadedLeaf.get(FLAGS_KEY);
            if (!(flagsRaw instanceof Number n)) {
                throw new IllegalArgumentException("`flags` is not numeric");
            }

            int flags = n.intValue();
            if (!isValidFlagBitmap(flags)) {
                throw new IllegalArgumentException("`flags` bitmap out of range: " + flags);
            }

            Object value = loadedLeaf.get(VALUE_KEY);
            validateJsonCompatible(value == JSONObject.NULL ? null : value);

            return new JSONObject()
                    .put(VALUE_KEY, value)
                    .put(FLAGS_KEY, flags);

        } catch (Exception e) {
            if (defaultLeaf != null) {
                consoleWarn("[14016] ConfigManager: invalid config leaf at `" + pathForLog +
                        "`, using coded default (" + e.getMessage() + ").");
                return new JSONObject(defaultLeaf.toString());
            }

            consoleWarn("[14017] ConfigManager: invalid config leaf ignored at `" + pathForLog +
                    "` (" + e.getMessage() + ").");
            return null;
        }
    }

    /**
     * Returns the leaf entry object for a dot path under the root `values` object.
     *
     * Missing or invalid traversal returns null.
     *
     * @param valuePath dot path under `values`
     * @return leaf entry object or null
     */
    private static JSONObject getEntry(String valuePath) {
        if (valuePath == null || valuePath.isBlank()) {
            return null;
        }

        JSONObject values = stateRoot.optJSONObject(VALUES_KEY);
        if (values == null) {
            return null;
        }

        List<String> tokens = tokenize(valuePath);
        if (tokens.isEmpty()) {
            return null;
        }

        Object current = values;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (!(current instanceof JSONObject obj)) {
                return null;
            }

            Object next = obj.opt(token);
            if (next == null) {
                return null;
            }

            if (i == tokens.size() - 1) {
                return (next instanceof JSONObject entry) ? entry : null;
            }
            current = next;
        }

        return null;
    }

    /**
     * Creates/descends all intermediate parent objects for a dot path.
     *
     * The final leaf token is not created here; caller handles the actual leaf object.
     *
     * @param valuesRoot root values object
     * @param tokens full path tokens
     * @return parent object for the last leaf
     */
    private static JSONObject descendOrCreateParent(JSONObject valuesRoot, List<String> tokens) {
        if (tokens.size() < 2) {
            return valuesRoot;
        }

        JSONObject current = valuesRoot;
        for (int i = 0; i < tokens.size() - 1; i++) {
            String token = tokens.get(i);
            JSONObject next = current.optJSONObject(token);
            if (next == null) {
                next = new JSONObject();
                current.put(token, next);
            }
            current = next;
        }
        return current;
    }

    /**
     * Splits a dot path into cleaned tokens.
     *
     * Empty tokens are removed so malformed repeated dots collapse harmlessly.
     *
     * @param path dot path
     * @return ordered token list
     */
    private static List<String> tokenize(String path) {
        if (path == null || path.isBlank()) {
            return Collections.emptyList();
        }

        String[] raw = path.split("\\.");
        List<String> out = new ArrayList<>(raw.length);
        for (String token : raw) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /**
     * Returns the last cleaned token of a dot path.
     *
     * @param path dot path
     * @return last token
     */
    private static String lastToken(String path) {
        List<String> tokens = tokenize(path);
        return tokens.get(tokens.size() - 1);
    }

    /**
     * Validates that a value is JSON-compatible for storage in runtime config memory.
     *
     * @param value candidate value
     * @throws IllegalArgumentException if the value contains unsupported types or invalid numbers
     */
    private static void validateJsonCompatible(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return;
        }

        if (value instanceof JSONObject obj) {
            for (String key : obj.keySet()) {
                validateJsonCompatible(obj.get(key));
            }
            return;
        }

        if (value instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) {
                validateJsonCompatible(arr.get(i));
            }
            return;
        }

        if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("invalid numeric JSON value");
            }
            return;
        }

        if (value instanceof String || value instanceof Boolean) {
            return;
        }

        throw new IllegalArgumentException("unsupported JSON value type: " + value.getClass().getName());
    }

    /**
     * Returns a defensive copy for JSON containers and the same reference for primitives/wrappers.
     *
     * @param value config value
     * @return copied JSON container or original primitive-like value
     */
    private static Object defensiveCopyJsonValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONObject obj) {
            return new JSONObject(obj.toString());
        }
        if (value instanceof JSONArray arr) {
            return new JSONArray(arr.toString());
        }
        return value;
    }

    /**
     * Returns true when the flags bitmap is valid for v1.
     *
     * @param flags candidate bitmap
     * @return true if 0..2 inclusive
     */
    private static boolean isValidFlagBitmap(int flags) {
        return flags >= 0 && flags <= 2;
    }

    /**
     * Renders a config value into a concise single-line log representation.
     *
     * @param value config value
     * @return printable text
     */
    private static String stringifyValueForLog(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }
        if (value instanceof JSONObject || value instanceof JSONArray) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    /**
     * Console-only warning diagnostic.
     *
     * @param message warning to print
     */
    private static void consoleWarn(String message) {
        System.out.println("[CONFIG][WARN] " + message);
    }

    /**
     * Console-only informational diagnostic.
     *
     * @param message info line to print
     */
    private static void consoleInfo(String message) {
        System.out.println("[CONFIG][INFO] " + message);
    }

    /**
     * Console-only error diagnostic.
     *
     * @param message error to print
     */
    private static void consoleError(String message) {
        System.err.println("[CONFIG][ERROR] " + message);
    }

    /**
     * Immutable load result for runtime config disk reads.
     */
    private static final class LoadResult {
        final JSONObject root;
        final boolean loadedFromFile;

        LoadResult(JSONObject root, boolean loadedFromFile) {
            this.root = root;
            this.loadedFromFile = loadedFromFile;
        }
    }
}
