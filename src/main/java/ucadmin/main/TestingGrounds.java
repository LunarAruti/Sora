package ucadmin.main;

import ucadmin.database.DatabaseManager;
import ucadmin.database.QueueManager;
import ucadmin.network.NetworkManager;
import ucadmin.network.NetworkRequest;
import ucadmin.util.Logger;
import ucadmin.util.ShutdownManager;

public class TestingGrounds {

    public static void TestingGrounds() {

        Logger.log(Logger.TAG.SYSTEM, "=== NETWORK TEST SUITE START (SIMPLE) ===");

        try {
            // ---------------------------------------------------------
            // 0) Init
            // ---------------------------------------------------------
            DatabaseManager.initialize();
            NetworkManager.start(3);

            final String userId = "547532739";
            final String cachePath = "database/network/" + userId + ".json";

            NetworkRequest req = new NetworkRequest("roblox", "UserFriendsFind")
                    .setRequestUrl("https://friends.roblox.com")
                    .setPath("/v1/users/547532739/friends/find")
                    .setResponseType(NetworkRequest.ResponseType.JSON_OBJECT)
                    .setCachePath(cachePath);

            String resolvedPath = NetworkManager.requestAndReturnCachePath(req);
            Logger.log(Logger.TAG.INFO, "[NetworkTest] cachePath=" + resolvedPath);

            // Wait for async workers to complete
            Thread.sleep(5000);

            // Mark as permanent after write
            try {
                boolean madePerm = DatabaseManager.makePermanent(resolvedPath);
                Logger.log(Logger.TAG.INFO, "[NetworkTest] makePermanent=" + madePerm + " path=" + resolvedPath);
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "[NetworkTest] makePermanent failed path=" + resolvedPath + " err=" + t.getMessage());
            }

            try {
                Object root = DatabaseManager.readJSONPath(resolvedPath, "");
                Logger.log(Logger.TAG.INFO, "[NetworkTest] read root ok path=" + resolvedPath + " type=" +
                        (root == null ? "null" : root.getClass().getSimpleName()));
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "[NetworkTest] read root failed path=" + resolvedPath + " err=" + t.getMessage());
            }

            Logger.log(Logger.TAG.INFO, "Queue sizes: flush=" + QueueManager.getQueueSize()
                    + ", cache=" + QueueManager.getCacheSize());
            Logger.log(Logger.TAG.SYSTEM, "=== NETWORK TEST SUITE END (SIMPLE) ===");

        } catch (Throwable t) {
            Logger.log(Logger.TAG.ERROR, "NETWORK TEST SUITE CRASHED: " + t);
        }

        ShutdownManager.shutdown(null);

    }
}
