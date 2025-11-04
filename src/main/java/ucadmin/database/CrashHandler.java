package ucadmin.database;

import ucadmin.exceptions.DatabaseException;
import ucadmin.exceptions.QueueException;
import ucadmin.util.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class CrashHandler {

    public static final class Result {
        public final boolean recoveryTriggered;
        public final int journalsFound;
        public final int filesMaterialized;
        public final List<String> quarantined;

        Result(boolean trig, int jf, int fm, List<String> q) {
            this.recoveryTriggered = trig;
            this.journalsFound = jf;
            this.filesMaterialized = fm;
            this.quarantined = Collections.unmodifiableList(q);
        }

        @Override public String toString() {
            return "CrashRecovery{triggered=" + recoveryTriggered +
                    ", journalsFound=" + journalsFound +
                    ", filesMaterialized=" + filesMaterialized +
                    ", quarantined=" + quarantined.size() + "}";
        }
    }

    private static final List<Path> SCAN_ROOTS = List.of(Paths.get("database"));
    private static final int JOURNAL_CLEAR_TIMEOUT_MS = 10_000;
    private static final int JOURNAL_POLL_INTERVAL_MS = 50;

    private static boolean isNonEmptyPatch(Path p) {
        try {
            return Files.isRegularFile(p)
                    && p.toString().endsWith(".json.patch")
                    && Files.size(p) > 0L;
        } catch (IOException e) {
            Logger.log(Logger.TAG.WARN, "CrashHandler: unable to stat potential patch: " + p + " (" + e.getMessage() + ")");
            return false;
        }
    }

    /** Collect unique *.json bases for which a non-empty *.json.patch exists. */
    private static List<Path> findPatchedJsonBases() throws IOException {
        final Set<Path> bases = new LinkedHashSet<>();
        for (Path root : SCAN_ROOTS) {
            if (!Files.exists(root)) continue;
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isNonEmptyPatch(file)) {
                        String s = file.toString();
                        String base = s.substring(0, s.length() - ".patch".length());
                        bases.add(Paths.get(base).toAbsolutePath().normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return List.copyOf(bases);
    }

    /** Public entrypoint invoked by StartupManager. */
    public static Result checkAndRecover() throws DatabaseException, QueueException {
        long t0 = System.nanoTime();
        Logger.log(Logger.TAG.SYSTEM, "CrashHandler: startup check begin");

        final List<Path> bases;
        try {
            bases = findPatchedJsonBases();
        } catch (IOException io) {
            Logger.log(Logger.TAG.ERROR, "CrashHandler: scan failed: " + io.getMessage());
            throw new DatabaseException("CrashHandler scan failed", io);
        }

        if (bases.isEmpty()) {
            Logger.log(Logger.TAG.INFO, "CrashHandler: no non-empty journals found; clean previous shutdown.");
            return new Result(false, 0, 0, List.of());
        }

        Logger.log(Logger.TAG.WARN, "CrashHandler: UNEXPECTED previous exit detected — journals found: " + bases.size());

        final List<String> quarantined = new ArrayList<>();
        int materialized = 0;

        for (Path base : bases) {
            final String path = base.toString();
            final Path journal = Paths.get(path + ".patch");

            // 1) Integrity + sanitation BEFORE materializing (use IntegrityReport signal)
            try {
                Logger.log(Logger.TAG.DEBUG, "CrashHandler: integrity pass → " + path);
                DatabaseManager.IntegrityReport report =
                        DatabaseManager.ensureJSONIntegrity(path, /*enforceObject=*/true, /*autoRepair=*/true);

                if (report == null || !report.valid) {
                    // Integrity report says "not valid" → quarantine and abort
                    String reason = "Integrity invalid"
                            + (report == null ? "" : " messages=" + report.messages);
                    quarantineWithDump(path, journal, reason);
                    Logger.log(Logger.TAG.ERROR, "CrashHandler: aborting startup — integrity failed for " + path);
                    throw new DatabaseException("Crash recovery aborted — integrity failed for " + path);
                }

                // Optional sanitation (project policy)
                DatabaseManager.sanitizeJSON(path, /*fixArrays=*/true);
            } catch (DatabaseException ex) {
                // Quarantine base and its journal; then abort startup (policy)
                quarantineWithDump(path, journal, ex.getClass().getSimpleName() + ": " +
                        (ex.getMessage() == null ? "<none>" : ex.getMessage()));
                Logger.log(Logger.TAG.ERROR, "CrashHandler: aborting startup — " + (quarantined.size() + 1) +
                        " file(s) quarantined. Admin action required.");
                quarantined.add(path);
                throw new DatabaseException("Crash recovery aborted — quarantined: " + quarantined.size(), ex);
            }

            // 2) Prime cache (ensures DBM path is live; loader will merge journal)
            try {
                QueueManager.readValue(path, /*rawLoader=*/null, /*accessor=*/j -> Boolean.TRUE);
            } catch (QueueException qe) {
                quarantineWithDump(path, journal, "Cache prime failed: " + qe.getMessage());
                throw new DatabaseException("Crash recovery aborted — cache prime failed for " + path, qe);
            }

            // 3) Materialize JUST THIS FILE (apply journal → base), then WAIT for journal to clear
            try {
                Logger.log(Logger.TAG.SYSTEM, "CrashHandler: materialize (single-file) → " + path);
                QueueManager.flushFileMaterialized(path);
            } catch (QueueException qx) {
                quarantineWithDump(path, journal, "Materialize enqueue failed: " + qx.getMessage());
                throw new DatabaseException("Crash recovery aborted — materialize failed for " + path, qx);
            }

            // Because the worker is async, poll the journal on disk until it’s empty/missing.
            if (!waitForJournalClear(journal, JOURNAL_CLEAR_TIMEOUT_MS, JOURNAL_POLL_INTERVAL_MS)) {
                quarantineWithDump(path, journal, "Journal not cleared after materialize timeout");
                throw new DatabaseException("CrashHandler: materialize failed to clear journal: " + journal);
            }

            // 4) Verify read via DBM
            try {
                DatabaseManager.readJSONPath(path, ""); // root touch; exception == bad
            } catch (DatabaseException verifyEx) {
                quarantineWithDump(path, journal, "Verify read failed after materialize: " + verifyEx.getMessage());
                throw new DatabaseException("Crash recovery aborted — verify read failed for " + path, verifyEx);
            }

            materialized++;
        }

        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        Logger.log(Logger.TAG.INFO, "CrashHandler: recovery complete in " + ms + " ms — journals="
                + bases.size() + ", materialized=" + materialized + ", quarantined=0");

        return new Result(true, bases.size(), materialized, List.of());
    }

    // ---------- helpers ----------

    private static boolean hasJournalBytes(Path p) {
        try { return Files.exists(p) && Files.size(p) > 0L; }
        catch (IOException e) { return false; }
    }

    /** Polls the journal until it is empty/missing, or the timeout elapses. */
    private static boolean waitForJournalClear(Path journal, int timeoutMs, int pollMs) {
        final long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        boolean last = hasJournalBytes(journal);
        while (System.nanoTime() < deadline) {
            boolean curr = hasJournalBytes(journal);
            if (curr != last) {
                Logger.log(Logger.TAG.DEBUG, "CrashHandler: journal state changed (" +
                        (last ? "non-empty→" : "empty→") + (curr ? "non-empty" : "empty") + "): " + journal);
                last = curr;
            }
            if (!curr) return true;
            try { Thread.sleep(pollMs); } catch (InterruptedException ignored) {}
        }
        return !hasJournalBytes(journal);
    }

    private static void quarantineWithDump(String basePath, Path journal, String reason) {
        try {
            DatabaseManager.moveToCorrupt(basePath);
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "CrashHandler: moveToCorrupt(base) failed for " + basePath + " — " + e.getMessage());
        }
        try {
            if (journal != null && Files.exists(journal)) {
                DatabaseManager.moveToCorrupt(journal.toString());
            }
        } catch (Exception e) {
            Logger.log(Logger.TAG.ERROR, "CrashHandler: moveToCorrupt(journal) failed for " + journal + " — " + e.getMessage());
        }

        Logger.logDump(
                "CRASH_RECOVERY_QUARANTINE\n" +
                        "path=" + basePath + "\n" +
                        "journal=" + (journal == null ? "<none>" : journal) + "\n" +
                        "reason=" + (reason == null ? "<none>" : reason)
        );
    }

    private CrashHandler() {}
}