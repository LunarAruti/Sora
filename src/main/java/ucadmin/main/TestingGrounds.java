package ucadmin.main;

import org.json.JSONObject;
import ucadmin.network.*;
import ucadmin.database.DatabaseManager;
import ucadmin.util.Logger;

import java.time.Duration;

public class TestingGrounds {

    public static void TestingGrounds() {

        Logger.log(Logger.TAG.SYSTEM, "=== TEST: Basic API Call + Promote TEMP START ===");

        try {
            // ---------------------------------------------------------
            // 1. Start network system
            // ---------------------------------------------------------
            NetworkManager.start();
            NetworkConfig.addWhitelistedHost("httpbin.org");

            // ---------------------------------------------------------
            // 2. Build request to a real public API
            // ---------------------------------------------------------
            NetworkRequest req = new NetworkRequest("httpbin", "GetJson")
                    .setRequestUrl("https://httpbin.org")
                    .setPath("/json")
                    .setType(NetworkRequest.Type.GET)
                    .setTimeout(Duration.ofSeconds(10))
                    .setPriority(NetworkRequest.Priority.NORMAL);

            req.seal();

            Logger.log(Logger.TAG.INFO, "Sealed request → finalUrl=" + req.getFinalUrl());
            Logger.log(Logger.TAG.INFO, "TEMP cache path = " + req.getCachePath());

            // ---------------------------------------------------------
            // 3. Submit request
            // ---------------------------------------------------------
            String tempPath = NetworkManager.requestAndReturnCachePath(req);

            if (tempPath == null) {
                Logger.log(Logger.TAG.ERROR,
                        "requestAndReturnCachePath() returned null → aborting test");
                return;
            }

            Logger.log(Logger.TAG.REQUEST,
                    "Request accepted, TEMP path=" + tempPath);

            // ---------------------------------------------------------
            // 4. Wait for worker to complete
            // ---------------------------------------------------------
            Thread.sleep(1200);

            // ---------------------------------------------------------
            // 5. PROMOTE the TEMP entry FIRST
            // ---------------------------------------------------------
            Logger.log(Logger.TAG.SYSTEM,
                    "Promoting TEMP entry: " + tempPath);

            try {
                boolean ok = DatabaseManager.promoteTemp(tempPath);
                if (!ok) {
                    Logger.log(Logger.TAG.ERROR,
                            "promoteTemp() returned false (entry may not exist?)");
                    return;
                }
                Logger.log(Logger.TAG.INFO,
                        "TEMP promotion successful: " + tempPath);
            } catch (Exception ex) {
                Logger.log(Logger.TAG.ERROR,
                        "promoteTemp() FAILED: " + ex.getMessage());
                return;
            }

            // ---------------------------------------------------------
            // 6. Now read the promoted entry (should show real JSON)
            // ---------------------------------------------------------
            Logger.log(Logger.TAG.SYSTEM,
                    "Reading promoted JSON from: " + tempPath);

            JSONObject resultJson;
            try {
                resultJson = DatabaseManager.readJSONRaw(tempPath);
                Logger.log(Logger.TAG.INFO,
                        "Promoted JSON read OK, keys=" + resultJson.length());
                Logger.log(Logger.TAG.INFO,
                        "Full JSON: " + resultJson.toString());
            } catch (Exception ex) {
                Logger.log(Logger.TAG.ERROR,
                        "Reading promoted JSON failed: " + ex.getMessage());
                return;
            }

            // ---------------------------------------------------------
            // 7. Shutdown
            // ---------------------------------------------------------
            NetworkManager.shutdown();
            Logger.log(Logger.TAG.SYSTEM,
                    "=== TEST COMPLETE (API call + promote) ===");

        } catch (Throwable t) {
            Logger.log(Logger.TAG.ERROR, "TestingGrounds crashed: " + t);
        }
    }
}
