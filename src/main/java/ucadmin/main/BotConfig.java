package ucadmin.main;

import ucadmin.util.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

public class BotConfig {
    public static final String CLIENT_ID = "870207081695838209";
    public static final String OWNER_ID = "516854610024071188";
    public static final String PREFIX = "_";

    public static final int netWorkerThreads = 2;

    private static final Path DATABASE_ROOT = Paths.get("database");

    public static final String LOGPATH = DATABASE_ROOT.resolve("LOGGER.txt").toString();
    public static final String CORRUPTPATH = DATABASE_ROOT.resolve("corrupt").toString();
    public static final String DUMPPATH = DATABASE_ROOT.resolve(Paths.get("corrupt", "DUMP.txt")).toString();
    public static final String NETWORKPATH = DATABASE_ROOT.resolve("network").toString();
    public static final EnumSet<Logger.TAG> LOG_IGNORE = EnumSet.of(
            Logger.TAG.NULL
    );

    /**
     * When false, the bot skips JDA/Discord startup and only boots core subsystems.
     */
    public static boolean DISCORD_ENABLED = false;

    /**
     * When true, logger output is printed to console only and never written to files.
     * Useful for local testing where persistent log files are not wanted.
     */
    public static volatile boolean CONSOLE_ONLY = true;

    // Used to enable running of testing code
    public static boolean TESTING = false;

}
