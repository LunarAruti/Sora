package ucadmin.main;

import ucadmin.database.DatabaseManager;
import ucadmin.database.QueueManager;
import ucadmin.util.Logger;

public class TestingGrounds {

    public static void TestingGrounds() {

        Logger.log(Logger.TAG.SYSTEM, "=== DBM CRASH TEST SUITE START ===");

        try {
            // ---------------------------------------------------------
            // 0) Init
            // ---------------------------------------------------------
            DatabaseManager.initialize();

            // ---------------------------------------------------------
            // 1) Paths
            // ---------------------------------------------------------
            final String ROOT = DatabaseManager.createFolder("sandbox_crash_test");
            final String PERM_FILE = ROOT + "/perm_crash.json";
            final String TEMP_FILE = ROOT + "/temp_crash.json";

            Logger.log(Logger.TAG.INFO, "ROOT=" + ROOT);
            Logger.log(Logger.TAG.INFO, "PERM_FILE=" + PERM_FILE);
            Logger.log(Logger.TAG.INFO, "TEMP_FILE=" + TEMP_FILE);

            // ---------------------------------------------------------
            // 2) PERM file writes (ensure journal is created)
            // ---------------------------------------------------------
            Logger.log(Logger.TAG.INFO, "PERM write $.value=1 -> " +
                    DatabaseManager.writeJSONPath(PERM_FILE, "$.value", 1, true));
            Logger.log(Logger.TAG.INFO, "PERM write $.value=2 -> " +
                    DatabaseManager.writeJSONPath(PERM_FILE, "$.value", 2, true));
            Logger.log(Logger.TAG.INFO, "PERM write $.value=3 -> " +
                    DatabaseManager.writeJSONPath(PERM_FILE, "$.value", 3, true));
            Logger.log(Logger.TAG.INFO, "PERM fileExists(base/journal)=" + DatabaseManager.fileExists(PERM_FILE));

            // ---------------------------------------------------------
            // 3) TEMP file writes (cache-only)
            // ---------------------------------------------------------
            Object tempRootBefore = DatabaseManager.readJSONPath(TEMP_FILE, "");
            Logger.log(Logger.TAG.INFO, "TEMP read root (prime cache) -> " + (tempRootBefore == null ? "null" : "ok"));
            DatabaseManager.makeTemporary(TEMP_FILE);
            Logger.log(Logger.TAG.INFO, "TEMP marked: " + TEMP_FILE);

            Logger.log(Logger.TAG.INFO, "TEMP write $.token=crash -> " +
                    DatabaseManager.writeJSONPath(TEMP_FILE, "$.token", "crash", true));
            Logger.log(Logger.TAG.INFO, "TEMP read $.token -> " +
                    DatabaseManager.readJSONPath(TEMP_FILE, "$.token"));
            Logger.log(Logger.TAG.INFO, "TEMP fileExists(base/journal)=" + DatabaseManager.fileExists(TEMP_FILE));

            Logger.log(Logger.TAG.INFO, "Queue sizes: flush=" + QueueManager.getQueueSize()
                    + ", cache=" + QueueManager.getCacheSize());

            // ---------------------------------------------------------
            // 4) Hold for manual termination
            // ---------------------------------------------------------
            Logger.log(Logger.TAG.SYSTEM, "=== CRASH TEST: terminate process after ~2 minutes ===");
            Logger.log(Logger.TAG.SYSTEM, "=== Do NOT call shutdown. Allow journals to exist. ===");

            Thread.sleep(10 * 60_000);

            Logger.log(Logger.TAG.WARN, "Crash test window ended without termination. Exiting normally.");

        } catch (Throwable t) {
            Logger.log(Logger.TAG.ERROR, "DBM TEST SUITE CRASHED: " + t);
        }

    }
}
