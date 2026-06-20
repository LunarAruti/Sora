package sora.config;

import sora.util.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

/**
 * Hardcoded bootstrap configuration for Sora.
 *
 * Purpose:
 * - Provide the minimum required configuration surface before runtime config
 *   loading is possible.
 * - Keep early startup safe even when the runtime config file is missing,
 *   malformed, or not yet readable.
 * - Centralize process-lifetime bootstrap values that are required before
 *   logger-backed diagnostics and full subsystem startup are available.
 *
 * Scope:
 * - Values in this class are intentionally hardcoded in v1 bootstrap flow.
 * - These values are not loaded from JSON.
 * - These values are not intended to live-update during runtime.
 *
 * Current responsibilities:
 * - Startup-mode booleans needed before runtime config load.
 * - Bootstrap identity constants that should not depend on runtime file I/O.
 * - Base database and bootstrap filesystem paths.
 * - Logger bootstrap paths and ignore defaults.
 *
 * Migration note:
 * - This class is the target location for values currently still mirrored in
 *   BotConfig during migration.
 * - BotConfig remains temporarily for compatibility until later passes remove it.
 */
public final class BootstrapConfig {

    /**
     * When false, the process skips Discord/JDA startup and only boots the
     * non-Discord runtime.
     *
     * Bootstrap-only:
     * - Read before normal runtime config is available.
     * - Should be treated as process-lifetime for a given run.
     */
    public static boolean DISCORD_ENABLED = false;

    /**
     * Enables entry into the testing flow after startup completes.
     *
     * Bootstrap-only:
     * - Used during early program branching in Main.
     * - Not intended as a live runtime toggle in v1 bootstrap design.
     */
    public static boolean TESTING = true;

    /**
     * When true, logger output is emitted to console only and no log files are
     * written.
     *
     * Bootstrap-only:
     * - Needed before logger initialization.
     * - Treated as a startup decision in the bootstrap layer.
     */
    public static volatile boolean CONSOLE_ONLY = true;

    /**
     * Discord application client id.
     *
     * Bootstrap-only:
     * - Treated as process identity rather than runtime-tunable behavior.
     * - Kept hardcoded so identity checks do not depend on runtime config load.
     */
    public static final String CLIENT_ID = "870207081695838209";

    /**
     * Discord owner id used for owner-gated commands.
     *
     * Bootstrap-only:
     * - This is an authority/identity value, not a runtime tuning value.
     * - Kept hardcoded so privileged command checks remain available even if
     *   runtime config is unavailable.
     */
    public static final String OWNER_ID = "516854610024071188";

    /**
     * Default command prefix.
     *
     * Bootstrap-only:
     * - This value is command identity/configuration, not a hot runtime tuning
     *   parameter in the current design.
     * - Kept centralized here so all remaining static command identity values
     *   live in one bootstrap location.
     */
    public static final String PREFIX = "_";

    /**
     * Base root for the local database directory tree.
     *
     * Bootstrap-only:
     * - Used to derive the process-lifetime filesystem layout for this run.
     * - Paths derived from this root should not be live-edited.
     */
    public static final Path DATABASE_ROOT = Paths.get("database");

    /**
     * Utility database folder path.
     *
     * This folder owns utility/runtime-support artifacts such as:
     * - logger files
     * - dump files
     * - corrupt/quarantine storage
     * - runtime config file
     */
    public static final String UTILITYPATH = DATABASE_ROOT.resolve("utility").toString();

    /**
     * Main logger file path.
     * Used before runtime config and logger-backed diagnostics are available.
     */
    public static final String LOGPATH = Path.of(UTILITYPATH).resolve("LOGGER.txt").toString();

    /**
     * Corrupt/quarantine directory path for damaged JSON or journal artifacts.
     */
    public static final String CORRUPTPATH = Path.of(UTILITYPATH).resolve("corrupt").toString();

    /**
     * Dump file path for forensic logger output.
     */
    public static final String DUMPPATH = Path.of(UTILITYPATH).resolve(Paths.get("corrupt", "DUMP.txt")).toString();

    /**
     * Network database/cache root path.
     */
    public static final String NETWORKPATH = DATABASE_ROOT.resolve("network").toString();

    /**
     * Runtime utility config file parent path.
     *
     * This folder holds the runtime-loaded `config.json` file under the utility subtree.
     * The directory location itself is bootstrap-only and should not live-update.
     */
    public static final String CONFIGPATH = UTILITYPATH;

    /**
     * Global database folder path.
     */
    public static final String GLOBALPATH = DATABASE_ROOT.resolve("global").toString();

    /**
     * User database folder path.
     */
    public static final String USERPATH = DATABASE_ROOT.resolve("user").toString();

    /**
     * Server database folder path.
     */
    public static final String SERVERPATH = DATABASE_ROOT.resolve("server").toString();

    /**
     * Registry database folder path.
     */
    public static final String REGISTRYPATH = DATABASE_ROOT.resolve("registry").toString();

    /**
     * Default logger ignore set used during bootstrap and initial logger setup.
     *
     * Runtime note:
     * - This bootstrap default exists so logger can start safely before runtime
     *   config is loaded.
     * - A later runtime config pass may override ignore behavior after startup.
     */
    public static final EnumSet<Logger.TAG> LOG_IGNORE = EnumSet.of(
            Logger.TAG.NULL
    );

    private BootstrapConfig() {}
}
