package ucadmin.main;

import ucadmin.database.DatabaseManager;
import ucadmin.database.QueueManager;
import ucadmin.exceptions.DatabaseException;
import ucadmin.exceptions.QueueException;
import ucadmin.util.Logger;

public class TestingGrounds {
    public static void TestingGrounds() {
        try {
            Logger.log(Logger.TAG.SYSTEM, "========== BEGIN DBM REPAIR/MATERIALIZE SMOKE ==========");

// --- Paths
            final String ROOT   = "database/test_a";
            final String USERS  = ROOT + "/users";
            final String DOCS   = ROOT + "/docs";
            final String U1     = USERS + "/2001.json";
            final String U2     = USERS + "/2002.json";
            final String U3     = USERS + "/2003.json";
            final String RAWTXT = DOCS  + "/note.txt";

// --- Clean slate
            if (DatabaseManager.folderExists(ROOT)) {
                DatabaseManager.deleteFolder(ROOT);
            }

// --- Create base folders/files
            DatabaseManager.createFolder(ROOT);
            DatabaseManager.createFolder(USERS);
            DatabaseManager.createFolder(DOCS);

            DatabaseManager.createFile(RAWTXT);
            DatabaseManager.createJSON(U1);   // cached {} (no disk yet)
            DatabaseManager.createJSON(U2);   // cached {}
            DatabaseManager.createJSON(U3);   // cached {}

// --- Queue-only writes to U1 (still not materialized yet)
            org.json.JSONObject rootU1 = new org.json.JSONObject()
                    .put("userId", 2001)
                    .put("profile", new org.json.JSONObject()
                            .put("name", "User_2001")
                            .put("verified", true)
                            .put("level", 3))
                    .put("tags", new org.json.JSONArray().put("alpha").put("beta"))
                    .put("junk", org.json.JSONObject.NULL);              // will be removed by sanitize

            DatabaseManager.writeJSONPath(U1, "", rootU1, true);         // replace root
            DatabaseManager.appendJSONArray(U1, "tags", "inserted");
            DatabaseManager.buildJSONTree(U1, false);
            DatabaseManager.printJSONTree(U1);

// --- Sanitation pass should MODIFY and then MATERIALIZE (A)
//     (removes nulls/empties per your sanitize rules)
            DatabaseManager.sanitizeJSON(U1, /*fixArrays=*/true);

// Verify can still read via queue (should be object-root, no "junk")
            DatabaseManager.readJSONPath(U1, "");
            DatabaseManager.listJSONKeys(U1, "");

// --- CORRUPTION CASE #1 (repairable): force ARRAY ROOT on disk for U2
//     We bypass queue intentionally to simulate external/base corruption.
            {
                java.nio.file.Path p = java.nio.file.Paths.get(U2).toAbsolutePath().normalize();
                java.nio.file.Files.createDirectories(p.getParent());
                // Write an array-root base
                java.nio.file.Files.writeString(p, "[]", java.nio.charset.StandardCharsets.UTF_8);
            }
// ensure with enforceObject=true should replace root → {} and MATERIALIZE
            DatabaseManager.ensureJSONIntegrity(U2, /*enforceObject=*/true, /*autoRepair=*/true);
// sanity reads
            DatabaseManager.readJSONPath(U2, "");
            DatabaseManager.listJSONKeys(U2, "");

// --- CORRUPTION CASE #2 (unrepairable): invalid JSON on disk for U3
            {
                java.nio.file.Path p = java.nio.file.Paths.get(U3).toAbsolutePath().normalize();
                java.nio.file.Files.createDirectories(p.getParent());
                // Write invalid JSON
                java.nio.file.Files.writeString(p, "{ \"bad\": ", java.nio.charset.StandardCharsets.UTF_8);
            }
// ensure should quarantine + re-init {} and MATERIALIZE
            DatabaseManager.ensureJSONIntegrity(U3, /*enforceObject=*/true, /*autoRepair=*/true);
// sanity reads
            DatabaseManager.readJSONPath(U3, "");
            DatabaseManager.listJSONKeys(U3, "");

// --- Final compaction/materialization sweep for anything still pending
            QueueManager.flushAll(/*materialize=*/true);

// --- Show final trees on disk
            DatabaseManager.printJSONTree(U1);
            DatabaseManager.printJSONTree(U2);
            DatabaseManager.printJSONTree(U3);

            Logger.log(Logger.TAG.SYSTEM, "========== DBM REPAIR/MATERIALIZE SMOKE COMPLETE ==========");

        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Database test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
