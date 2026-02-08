package ucadmin.main;

import org.json.JSONObject;
import ucadmin.database.DatabaseManager;
import ucadmin.util.Logger;
import ucadmin.util.ShutdownManager;

public class TestingGrounds {

    public static void TestingGrounds() {

        Logger.log(Logger.TAG.SYSTEM, "=== DBM DELETE + COMPLEX JSON TEST ===");

        try {
            String suffix = String.valueOf(System.currentTimeMillis());
            String deleteFile = "database/user/delete_test_" + suffix + ".json";
            String complexFile = "database/user/complex_test_" + suffix + ".json";

            Logger.log(Logger.TAG.INFO, "delete file test: " + deleteFile);
            Logger.log(Logger.TAG.INFO, "complex file test: " + complexFile);

            // DELETE FILE TEST
            DatabaseManager.createJSON(deleteFile);
            Logger.log(Logger.TAG.INFO, "delete test: write seed ok=" +
                    DatabaseManager.writeJSONPath(deleteFile, "seed", 1, true));
            Logger.log(Logger.TAG.INFO, "delete test: exists after write=" +
                    DatabaseManager.fileExists(deleteFile));
            Logger.log(Logger.TAG.INFO, "delete test: delete ok=" +
                    DatabaseManager.deleteFile(deleteFile));
            Logger.log(Logger.TAG.INFO, "delete test: exists after delete=" +
                    DatabaseManager.fileExists(deleteFile));

            // COMPLEX JSON TEST
            DatabaseManager.createJSON(complexFile);
            Logger.log(Logger.TAG.INFO, "complex: write profile.id ok=" +
                    DatabaseManager.writeJSONPath(complexFile, "profile.id", suffix, true));
            Logger.log(Logger.TAG.INFO, "complex: write profile.name ok=" +
                    DatabaseManager.writeJSONPath(complexFile, "profile.name", "lunar", true));
            Logger.log(Logger.TAG.INFO, "complex: write profile.stats.level ok=" +
                    DatabaseManager.writeJSONPath(complexFile, "profile.stats.level", 42, true));
            Logger.log(Logger.TAG.INFO, "complex: write profile.stats.rank ok=" +
                    DatabaseManager.writeJSONPath(complexFile, "profile.stats.rank", "gold", true));
            Logger.log(Logger.TAG.INFO, "complex: write profile.flags.active ok=" +
                    DatabaseManager.writeJSONPath(complexFile, "profile.flags.active", true, true));

            Logger.log(Logger.TAG.INFO, "complex: append badge alpha ok=" +
                    DatabaseManager.appendJSONArray(complexFile, "profile.badges", "alpha"));
            Logger.log(Logger.TAG.INFO, "complex: append badge beta ok=" +
                    DatabaseManager.appendJSONArray(complexFile, "profile.badges", "beta"));
            Logger.log(Logger.TAG.INFO, "complex: append badge gamma ok=" +
                    DatabaseManager.appendJSONArray(complexFile, "profile.badges", "gamma"));

            JSONObject event1 = new JSONObject()
                    .put("type", "login")
                    .put("ts", 1)
                    .put("ip", "127.0.0.1");
            JSONObject event2 = new JSONObject()
                    .put("type", "kick")
                    .put("ts", 2)
                    .put("reason", "spam");

            Logger.log(Logger.TAG.INFO, "complex: append event1 ok=" +
                    DatabaseManager.appendJSONArray(complexFile, "history.events", event1));
            Logger.log(Logger.TAG.INFO, "complex: append event2 ok=" +
                    DatabaseManager.appendJSONArray(complexFile, "history.events", event2));
            Logger.log(Logger.TAG.INFO, "complex: append tag early ok=" +
                    DatabaseManager.appendJSONArray(complexFile, "history.tags", "early"));
            Logger.log(Logger.TAG.INFO, "complex: append tag beta ok=" +
                    DatabaseManager.appendJSONArray(complexFile, "history.tags", "beta"));

            Logger.log(Logger.TAG.INFO, "complex: update profile.stats.level ok=" +
                    DatabaseManager.writeJSONPath(complexFile, "profile.stats.level", 43, true));
            Logger.log(Logger.TAG.INFO, "complex: update event2.reason ok=" +
                    DatabaseManager.writeJSONPath(complexFile, "history.events[1].reason", "rule-1", true));

            Logger.log(Logger.TAG.INFO, "complex: read profile.name -> " +
                    DatabaseManager.readJSONPath(complexFile, "profile.name"));
            Logger.log(Logger.TAG.INFO, "complex: read profile.stats.level -> " +
                    DatabaseManager.readJSONPath(complexFile, "profile.stats.level"));
            Logger.log(Logger.TAG.INFO, "complex: read badges[2] -> " +
                    DatabaseManager.readJSONPath(complexFile, "profile.badges[2]"));
            Logger.log(Logger.TAG.INFO, "complex: read event2.reason -> " +
                    DatabaseManager.readJSONPath(complexFile, "history.events[1].reason"));

            Logger.log(Logger.TAG.INFO, "complex: remove profile.flags.active ok=" +
                    DatabaseManager.removeJSONPath(complexFile, "profile.flags.active"));
            Logger.log(Logger.TAG.INFO, "complex: contains profile.flags.active=" +
                    DatabaseManager.containsJSONPath(complexFile, "profile.flags.active"));

            System.err.println("test error");

        } catch (Throwable t) {
            Logger.log(Logger.TAG.ERROR, "DBM TEST CRASH: " + t);
        }

        try {
            Thread.sleep(1*60000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        ShutdownManager.shutdown(null);

    }
}
