package ucadmin.main;

import ucadmin.util.Logger;
import java.util.EnumSet;

public class BotConfig {
    public static final String CLIENT_ID = "870207081695838209";
    public static final String OWNER_ID = "516854610024071188";
    public static final String PREFIX = "_";
    public static final String LOGPATH = "C:\\Users\\lunar\\Documents\\CS152l\\UC_Admin\\database\\LOGGER.txt";
    public static final String CORRUPTPATH = "C:\\Users\\lunar\\Documents\\CS152l\\UC_Admin\\database\\corrupt";
    public static final String DUMPPATH = "C:\\Users\\lunar\\Documents\\CS152l\\UC_Admin\\database\\corrupt\\DUMP.txt";
    public static final EnumSet<Logger.TAG> LOG_IGNORE = EnumSet.of(
            Logger.TAG.NULL
    );

    public static final boolean TESTING = false;

}
