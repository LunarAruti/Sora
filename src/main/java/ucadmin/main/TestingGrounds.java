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
            final String path = "C:\\Users\\lunar\\Documents\\CS152l\\UC_Admin\\database\\test_recovery\\users\\3001.json";

// 0) Ensure file exists with your base content (only if missing)
            if (!DatabaseManager.fileExists(path)) {
                JSONObject base = new JSONObject()
                        .put("profile", new JSONObject()
                                .put("level", 1)
                                .put("name", "Old"))
                        .put("startup_count", 41)
                        .put("last_updated", 0);
                DatabaseManager.createJSON(path, base);
            }

// 1) Scalar updates (simple writes)
            DatabaseManager.writeJSONPath(path, "profile.name", "New", true);
            DatabaseManager.writeJSONPath(path, "startup_count", 42, true);
            DatabaseManager.writeJSONPath(path, "last_updated", System.currentTimeMillis(), true);

// 2) Add a nested object tree (creates any missing parents)
            DatabaseManager.writeJSONPath(path, "profile.meta.created_by", "fabricator", true);
            DatabaseManager.writeJSONPath(path, "profile.meta.created_at", System.currentTimeMillis(), true);
            DatabaseManager.writeJSONPath(path, "profile.meta.flags.active", true, true);
            DatabaseManager.writeJSONPath(path, "profile.meta.flags.trust", 3, true);

// (optional) snapshot
            System.out.println("=== TREE SNAPSHOT A (after meta/flags writes) ===");
            System.out.println(DatabaseManager.buildJSONTree(path, true));

// 3) Create arrays and append a bunch of values
            DatabaseManager.writeJSONPath(path, "tags", new JSONArray(), true);
            DatabaseManager.appendJSONArray(path, "tags", "alpha");
            DatabaseManager.appendJSONArray(path, "tags", "beta");
            DatabaseManager.appendJSONArray(path, "tags", "gamma");

            DatabaseManager.writeJSONPath(path, "activities", new JSONArray(), true);
            DatabaseManager.appendJSONArray(path, "activities", new JSONObject().put("id", 1001).put("type", "login"));
            DatabaseManager.appendJSONArray(path, "activities", new JSONObject().put("id", 1002).put("type", "update"));
            DatabaseManager.appendJSONArray(path, "activities", new JSONObject().put("id", 1003).put("type", "logout"));

// 4) Insert & replace within arrays
            DatabaseManager.insertJSONArray(path, "tags", 1, "inserted"); // tags: alpha, inserted, beta, gamma
            DatabaseManager.replaceJSONArray(path, "activities", 1, new JSONObject().put("id", 2002).put("type", "update+"));

// 5) Create a complex subtree and exercise rename/move/copy
            DatabaseManager.writeJSONPath(path, "settings", new JSONObject()
                    .put("ui", new JSONObject()
                            .put("theme", "dark")
                            .put("accent", "blue"))
                    .put("privacy", new JSONObject()
                            .put("share_usage", false)
                            .put("ads_personalization", false)), true);

// Rename a key inside "settings.ui": accent -> accentColor
            DatabaseManager.renameJSONKey(path, "settings.ui", "accent", "accentColor");

// Move a subtree: settings.privacy -> profile.privacy
            DatabaseManager.moveJSONPath(path, "settings.privacy", "profile.privacy");

// Copy a subtree: profile -> backups.profile_snapshot
            DatabaseManager.copyJSONPath(path, "profile", "backups.profile_snapshot");

// 6) Clear then re-populate arrays/objects to generate more deltas
            DatabaseManager.clearJSONArray(path, "tags");
            DatabaseManager.appendJSONArray(path, "tags", "omega");
            DatabaseManager.appendJSONArray(path, "tags", "delta");

            DatabaseManager.clearJSONObject(path, "settings.ui"); // clears theme & accentColor
            DatabaseManager.writeJSONPath(path, "settings.ui.theme", "light", true);
            DatabaseManager.writeJSONPath(path, "settings.ui.zoom", 1.25, true);

// 7) Deletes: remove a few paths (guarded to avoid strict failures)
            if (DatabaseManager.containsJSONPath(path, "backups.profile_snapshot.meta")) {
                DatabaseManager.removeJSONPath(path, "backups.profile_snapshot.meta");
            } else {
                System.out.println("skip remove: backups.profile_snapshot.meta (not present)");
            }
            if (DatabaseManager.containsJSONPath(path, "profile.meta.flags.trust")) {
                DatabaseManager.removeJSONPath(path, "profile.meta.flags.trust");
            } else {
                System.out.println("skip remove: profile.meta.flags.trust (not present)");
            }

// (optional) snapshot
            System.out.println("=== TREE SNAPSHOT B (post-conditional removes) ===");
            System.out.println(DatabaseManager.buildJSONTree(path, true));

// 8) More nested writes to ensure mixed-type ops are covered
            DatabaseManager.writeJSONPath(path, "profile.stats.login_count", 7, true);
            DatabaseManager.writeJSONPath(path, "profile.stats.last_ip", "127.0.0.1", true);

// 9) A small object clone and replace in array to exercise structure changes
            JSONObject act = new JSONObject().put("id", 3004).put("type", "heartbeat").put("ok", true);
            DatabaseManager.appendJSONArray(path, "activities", act);
            DatabaseManager.replaceJSONArray(path, "activities", 0,
                    new JSONObject().put("id", 1000).put("type", "boot"));

// 10) Optional: create another array with objects and insert middle
            DatabaseManager.writeJSONPath(path, "devices", new JSONArray(), true);
            DatabaseManager.appendJSONArray(path, "devices", new JSONObject().put("name", "laptop").put("trusted", true));
            DatabaseManager.appendJSONArray(path, "devices", new JSONObject().put("name", "phone").put("trusted", false));
            DatabaseManager.insertJSONArray(path, "devices", 1, new JSONObject().put("name", "tablet").put("trusted", true));

// 11) Sprinkle in some keys for later rename/copy/delete by recovery tests (no flush here)
            DatabaseManager.writeJSONPath(path, "tmp.scratch", new JSONObject().put("note", "transient"), true);
            DatabaseManager.copyJSONPath(path, "tmp.scratch", "backups.latest_tmp");
            DatabaseManager.renameJSONKey(path, "tmp", "scratch", "scratchpad");

        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "Database test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
