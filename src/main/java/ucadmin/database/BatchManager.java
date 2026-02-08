package ucadmin.database;

import org.json.*;
import ucadmin.exceptions.BatchException;
import ucadmin.util.Logger;

import java.util.*;

import static ucadmin.database.DatabaseManager.ensureArraySize;

/**
 * BatchManager (final, validated)
 * ----------------------------------------------------------
 *  - Parses dot + array paths ("a.b[2].c")
 *  - Builds validated QueueManager.Batch objects
 *  - Performs strict, atomic JSON edits
 *  - Smartly creates objects/arrays when allowed
 *  - Aborts batch on first failure
 *  - Validates batches before queuing
 *  - Tied to QueueManager flush/shutdown lifecycle
 */
public final class BatchManager {

    private BatchManager() {}

    /* ----------------------------------------------------------
     * PATH PARSER + VALIDATION
     * ---------------------------------------------------------- */

    /** Represents a single segment of a JSON path. */
    private static final class PathToken {
        final String key;
        final Integer index;
        PathToken(String key, Integer index) { this.key = key; this.index = index; }
        boolean isArray() { return index != null; }
    }

    /**
     * Converts a JSON-style path (e.g. "a.b[2].c") into a list of
     * PathToken objects while performing syntax validation.
     *
     * Each segment is recorded as either an object key or an array
     * index, preserving access order.
     *
     * @param path JSON path string to be parsed
     * @return list of validated PathTokens
     * @throws BatchException if syntax or index parsing fails
     */
    private static List<PathToken> parsePath(String path) throws BatchException {
        Logger.log(Logger.TAG.DEBUG, "Parsing JSON path: " + path);

        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "Path validation failed: null or empty path.");
            throw new BatchException("JSON path cannot be null or empty.");
        }

        // basic syntax guards (we'll handle dot placement more flexibly below)
        if (path.contains("..") || path.startsWith(".") || path.endsWith(".")) {
            Logger.log(Logger.TAG.ERROR, "Path validation failed: invalid syntax (" + path + ")");
            throw new BatchException("Invalid JSON path syntax: " + path);
        }

        List<PathToken> tokens = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        StringBuilder num = new StringBuilder();
        boolean inBracket = false;

        char[] chars = path.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];

            if (!inBracket && ch == '\\') {
                if (i + 1 >= chars.length) {
                    Logger.log(Logger.TAG.ERROR, "Trailing escape in path: " + path);
                    throw new BatchException("Invalid escape in path: " + path);
                }
                key.append(chars[i + 1]);
                i++;
                continue;
            }

            if (ch == '.') {
                if (!inBracket) {
                    if (key.length() == 0) {
                        if (!tokens.isEmpty()) {
                            continue;
                        }
                        Logger.log(Logger.TAG.ERROR, "Invalid dot placement in path: " + path);
                        throw new BatchException("Invalid dot placement in path: " + path);
                    }
                    tokens.add(new PathToken(key.toString(), null));
                    key.setLength(0);
                    continue;
                }
            }

            if (ch == '[') {
                if (inBracket) {
                    Logger.log(Logger.TAG.ERROR, "Nested '[' without closing ']' in path: " + path);
                    throw new BatchException("Invalid '[' nesting in path: " + path);
                }

                if (i + 1 < chars.length && (chars[i + 1] == '\'' || chars[i + 1] == '"')) {
                    char quote = chars[i + 1];
                    i += 2;
                    StringBuilder quotedKey = new StringBuilder();
                    boolean closed = false;
                    while (i < chars.length) {
                        char qch = chars[i];
                        if (qch == '\\') {
                            if (i + 1 >= chars.length) {
                                Logger.log(Logger.TAG.ERROR, "Bad escape in quoted key: " + path);
                                throw new BatchException("Invalid escape in quoted key: " + path);
                            }
                            quotedKey.append(chars[i + 1]);
                            i += 2;
                            continue;
                        }
                        if (qch == quote) {
                            closed = true;
                            i++;
                            break;
                        }
                        quotedKey.append(qch);
                        i++;
                    }
                    if (!closed || i >= chars.length || chars[i] != ']') {
                        Logger.log(Logger.TAG.ERROR, "Unterminated quoted key in path: " + path);
                        throw new BatchException("Unterminated quoted key in path: " + path);
                    }

                    if (key.length() > 0) {
                        tokens.add(new PathToken(key.toString(), null));
                        key.setLength(0);
                    }
                    key.append(quotedKey);
                    continue;
                }

                inBracket = true;
                continue;
            }

            if (ch == ']') {
                if (!inBracket) {
                    Logger.log(Logger.TAG.ERROR, "Unmatched ']' in path: " + path);
                    throw new BatchException("Unmatched ']' in path: " + path);
                }
                inBracket = false;

                if (num.length() == 0) {
                    Logger.log(Logger.TAG.ERROR, "Empty array index in path: " + path);
                    throw new BatchException("Empty array index in path: " + path);
                }

                if (key.length() == 0 && tokens.isEmpty()) {
                    Logger.log(Logger.TAG.ERROR, "Array index without key is not allowed: " + path);
                    throw new BatchException("Array index without key is not allowed: " + path);
                }

                try {
                    int idx = Integer.parseInt(num.toString());
                    tokens.add(new PathToken(key.toString(), idx));
                } catch (NumberFormatException e) {
                    Logger.log(Logger.TAG.ERROR, "Invalid array index '" + num + "' in path: " + path);
                    throw new BatchException("Invalid array index: " + num + " in path " + path);
                }

                key.setLength(0);
                num.setLength(0);
                continue;
            }

            if (inBracket) {
                if (!Character.isWhitespace(ch)) {
                    if (!Character.isDigit(ch)) {
                        Logger.log(Logger.TAG.ERROR, "Non-numeric array index character '" + ch + "' in path: " + path);
                        throw new BatchException("Non-numeric array index in path: " + path);
                    }
                    num.append(ch);
                }
            } else {
                key.append(ch);
            }
        }

        if (inBracket) {
            Logger.log(Logger.TAG.ERROR, "Unclosed '[' in path: " + path);
            throw new BatchException("Unclosed '[' in path: " + path);
        }

        if (key.length() > 0) {
            tokens.add(new PathToken(key.toString(), null));
        }

        if (tokens.isEmpty()) {
            Logger.log(Logger.TAG.ERROR, "Parsed path is empty after validation: " + path);
            throw new BatchException("Parsed path is empty: " + path);
        }

        Logger.log(Logger.TAG.DEBUG, "Path parsed successfully with " + tokens.size() + " tokens.");
        return tokens;
    }

    /* ----------------------------------------------------------
     * JSON STRUCTURE HELPERS
     * ---------------------------------------------------------- */

    /**
     * Safely unwraps a JSON root value into the correct type.
     * If the root is not the expected type, throws a BatchException.
     */
    private static <T> T requireJsonType(Object root, Class<T> type, String context) throws BatchException {
        if (root == null || root == JSONObject.NULL) {
            throw new BatchException(context + ": root is null.");
        }

        if (!type.isInstance(root)) {
            String actual = root.getClass().getSimpleName();
            throw new BatchException(context + ": expected " + type.getSimpleName() + " but found " + actual);
        }

        return type.cast(root);
    }

    private static Object getChildObject(Object json, PathToken token) {
        Object current = json;
        if (token.key != null && !token.key.isEmpty()) {
            if (current instanceof JSONObject obj) {
                current = obj.has(token.key) ? obj.get(token.key) : obj;
            } else {
                return current;
            }
        }
        if (token.isArray() && current instanceof JSONArray arr) {
            if (token.index >= 0 && token.index < arr.length()) {
                current = arr.get(token.index);
            }
        }
        return current;
    }

    private static Object traverseFullPath(Object json, List<PathToken> tokens) {
        Object current = json;
        for (PathToken t : tokens) {
            if (t.key != null && !t.key.isEmpty()) {
                if (current instanceof JSONObject o) {
                    current = o.opt(t.key);
                } else {
                    break;
                }
            }
            if (t.isArray()) {
                if (current instanceof JSONArray arr) {
                    if (t.index >= 0 && t.index < arr.length()) {
                        current = arr.get(t.index);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        return current;
    }

    /**
     * Traverses a JSON hierarchy to locate the parent object of the
     * target key or array element referenced by a parsed JSON path.
     *
     * Optionally creates any missing intermediate objects or arrays
     * when {@code createMissing} is true.
     *
     * @param root the root JSONObject to traverse
     * @param tokens parsed path tokens representing each nested key
     * @param createMissing whether to automatically create missing elements
     * @return the parent object of the final key or array in the path
     * @throws BatchException if traversal encounters invalid structure or syntax
     */
    private static Object resolveParent(JSONObject root, List<PathToken> tokens, boolean createMissing)
            throws BatchException {

        Logger.log(Logger.TAG.DEBUG, "Resolving parent for path with " + tokens.size() + " tokens.");

        Object current = root;
        for (int i = 0; i < tokens.size() - 1; i++) {
            PathToken t = tokens.get(i);
            PathToken next = (i + 1 < tokens.size()) ? tokens.get(i + 1) : null;

            if (current instanceof JSONObject obj) {
                if (t.key == null || t.key.isEmpty()) {
                    Logger.log(Logger.TAG.ERROR, "Array index without key is not allowed.");
                    throw new BatchException("Array index without key is not allowed.");
                }
                if (!obj.has(t.key)) {
                    if (createMissing) {
                        Logger.log(Logger.TAG.DEBUG, "Creating missing key: " + t.key);
                        obj.put(t.key, (next != null && next.isArray()) ? new JSONArray() : new JSONObject());
                    } else {
                        Logger.log(Logger.TAG.ERROR, "Missing key during traversal: " + t.key);
                        throw new BatchException("Missing key: " + t.key);
                    }
                }
                current = obj.get(t.key);
                if (t.isArray())
                    current = navigateArray(current, t.index, createMissing, t.key, next);

            } else if (current instanceof JSONArray arr) {
                if (!t.isArray()) {
                    Logger.log(Logger.TAG.ERROR, "Expected array index for path segment.");
                    throw new BatchException("Expected array index for path segment.");
                }
                current = navigateArray(current, t.index, createMissing, t.key, next);

            } else {
                Logger.log(Logger.TAG.ERROR, "Invalid structure while traversing key: " + t.key);
                throw new BatchException("Invalid structure while traversing: " + t.key);
            }
        }

        Logger.log(Logger.TAG.DEBUG, "Parent object resolved successfully.");
        return current;
    }

    /**
     * Handles traversal of JSONArray elements by index.
     *
     * If {@code createMissing} is true and the requested index is at the
     * array’s boundary, new elements are appended automatically.
     *
     * @param current the current array object being navigated
     * @param index index within the array to access or create
     * @param createMissing whether to automatically append missing indices
     * @param keyName name of the key associated with the array
     * @param next the next PathToken (used to determine what to create)
     * @return the resolved or newly created array element
     * @throws BatchException if an invalid structure or out-of-range index is encountered
     */
    private static Object navigateArray(Object current, int index, boolean createMissing,
                                        String keyName, PathToken next) throws BatchException {

        if (!(current instanceof JSONArray arr)) {
            Logger.log(Logger.TAG.ERROR, "Expected array for key: " + keyName);
            throw new BatchException("Expected array for key: " + keyName);
        }

        if (index < 0) {
            Logger.log(Logger.TAG.ERROR, "Negative array index at path: " + keyName + "[" + index + "]");
            throw new BatchException("Negative array index at path: " + keyName + "[" + index + "]");
        }

        if (index < arr.length()) {
            Logger.log(Logger.TAG.DEBUG, "Accessed existing array index " + index + " for key: " + keyName);
            return arr.get(index);
        }

        if (createMissing) {
            int target = Math.min(index, arr.length());
            Object toAdd = (next != null && next.isArray()) ? new JSONArray() : new JSONObject();
            arr.put(toAdd);
            Logger.log(Logger.TAG.DEBUG, "Created new array element at index " + target + " for key: " + keyName);
            return toAdd;
        }

        Logger.log(Logger.TAG.ERROR, "Array index out of range for key: " + keyName + "[" + index + "]");
        throw new BatchException("Array index out of range for key: " + keyName + "[" + index + "]");
    }

    /* ----------------------------------------------------------
     * CORE JSON MUTATIONS
     * ---------------------------------------------------------- */

    /**
     * INTERNAL — Writes or updates a value at the specified JSON path.
     * Creates missing objects/arrays if createMissing == true.
     *
     * Behavior:
     *   • Traverses path token-by-token (dot + array notation).
     *   • Automatically creates intermediate objects/arrays when allowed.
     *   • Throws BatchException on structural mismatch or out-of-bounds.
     */
    private static void setAtPath(JSONObject root, String path, Object value, boolean createMissing)
            throws BatchException {

        Logger.log(Logger.TAG.DEBUG, "setAtPath called for path: " + path);

        if (root == null) {
            Logger.log(Logger.TAG.ERROR, "setAtPath failed — root is null");
            throw new BatchException("setAtPath: root is null");
        }
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "setAtPath failed — path is null or empty");
            throw new BatchException("setAtPath: path is null or empty");
        }

        List<PathToken> tokens = parsePath(path);
        if (tokens.isEmpty()) {
            Logger.log(Logger.TAG.ERROR, "setAtPath failed — no valid tokens for path: " + path);
            throw new BatchException("setAtPath: no valid tokens for path: " + path);
        }

        // Handle root-level assignment (e.g. single key)
        if (tokens.size() == 1) {
            PathToken t = tokens.get(0);
            if (t.isArray()) {
                if (t.key == null || t.key.isEmpty()) {
                    Logger.log(Logger.TAG.ERROR, "Cannot set array index directly at root: " + path);
                    throw new BatchException("Cannot set array index directly at root: " + path);
                }

                JSONArray arr;
                Object existing = root.opt(t.key);
                if (existing == null) {
                    if (!createMissing)
                        throw new BatchException("Missing array key at root: " + path);
                    arr = new JSONArray();
                    root.put(t.key, arr);
                } else if (existing instanceof JSONArray) {
                    arr = (JSONArray) existing;
                } else {
                    throw new BatchException("Expected array at root key: " + t.key);
                }

                insertIntoArray(arr, t.index, value);
                Logger.log(Logger.TAG.DEBUG, "Value inserted into root array key '" + t.key + "' at index " + t.index);
                return;
            }
            root.put(t.key, value);
            Logger.log(Logger.TAG.DEBUG, "Root-level key '" + t.key + "' updated successfully.");
            return;
        }

        // Traverse down to parent of final node
        Object parent = resolveParent(root, tokens, createMissing);
        PathToken last = tokens.get(tokens.size() - 1);

        try {
            if (parent instanceof JSONObject obj) {
                if (last.isArray()) {
                    JSONArray arr;
                    Object existing = obj.opt(last.key);
                    if (existing == null) {
                        if (!createMissing)
                            throw new BatchException("Missing array key at path: " + path);
                        arr = new JSONArray();
                        obj.put(last.key, arr);
                    } else if (existing instanceof JSONArray) {
                        arr = (JSONArray) existing;
                    } else {
                        throw new BatchException("Expected array at path: " + path);
                    }

                    insertIntoArray(arr, last.index, value);
                    Logger.log(Logger.TAG.DEBUG, "Value inserted into array at path: " + path);
                } else {
                    obj.put(last.key, value);
                    Logger.log(Logger.TAG.DEBUG, "Value set at object path: " + path);
                }
            } else if (parent instanceof JSONArray arr) {
                if (!last.isArray())
                    throw new BatchException("Expected array index for path: " + path);

                insertIntoArray(arr, last.index, value);
                Logger.log(Logger.TAG.DEBUG, "Value inserted into array at index " + last.index + " for path: " + path);
            } else {
                throw new BatchException("Invalid parent structure at path: " + path);
            }
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "setAtPath exception at " + path + ": " + e.getMessage());
            throw new BatchException("setAtPath failed at " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * INTERNAL — Removes a key or array index at the specified JSON path.
     *
     * Behavior:
     *   • Works for both object keys and array elements.
     *   • Throws BatchException on type mismatch or invalid path.
     */
    private static void removeAtPath(JSONObject root, String path) throws BatchException {
        Logger.log(Logger.TAG.DEBUG, "removeAtPath called for path: " + path);

        if (root == null) {
            Logger.log(Logger.TAG.ERROR, "removeAtPath failed — root is null");
            throw new BatchException("removeAtPath: root is null");
        }

        // Per your current contract, null/blank is NOT allowed here
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "removeAtPath failed — path is null or empty");
            throw new BatchException("removeAtPath: path is null or empty");
        }

        // Parse tokens
        List<PathToken> tokens = parsePath(path);
        if (tokens.isEmpty()) {
            Logger.log(Logger.TAG.ERROR, "removeAtPath failed — no valid tokens for path: " + path);
            throw new BatchException("removeAtPath: no valid tokens for path: " + path);
        }

        // ─────────────────────────────────────────────────────────
        // Case A: single token at root (either a plain key OR key[index])
        // Examples:
        //   "username"           -> remove root key
        //   "inventory[0]"       -> remove element 0 from array at root key "inventory"
        // ─────────────────────────────────────────────────────────
        if (tokens.size() == 1) {
            PathToken t = tokens.get(0);

            // A1) key[index] — root-level array element removal
            if (t.isArray()) {
                // parent is the root object; the token's "key" must exist and be a JSONArray
                Object target = root.opt(t.key);
                if (!(target instanceof org.json.JSONArray)) {
                    throw new BatchException("removeAtPath: target at root key '" + t.key + "' is not an array");
                }

                org.json.JSONArray arr = (org.json.JSONArray) target;
                int idx = t.index;
                if (idx < 0 || idx >= arr.length()) {
                    throw new BatchException("removeAtPath: array index out of bounds [" + idx + "] for '" + t.key + "'");
                }

                // Rebuild array without the removed element (org.json lacks remove by index)
                org.json.JSONArray rebuilt = new org.json.JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    if (i != idx) rebuilt.put(arr.get(i));
                }

                // Clear and copy back
                DatabaseManager.clearJSONArray(arr);
                for (int i = 0; i < rebuilt.length(); i++) {
                    arr.put(rebuilt.get(i));
                }

                Logger.log(Logger.TAG.DEBUG, "Root array element removed: " + t.key + "[" + idx + "]");
                return;
            }

            // A2) plain key — remove the root-level key
            root.remove(t.key);
            Logger.log(Logger.TAG.DEBUG, "Root-level key '" + t.key + "' removed successfully.");
            return;
        }

        // ─────────────────────────────────────────────────────────
        // Case B: nested path — resolve parent and remove at last segment
        // Examples:
        //   "stats.level"          -> remove key "level" from object at "stats"
        //   "stats.items[2]"       -> remove index 2 from array at "stats.items"
        // ─────────────────────────────────────────────────────────
        Object parent = resolveParent(root, tokens, false);
        PathToken last = tokens.get(tokens.size() - 1);

        try {
            // B1) Parent is an object: last must be a key (NOT an array index here)
            if (parent instanceof org.json.JSONObject) {
                org.json.JSONObject obj = (org.json.JSONObject) parent;

                if (last.isArray()) {
                    throw new BatchException("Expected object field for removal, got array index at: " + path);
                }
                if (!obj.has(last.key)) {
                    throw new BatchException("Key not found for removal: " + last.key);
                }

                obj.remove(last.key);
                Logger.log(Logger.TAG.DEBUG, "Removed key '" + last.key + "' from object at path: " + path);
                return;
            }

            // B2) Parent is an array: last must be an array index
            if (parent instanceof org.json.JSONArray) {
                org.json.JSONArray arr = (org.json.JSONArray) parent;

                if (!last.isArray()) {
                    throw new BatchException("Expected array index for removal at: " + path);
                }
                int idx = last.index;
                if (idx < 0 || idx >= arr.length()) {
                    throw new BatchException("Array index out of bounds for removal: " + idx);
                }

                org.json.JSONArray rebuilt = new org.json.JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    if (i != idx) rebuilt.put(arr.get(i));
                }

                DatabaseManager.clearJSONArray(arr);
                for (int i = 0; i < rebuilt.length(); i++) {
                    arr.put(rebuilt.get(i));
                }

                Logger.log(Logger.TAG.DEBUG, "Removed array index " + idx + " at path: " + path);
                return;
            }

            // Invalid parent structure
            throw new BatchException("Invalid parent structure for remove at: " + path);

        } catch (BatchException e) {
            Logger.log(Logger.TAG.ERROR, "removeAtPath error at " + path + ": " + e.getMessage());
            throw e;
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "removeAtPath exception at " + path + ": " + e.getMessage());
            throw new BatchException("removeAtPath failed at " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * INTERNAL — Appends a new value to a JSON array at the specified path.
     * Automatically creates the array if it does not exist.
     *
     * @param root  root JSON object
     * @param path  target path
     * @param value value to append
     * @throws BatchException if the structure is invalid or path malformed
     */
    private static void appendAtPath(JSONObject root, String path, Object value) throws BatchException {
        Logger.log(Logger.TAG.DEBUG, "appendAtPath called for path: " + path);

        if (path != null) {
            String trimmed = path.trim();
            if (trimmed.endsWith("[]")) {
                path = trimmed.substring(0, trimmed.length() - 2);
            }
        }

        List<PathToken> tokens = parsePath(path);
        if (tokens.isEmpty())
            throw new BatchException("Empty path.");

        Object parent = resolveParent(root, tokens, true);
        PathToken last = tokens.get(tokens.size() - 1);

        try {
            if (parent instanceof JSONObject obj) {
                Object arrObj = obj.opt(last.key);
                if (arrObj == null) {
                    JSONArray arr = new JSONArray();
                    arr.put(value);
                    obj.put(last.key, arr);
                    Logger.log(Logger.TAG.DEBUG, "Created new array and appended value at path: " + path);
                    return;
                }
                if (!(arrObj instanceof JSONArray))
                    throw new BatchException("Target not array at path: " + path);
                ((JSONArray) arrObj).put(value);
                Logger.log(Logger.TAG.DEBUG, "Appended value to existing array at path: " + path);
            } else if (parent instanceof JSONArray arr) {
                arr.put(value);
                Logger.log(Logger.TAG.DEBUG, "Appended value to unnamed array at path: " + path);
            } else {
                throw new BatchException("Invalid structure for appendAtPath.");
            }
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "appendAtPath exception at " + path + ": " + e.getMessage());
            throw new BatchException("appendAtPath failed at " + path + ": " + e.getMessage(), e);
        }
    }

    private static void insertIntoArray(JSONArray arr, int index, Object value) throws BatchException {
        if (index < 0) {
            throw new BatchException("Negative array index at path.");
        }

        int len = arr.length();
        int target = Math.min(index, len);

        JSONArray rebuilt = new JSONArray();
        for (int i = 0; i < len; i++) {
            if (i == target) {
                rebuilt.put(value);
            }
            rebuilt.put(arr.get(i));
        }
        if (target == len) {
            rebuilt.put(value);
        }

        DatabaseManager.clearJSONArray(arr);
        for (int i = 0; i < rebuilt.length(); i++) {
            arr.put(rebuilt.get(i));
        }
    }

    /* ----------------------------------------------------------
     * BATCH BUILDERS (validated)
     * ---------------------------------------------------------- */

    /**
     * Validates the structural integrity of a batch before enqueueing.
     *
     * Checks that the batch is not null or empty, and that all contained
     * operations have unique, non-blank names.
     *
     * @param batch the batch to validate
     * @throws BatchException if validation fails
     */
    private static void validateBatch(QueueManager.Batch batch) throws BatchException {
        Logger.log(Logger.TAG.DEBUG, "Validating batch integrity...");

        if (batch == null || batch.isEmpty()) {
            Logger.log(Logger.TAG.ERROR, "Batch validation failed: empty or null batch.");
            throw new BatchException("Cannot enqueue empty batch.");
        }

        Set<String> names = new HashSet<>();
        for (QueueManager.WriteOp op : batch.ops()) {
            if (op.name == null || op.name.isBlank()) {
                Logger.log(Logger.TAG.ERROR, "Batch validation failed: unnamed operation detected.");
                throw new BatchException("Batch contains unnamed operation.");
            }
            if (!names.add(op.name)) {
                Logger.log(Logger.TAG.ERROR, "Batch validation failed: duplicate operation name '" + op.name + "'");
                throw new BatchException("Duplicate operation name in batch: " + op.name);
            }
        }

        Logger.log(Logger.TAG.DEBUG, "Batch validation successful (" + names.size() + " ops).");
    }

    /**
     * Builds a batch that performs a write operation to a JSON path.
     *
     * @param jsonPath target JSON path
     * @param value value to write at that path
     * @param createMissing whether to create intermediate nodes
     * @return validated batch ready for queueing
     * @throws BatchException if batch construction fails
     */
    public static QueueManager.Batch buildWriteJSONPath(String jsonPath, Object value, boolean createMissing)
            throws BatchException {
        if (jsonPath == null || jsonPath.isBlank()) {
            Logger.log(Logger.TAG.DEBUG, "buildWriteJSONPath: empty path → redirecting to replaceRoot");
            if (value == null) {
                return buildReplaceRoot(new JSONObject());
            }
            if (value instanceof JSONObject obj) {
                return buildReplaceRoot(obj);
            }
            throw new BatchException("writeJSONPath(<root>): replacement must be a JSONObject.");
        }

        Logger.log(Logger.TAG.DEBUG, "Building writeJSONPath batch for: " + jsonPath);

        QueueManager.Batch batch = new QueueManager.Batch();
        batch.add(new QueueManager.WriteOp("writeJSONPath", json -> {
            try {
                setAtPath(json, jsonPath, value, createMissing);
            } catch (BatchException e) {
                throw new RuntimeException(e);
            }
        }));

        validateBatch(batch);
        Logger.log(Logger.TAG.DEBUG, "writeJSONPath batch built successfully.");
        return batch;
    }

    /**
     * Builds a batch that removes a value at the specified JSON path.
     *
     * @param jsonPath target JSON path to remove
     * @return validated batch ready for queueing
     * @throws BatchException if batch construction fails
     */
    public static QueueManager.Batch buildRemoveJSONPath(String jsonPath) throws BatchException {
        if (jsonPath == null || jsonPath.isBlank()) {
            Logger.log(Logger.TAG.DEBUG, "buildRemoveJSONPath: empty path → redirecting to replaceRoot (empty object)");
            return buildReplaceRoot(new JSONObject());
        }

        Logger.log(Logger.TAG.DEBUG, "Building removeJSONPath batch for: " + jsonPath);

        QueueManager.Batch batch = new QueueManager.Batch();
        batch.add(new QueueManager.WriteOp("removeJSONPath", json -> {
            try {
                removeAtPath(json, jsonPath);
            } catch (BatchException e) {
                throw new RuntimeException(e);
            }
        }));

        validateBatch(batch);
        Logger.log(Logger.TAG.DEBUG, "removeJSONPath batch built successfully.");
        return batch;
    }

    /**
     * Builds a batch that appends a value to a JSON array.
     *
     * @param jsonPath target JSON array path
     * @param value value to append to the array
     * @return validated batch ready for queueing
     * @throws BatchException if batch construction fails
     */
    public static QueueManager.Batch buildAppendJSONArray(String jsonPath, Object value) throws BatchException {
        Logger.log(Logger.TAG.DEBUG, "Building appendJSONArray batch for: " + jsonPath);

        final String normalizedPath = (jsonPath == null || jsonPath.isBlank()) ? "" : jsonPath;

        QueueManager.Batch batch = new QueueManager.Batch();
        batch.add(new QueueManager.WriteOp("appendJSONArray", json -> {
            try {
                // Delegate all type and root handling to appendAtPath
                appendAtPath(json, normalizedPath, value);
            } catch (BatchException e) {
                throw new RuntimeException(e);
            }
        }));

        validateBatch(batch);
        Logger.log(Logger.TAG.DEBUG, "appendJSONArray batch built successfully.");
        return batch;
    }

    /**
     * Builds a batch that inserts a value into a JSON array at the specified index.
     *
     * Behavior:
     *   - Traverses to the target array path.
     *   - Validates that the target exists and is a JSONArray.
     *   - Inserts the new element at the specified index (shifts subsequent elements).
     *   - Throws BatchException if type mismatch or index invalid.
     *
     * @param jsonPath target JSON array path
     * @param index index to insert at
     * @param value value to insert
     * @return validated batch ready for queueing
     * @throws BatchException if batch construction fails
     */
    public static QueueManager.Batch buildInsertJSONArray(String jsonPath, int index, Object value)
            throws BatchException {

        Logger.log(Logger.TAG.DEBUG, "Building insertJSONArray batch for: " + jsonPath + " [index=" + index + "]");

        QueueManager.Batch batch = new QueueManager.Batch();
        batch.add(new QueueManager.WriteOp("insertJSONArray", json -> {
            try {
                JSONArray targetArray;

                // --- CASE 1: root-level array insert ----------------------------
                if (jsonPath == null || jsonPath.isBlank()) {
                    throw new BatchException("insertJSONArray: root arrays are not allowed.");
                }

                // --- CASE 2: normal nested path --------------------------------
                else {
                    List<PathToken> tokens = parsePath(jsonPath);
                    Object parent = resolveParent(json, tokens, true);
                    PathToken last = tokens.get(tokens.size() - 1);

                    JSONObject parentObj = requireJsonType(parent, JSONObject.class, "insertJSONArray(parent)");

                    Object maybeArr = parentObj.opt(last.key);
                    if (maybeArr == null) {
                        maybeArr = new JSONArray();
                        parentObj.put(last.key, maybeArr);
                    }

                    targetArray = requireJsonType(maybeArr, JSONArray.class, "insertJSONArray(target)");
                }

                // --- Validate index and perform insert --------------------------
                int len = targetArray.length();
                if (index < 0)
                    throw new BatchException("insertJSONArray: invalid index " + index + " (len=" + len + ")");

                int target = Math.min(index, len);
                JSONArray result = new JSONArray();
                for (int i = 0; i < len; i++) {
                    if (i == target) result.put(value);
                    result.put(targetArray.get(i));
                }
                if (target == len) result.put(value);

                DatabaseManager.clearJSONArray(targetArray);
                for (int i = 0; i < result.length(); i++)
                    targetArray.put(result.get(i));

                Logger.log(Logger.TAG.INFO, "insertJSONArray succeeded for path: " +
                        (jsonPath == null || jsonPath.isBlank() ? "<root>" : jsonPath));

            } catch (BatchException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                Logger.log(Logger.TAG.ERROR, "insertJSONArray failed for path: " +
                        jsonPath + " | " + e.getMessage());
                throw new RuntimeException(new BatchException("insertJSONArray failed for path: " + jsonPath, e));
            }
        }));

        validateBatch(batch);
        Logger.log(Logger.TAG.DEBUG, "insertJSONArray batch built successfully.");
        return batch;
    }

    /**
     * Builds a batch that replaces a JSON array element at the given index.
     *
     * Behavior:
     *   - Traverses to target array.
     *   - Validates that it exists and index is within range.
     *   - Replaces element in place.
     *
     * @param jsonPath target JSON array path
     * @param index index to replace
     * @param value value to replace with
     * @return validated batch ready for queueing
     * @throws BatchException if batch construction fails
     */
    public static QueueManager.Batch buildReplaceJSONArray(String jsonPath, int index, Object value) throws BatchException {
        Logger.log(Logger.TAG.DEBUG, "Building replaceJSONArray batch for: " + jsonPath + " [index=" + index + "]");

        QueueManager.Batch batch = new QueueManager.Batch();
        batch.add(new QueueManager.WriteOp("replaceJSONArray", json -> {
            try {
                if (jsonPath == null || jsonPath.isBlank()) {
                    throw new BatchException("replaceJSONArray: root arrays are not allowed.");
                }

                List<PathToken> tokens = parsePath(jsonPath);
                Object parent = resolveParent(json, tokens, false);
                PathToken last = tokens.get(tokens.size() - 1);

                if (!(parent instanceof JSONObject obj))
                    throw new BatchException("Expected JSONObject parent at path: " + jsonPath);

                Object targetValue = obj.opt(last.key);
                if (!(targetValue instanceof JSONArray arr))
                    throw new BatchException("Target path is not a JSONArray: " + jsonPath);

                if (index < 0)
                    throw new BatchException("Index out of range (" + index + ") for array of length " + arr.length());

                int targetIndex = Math.min(index, arr.length());
                if (targetIndex == arr.length()) {
                    arr.put(value);
                } else {
                    arr.put(targetIndex, value);
                }

            } catch (Exception e) {
                Logger.log(Logger.TAG.ERROR, "replaceJSONArray failed for path: " + jsonPath + " | " + e.getMessage());
                throw new RuntimeException(new BatchException("replaceJSONArray failed for path: " + jsonPath, e));
            }
        }));

        validateBatch(batch);
        Logger.log(Logger.TAG.DEBUG, "replaceJSONArray batch built successfully.");
        return batch;
    }

    /**
     * Builds a batch that renames a key within a JSONObject at the specified parent path.
     *
     * Behavior:
     *   - Navigates to parent JSONObject.
     *   - Moves value from oldKey to newKey.
     *   - Throws BatchException if parent invalid or key conflicts.
     */
    public static QueueManager.Batch buildRenameJSONKey(String parentPath, String oldKey, String newKey)
            throws BatchException {
        Logger.log(Logger.TAG.DEBUG, "Building batch: renameJSONKey (" + oldKey + " → " + newKey + ") at " + parentPath);

        QueueManager.Batch batch = new QueueManager.Batch();
        batch.add(new QueueManager.WriteOp("renameJSONKey", json -> {
            try {
                // Root-level rename if parentPath is empty/null
                if (parentPath == null || parentPath.isBlank()) {
                    JSONObject rootObj = requireJsonType(json, JSONObject.class, "renameJSONKey (root)");
                    if (!rootObj.has(oldKey))
                        throw new BatchException("renameJSONKey: old key not found at root: " + oldKey);
                    if (rootObj.has(newKey))
                        throw new BatchException("renameJSONKey: new key already exists at root: " + newKey);

                    Object val = rootObj.get(oldKey);
                    rootObj.put(newKey, val);
                    rootObj.remove(oldKey);
                    Logger.log(Logger.TAG.INFO, "renameJSONKey completed successfully at root.");
                    return;
                }

                // Non-root rename path
                List<PathToken> tokens = parsePath(parentPath);

                // FIX: resolve to the actual target object (not its parent)
                Object parent = (tokens.size() == 1)
                        ? getChildObject(json, tokens.get(0))
                        : traverseFullPath(json, tokens);

                JSONObject obj = requireJsonType(parent, JSONObject.class, "renameJSONKey");
                if (!obj.has(oldKey))
                    throw new BatchException("Old key not found: " + oldKey);
                if (obj.has(newKey))
                    throw new BatchException("New key already exists: " + newKey);

                Object val = obj.get(oldKey);
                obj.put(newKey, val);
                obj.remove(oldKey);

                Logger.log(Logger.TAG.INFO, "renameJSONKey completed successfully at " + parentPath);

            } catch (BatchException e) {
                Logger.log(Logger.TAG.ERROR, "renameJSONKey failed at " + parentPath + ": " + e.getMessage());
                throw new RuntimeException(e);
            } catch (Exception e) {
                Logger.log(Logger.TAG.ERROR, "renameJSONKey unexpected error: " + e.getMessage());
                throw new RuntimeException(new BatchException("renameJSONKey failed at " + parentPath, e));
            }
        }));

        validateBatch(batch);
        Logger.log(Logger.TAG.DEBUG, "Batch validated for renameJSONKey.");
        return batch;
    }

    /**
     * Builds a batch that moves a value from one JSON path to another atomically.
     *
     * Behavior:
     *   - Operates entirely in-memory on the cached JSON structure.
     *   - Writes the given value to the destination path (creating intermediate objects/arrays if needed).
     *   - Removes the source field or array element at fromPath.
     *   - Throws BatchException if the source or destination structure is invalid.
     *
     * Usage:
     *   QueueManager.Batch batch = BatchManager.buildMoveJSONPath(fromPath, toPath, value);
     */
    public static QueueManager.Batch buildMoveJSONPath(String fromPath, String toPath, Object value)
            throws BatchException {
        Logger.log(Logger.TAG.DEBUG, "Building batch: moveJSONPath (" + fromPath + " → " + toPath + ")");

        if (fromPath == null || fromPath.isBlank()) {
            throw new BatchException("moveJSONPath: moving *from* root is not supported.");
        }
        if (toPath != null && fromPath.equals(toPath)) {
            throw new BatchException("moveJSONPath: fromPath and toPath are identical");
        }

        QueueManager.Batch batch = new QueueManager.Batch();
        batch.add(new QueueManager.WriteOp("moveJSONPath", json -> {
            try {
                if (toPath == null || toPath.isBlank()) {
                    // Move TO root: replace the root safely without casting arrays to objects.
                    JSONObject rootObj = requireJsonType(json, JSONObject.class, "moveJSONPath(toRoot)");

                    // Only JSONObject/JSONArray are supported for root replacement.
                    if (value instanceof JSONObject objVal) {
                        DatabaseManager.clearJSONObject(rootObj);
                        for (String k : objVal.keySet()) rootObj.put(k, objVal.get(k));
                    } else if (value instanceof JSONArray arrVal) {
                        DatabaseManager.clearJSONObject(rootObj);
                        // Represent array at root by numeric-object keys (system’s existing convention)
                        for (int i = 0; i < arrVal.length(); i++) rootObj.put(String.valueOf(i), arrVal.get(i));
                    } else {
                        throw new BatchException("moveJSONPath: cannot set a scalar as root; provide JSONObject or JSONArray.");
                    }

                    Logger.log(Logger.TAG.INFO, "moveJSONPath executed successfully (→ <root>)");
                } else {
                    // Normal move
                    setAtPath(json, toPath, value, /*createMissing*/ true);
                    removeAtPath(json, fromPath);
                    Logger.log(Logger.TAG.INFO, "moveJSONPath executed successfully (" + fromPath + " → " + toPath + ")");
                }
            } catch (BatchException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(new BatchException("moveJSONPath failed (" + fromPath + " → " + toPath + ")", e));
            }
        }));

        validateBatch(batch);
        Logger.log(Logger.TAG.DEBUG, "Batch validated for moveJSONPath.");
        return batch;
    }

    /**
     * Builds a batch that replaces the root JSONObject or JSONArray entirely.
     *
     * Behavior:
     *   - If newRoot is a JSONObject, clears the existing object and copies all keys.
     *   - If newRoot is a JSONArray, clears the existing root and reindexes array elements.
     *   - Throws BatchException if the provided root type is invalid.
     *
     * @param newRoot the new root object or array to replace the current root
     * @return a validated batch ready for execution
     * @throws BatchException if newRoot is not a valid JSON type
     */
    public static QueueManager.Batch buildReplaceRoot(Object newRoot) throws BatchException {
        String kind = (newRoot == null || newRoot == JSONObject.NULL) ? "null" : newRoot.getClass().getSimpleName();
        Logger.log(Logger.TAG.DEBUG, "Building batch: replaceRoot (" + kind + ")");

        if (newRoot == null || newRoot == JSONObject.NULL) {
            throw new BatchException("replaceRoot: newRoot cannot be null.");
        }

        // ---- TAKE A DEEP, DETACHED SNAPSHOT OF newRoot (avoid aliasing) ----
        final JSONObject snapshotObject;
        final JSONArray  snapshotArray;
        final boolean    isObject;
        final boolean    isArray;

        if (newRoot instanceof JSONObject o) {
            snapshotObject = new JSONObject(o.toString()); // deep copy
            snapshotArray  = null;
            isObject       = true;
            isArray        = false;
        } else if (newRoot instanceof JSONArray a) {
            snapshotArray  = new JSONArray(a.toString());  // deep copy
            snapshotObject = null;
            isObject       = false;
            isArray        = true;
        } else {
            snapshotObject = null;
            snapshotArray  = null;
            isObject       = false;
            isArray        = false;
        }

        QueueManager.Batch batch = new QueueManager.Batch();
        batch.add(new QueueManager.WriteOp("replaceRoot", json -> {
            try {
                // Root in our DB is an OBJECT; never cast an array to object.
                JSONObject target = requireJsonType(json, JSONObject.class, "replaceRoot");

                // Clear target first (we're about to replace the whole root)
                DatabaseManager.clearJSONObject(target);

                if (isObject) {
                    // copy keys from the detached snapshot
                    for (String k : snapshotObject.keySet()) {
                        target.put(k, snapshotObject.get(k));
                    }
                } else if (isArray) {
                    // store array at root using numeric-object keys (legacy-consistent)
                    for (int i = 0; i < snapshotArray.length(); i++) {
                        target.put(String.valueOf(i), snapshotArray.get(i));
                    }
                } else {
                    // scalars: wrap so the root remains an object
                    target.put("value", newRoot);
                }

                Logger.log(Logger.TAG.INFO, "replaceRoot executed successfully.");
            } catch (BatchException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(new BatchException("replaceRoot failed", e));
            }
        }));

        validateBatch(batch);
        Logger.log(Logger.TAG.DEBUG, "Batch validated for replaceRoot.");
        return batch;
    }

    /* ----------------------------------------------------------
     * ADMIN / UTILITY
     * ---------------------------------------------------------- */

    /**
     * Flushes all queued batches. Used for manual database or
     * shutdown synchronization.
     */
    public static void flushAll() {
        try {
            Logger.log(Logger.TAG.SYSTEM, "BatchManager: flushing all queues...");
            QueueManager.flushAll(true);
            Logger.log(Logger.TAG.INFO, "BatchManager: flushAll completed successfully.");
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "BatchManager.flushAll failed: " + e.getMessage());
        }
    }

    /**
     * Safely flushes all queues and performs shutdown cleanup.
     * Intended for use during bot termination.
     */
    public static void shutdown() {
        try {
            Logger.log(Logger.TAG.SYSTEM, "BatchManager shutting down: flushing queue...");
            QueueManager.flushAll(true);
            Logger.log(Logger.TAG.INFO, "BatchManager shutdown complete. All batches flushed.");
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "BatchManager shutdown failed: " + e.getMessage());
        }
    }

}
