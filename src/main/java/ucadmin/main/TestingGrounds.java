package ucadmin.main;

import ucadmin.database.DatabaseManager;
import ucadmin.database.QueueManager;
import ucadmin.network.NetworkManager;
import ucadmin.network.NetworkRequest;
import ucadmin.scheduler.ExeWhitelist;
import ucadmin.scheduler.TaskRequest;
import ucadmin.scheduler.TaskScheduler;
import ucadmin.util.Logger;
import ucadmin.util.ShutdownManager;

import static ucadmin.scheduler.TaskScheduler.scheduleRequest;


public class TestingGrounds {

    public static void TestingGrounds() {

        Logger.log(Logger.TAG.SYSTEM, "=== TASK SCHEDULER SMOKE SUITE ===");

        try {
            long base = System.currentTimeMillis();
            long t1 = base + 60_000L;
            long t2 = base + 90_000L;
            long t3 = base + 120_000L;
            long t4 = base + 150_000L;
            long t5 = base + 180_000L;
            long t6 = base + 210_000L;
            long t7 = base + 600_000L;

            TaskRequest req1 = new TaskRequest();
            req1.setName("log-high-priority");
            req1.setPriority(1);
            req1.setAbsoluteOnce(t1);
            req1.setOpKey(ExeWhitelist.OpKey.LOG_MESSAGE);
            req1.setOpArgs("info,\"priority 1 should run first\"");
            scheduleRequest(req1);

            TaskRequest req2 = new TaskRequest();
            req2.setName("log-default-priority");
            req2.setAbsoluteOnce(t1);
            req2.setOpKey(ExeWhitelist.OpKey.LOG_MESSAGE);
            req2.setOpArgs("info,default priority");
            scheduleRequest(req2);

            TaskRequest req3 = new TaskRequest();
            req3.setName("print-quoted");
            req3.setAbsoluteOnce(t2);
            req3.setOpKey(ExeWhitelist.OpKey.PRINT_MESSAGE);
            req3.setOpArgs("\"hello, world\"");
            scheduleRequest(req3);

            TaskRequest req4 = new TaskRequest();
            req4.setName("print-empty");
            req4.setAbsoluteOnce(t3);
            req4.setOpKey(ExeWhitelist.OpKey.PRINT_MESSAGE);
            req4.setOpArgs("");
            scheduleRequest(req4);

            TaskRequest req5 = new TaskRequest();
            req5.setName("log-system");
            req5.setAbsoluteOnce(t4);
            req5.setOpKey(ExeWhitelist.OpKey.LOG_MESSAGE);
            req5.setOpArgs("system,\"system message test\"");
            scheduleRequest(req5);

            TaskRequest req6 = new TaskRequest();
            req6.setName("log-warn");
            req6.setAbsoluteOnce(t5);
            req6.setOpKey(ExeWhitelist.OpKey.LOG_MESSAGE);
            req6.setOpArgs("warn,\"warn with comma, inside\"");
            scheduleRequest(req6);

            TaskRequest req7 = new TaskRequest();
            req7.setName("log-error");
            req7.setAbsoluteOnce(t6);
            req7.setOpKey(ExeWhitelist.OpKey.LOG_MESSAGE);
            req7.setOpArgs("error,\"error line\"");
            scheduleRequest(req7);

            TaskRequest req8 = new TaskRequest();
            req8.setName("log-debug-late");
            req8.setAbsoluteOnce(t7);
            req8.setOpKey(ExeWhitelist.OpKey.LOG_MESSAGE);
            req8.setOpArgs("debug,\"late debug in 10 minutes\"");
            scheduleRequest(req8);

        } catch (Throwable t) {
            Logger.log(Logger.TAG.ERROR, "TASK SCHEDULER TEST CRASH: " + t);
        }

        try {
            Thread.sleep(15 * 60 * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        ShutdownManager.shutdown(null);

    }
}
