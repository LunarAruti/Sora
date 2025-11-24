package ucadmin.main;

import org.json.JSONArray;
import org.json.JSONObject;
import ucadmin.database.DatabaseManager;
import ucadmin.database.QueueManager;
import ucadmin.exceptions.DatabaseException;
import ucadmin.exceptions.QueueException;
import ucadmin.util.Logger;

public class TestingGrounds {
    public static void TestingGrounds() {
        try {
            // ================== ULTIMATE INTEGRATION TEST (single final flush) ==================
            final String ROOT       = "C:\\Users\\lunar\\OneDrive\\Desktop\\current\\UC_Admin\\database\\corrupt";
            final String SUITE      = ROOT + "\\dm_suite_stress";
            final String SUB_A      = SUITE + "\\A";
            final String SUB_B      = SUITE + "\\B";
            final String SUB_C      = SUITE + "\\C";
            final String COPIES     = SUITE + "\\copies";

            final String TXT_1      = SUITE + "\\notes.txt";
            final String TXT_2      = SUITE + "\\renamed.txt";
            final String TXT_COPY   = COPIES + "\\notes_copy.txt";

            final String JSON_A     = SUITE + "\\main.json";
            final String JSON_B     = SUITE + "\\backup.json";
            final String JSON_C     = SUITE + "\\alt.json";

            DatabaseManager.createFolder(SUITE);
            DatabaseManager.createFolder(SUB_A);
            DatabaseManager.createFolder(SUB_B);
            DatabaseManager.createFolder(COPIES);

            DatabaseManager.folderExists(SUITE);
            DatabaseManager.folderExists(SUB_C);                 // expect false (not created)

            DatabaseManager.createFile(TXT_1);
            DatabaseManager.fileExists(TXT_1);
            DatabaseManager.getExtension(TXT_1);
            DatabaseManager.getParentPath(TXT_1);
            DatabaseManager.getFileName(TXT_1);

            DatabaseManager.renameFile(TXT_1, TXT_2);
            DatabaseManager.copyFile(TXT_2, TXT_COPY);

            DatabaseManager.listFiles(SUITE);
            DatabaseManager.listFolders(SUITE);

// --- JSON creation (both overloads) + integrity pass ---
            org.json.JSONObject seed = new org.json.JSONObject()
                    .put("version", 1)
                    .put("meta", new org.json.JSONObject()
                            .put("owner", "root")
                            .put("tags", new org.json.JSONArray().put("alpha").put("beta")))
                    .put("records", new org.json.JSONArray());

            DatabaseManager.createJSON(JSON_A, seed);            // with defaults
            DatabaseManager.createJSON(JSON_B);                  // empty object
            DatabaseManager.createJSON(JSON_C, new org.json.JSONObject().put("hello", "world"));

            DatabaseManager.ensureJSONIntegrity(JSON_A, true, true);

// --- JSON path writes/reads/types/contains/list-keys ---
            DatabaseManager.writeJSONPath(JSON_A, "version", 2, false);           // update existing
            DatabaseManager.writeJSONPath(JSON_A, "meta.flags.audit.enabled", true, true); // deep create
            DatabaseManager.writeJSONPath(JSON_A, "meta.owner", "admin", false);  // overwrite

            DatabaseManager.readJSONPath(JSON_A, "version");
            DatabaseManager.readJSONPath(JSON_A, "meta.owner");

            DatabaseManager.getTypeAtPath(JSON_A, "meta");        // "object"
            DatabaseManager.getTypeAtPath(JSON_A, "meta.tags");    // "array"
            DatabaseManager.getTypeAtPath(JSON_A, "records");      // "array"

            DatabaseManager.containsJSONPath(JSON_A, "meta.flags.audit.enabled"); // true
            DatabaseManager.containsJSONPath(JSON_A, "meta.missing");             // false
            DatabaseManager.listJSONKeys(JSON_A, "meta");

// --- Array ops: append / insert / replace / count / find / clear ---
            DatabaseManager.appendJSONArray(JSON_A, "records", new org.json.JSONObject().put("id", 1).put("name", "one"));
            DatabaseManager.appendJSONArray(JSON_A, "records", new org.json.JSONObject().put("id", 2).put("name", "two"));
            DatabaseManager.appendJSONArray(JSON_A, "records", new org.json.JSONObject().put("id", 3).put("name", "three"));
            DatabaseManager.countJSONArray(JSON_A, "records");                           // expect 3

            DatabaseManager.insertJSONArray(JSON_A, "records", 1, new org.json.JSONObject().put("id", 99).put("name", "ninety-nine"));
            DatabaseManager.replaceJSONArray(JSON_A, "records", 0, new org.json.JSONObject().put("id", 11).put("name", "eleven"));
            DatabaseManager.readJSONPath(JSON_A, "records[1].name");                     // expect "ninety-nine"
            DatabaseManager.findJSONArray(JSON_A, "records", "id", 99);                  // should find

            DatabaseManager.clearJSONArray(JSON_A, "meta.tags");
            DatabaseManager.countJSONArray(JSON_A, "meta.tags");                         // expect 0

// --- Moves / copies / renames / removes / clears (objects) ---
            DatabaseManager.writeJSONPath(JSON_A, "scratch.bucket.alpha", "A", true);
            DatabaseManager.writeJSONPath(JSON_A, "scratch.bucket.beta",  "B", true);

            DatabaseManager.moveJSONPath(JSON_A, "scratch.bucket", "meta.moved");        // move subtree into existing parent
            DatabaseManager.containsJSONPath(JSON_A, "scratch.bucket");                   // expect false
            DatabaseManager.containsJSONPath(JSON_A, "meta.moved.alpha");                 // expect true

            DatabaseManager.copyJSONPath(JSON_A, "meta.moved.alpha", "meta.copyOfAlpha");
            DatabaseManager.readJSONPath(JSON_A, "meta.copyOfAlpha");                     // "A"

            DatabaseManager.renameJSONKey(JSON_A, "meta", "owner", "ownerName");
            DatabaseManager.listJSONKeys(JSON_A, "meta");

            DatabaseManager.removeJSONPath(JSON_A, "meta.moved.beta");                    // remove single key
            DatabaseManager.containsJSONPath(JSON_A, "meta.moved.beta");                  // expect false

            DatabaseManager.clearJSONObject(JSON_A, "meta.moved");                        // clear subtree to { }
            DatabaseManager.listJSONKeys(JSON_A, "meta.moved");                           // expect empty

// --- Sanitize + trees (compact & pretty) ---
            DatabaseManager.sanitizeJSON(JSON_A, true);
            DatabaseManager.buildJSONTree(JSON_A, true);    // compact form
            DatabaseManager.buildJSONTree(JSON_A, false);   // pretty form
            DatabaseManager.printJSONTree(JSON_A);          // one console print to verify

// --- Cross-file actions (copy/rename/delete/list) ---
            DatabaseManager.copyFile(JSON_A, SUB_A + "\\main_copy.json");
            DatabaseManager.fileExists(SUB_A + "\\main_copy.json");

            DatabaseManager.renameFile(JSON_C, SUB_B + "\\alt_renamed.json");
            DatabaseManager.listFiles(SUITE);
            DatabaseManager.listFolders(SUITE);

            DatabaseManager.deleteFile(TXT_COPY);
            DatabaseManager.fileExists(TXT_COPY);                                         // expect false

            DatabaseManager.deleteFolder(SUB_B);
            DatabaseManager.folderExists(SUB_B);                                          // expect false

// --- Utility that throws on miss (operate on raw JSONObject, not a file) ---
            org.json.JSONObject probe = new org.json.JSONObject()
                    .put("grid", new org.json.JSONArray()
                            .put(new org.json.JSONArray().put(1).put(2))
                            .put(new org.json.JSONArray().put(3).put(4)));
            DatabaseManager.pathExistsOrThrow(probe, "grid[1][0]");                        // expect 3

// --- Final verify helpers ---
            DatabaseManager.getExtension(SUB_A + "\\main_copy.json");
            DatabaseManager.getParentPath(SUB_A + "\\main_copy.json");
            DatabaseManager.getFileName(SUB_A + "\\main_copy.json");

// ================== SINGLE MATERIALIZATION FLUSH (do this once) ==================
            QueueManager.flushAll(true);

        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Database test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
