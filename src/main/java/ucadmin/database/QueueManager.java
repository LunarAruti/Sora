package ucadmin.database;

import org.json.JSONArray;
import org.json.JSONObject;
import ucadmin.exceptions.QueueException;
import ucadmin.util.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.Callable;
import java.nio.file.Paths;

/**
 * QueueManager — Final (Batched Writes, Cache-first Reads)
 * -------------------------------------------------------
 * - INPUT = BATCHES (list of write ops) per file.
 * - Reads go through here to hit/establish cache; return immediately.
 * - Writes: apply to cache immediately & append ops to pending list; disk flush later.
 * - Flush: read disk JSON once, apply pending ops in order, write once.
 * - Single worker thread; FIFO across files.
 * - TTL flush, job-count flush, 500MB cache cap (flush oldest dirty first).
 * - Retry up to 5 times; on failure move/mark as corrupt via RawIO.
 * - Clean shutdown: flushAll() and stop worker.
 */
public final class QueueManager {
    private QueueManager() {}

    /* ===================== Config ===================== */

    /**
     * Runtime configuration values for queue behavior.
     *
     * All are public + volatile so you can tune them live.
     * Defaults favor reliability, immediate FORCED flushes, and safe disk writes.
     */
    public static final class Config {
        /** Inactive + dirty for this long ⇒ maintenance schedules a NON-FORCED flush. */
        public static volatile long CACHE_TTL_MILLIS = 2 * 60_000; // 5 minutes

        /**  Long idle → compact/materialize (write base + truncate journal) */
        public static int MATERIALIZE_INACTIVE_SECS = 600; // ~10 minutes default

        /** Soft cap on number of cached files (0 = unlimited; use memory cap instead). */
        public static volatile int CACHE_MAX_FILES = 0;

        /** Memory ceiling for cache (oldest dirty flushed first; else evict oldest clean). */
        public static volatile long CACHE_MEMORY_CAP_BYTES = 500L * 1024 * 1024; // 500 MB

        /** If a file accumulates ≥ N ops, maintenance schedules a NON-FORCED flush. */
        public static volatile int BATCH_JOB_FLUSH_THRESHOLD = 100;

        /**
         * Worker idle poll interval for REGULAR queue.
         * (FORCED jobs do not wait on this; they wake the worker via unpark and run immediately.)
         */
        public static volatile long WORKER_IDLE_POLL_MS = 5000;

        /** How many REGULAR jobs to process per tick before checking FORCED again. */
        public static volatile int WORKER_REGULAR_REQUEST_BURST = 4;

        /** Extra immediate retries within a single flushOne cycle (before yielding). */
        public static volatile int MAX_IMMEDIATE_RETRIES = 1;

        /** Total failure count before we quarantine the file to CORRUPTPATH. */
        public static volatile int MAX_TOTAL_RETRIES = 5;

        /* ---------- Transient failure backoff (e.g., file locked) ---------- */

        /** Base backoff for transient errors (applies per entry, exponential with jitter). */
        public static volatile long BACKOFF_BASE_MS = 150;

        /** Maximum backoff clamp for transient errors. */
        public static volatile long BACKOFF_MAX_MS = 2_000;

        /** Random jitter added/subtracted to backoff to avoid thundering herds. */
        public static volatile long BACKOFF_JITTER_MS = 50;

        /** Rotate the .patch journal if it grows beyond this size (bytes). 0 = disable. */
        public static volatile long JOURNAL_MAX_BYTES = 16L * 1024 * 1024; // 16 MiB default

        /** Optional hard cap on patch records before rotation (0 = ignore). */
        public static volatile long JOURNAL_MAX_RECORDS = 0L; // not counted by default

        /** Journal settings */
        public static final long MAX_JOURNAL_BYTES   = 512 * 1024; // 512 KiB
        public static final int  MAX_JOURNAL_RECORDS = 2000;       // lines in .patch

        /** How long the shutdown hook waits for the worker to exit (ms). */
        public static volatile long SHUTDOWN_AWAIT_MS = 15_000;

    }

    /* ===================== Raw I/O binding ===================== */

    /**
     * Handles the raw I/O layer used by QueueManager.
     *
     * These bindings are provided externally by DatabaseManager
     * or a bootstrap routine to abstract file operations. Logger
     * records all binding actions for traceability.
     */
    public static final class RawIO {
        // === QueueManager.RawIO ===

        public interface RawLoader {
            JSONObject load(String path) throws Exception;
        }

        public interface RawWriter {
            void write(String path, JSONObject json) throws Exception;
        }

        public interface MoveToCorrupt {
            void move(String path) throws Exception;
        }

        public interface RawPatchAppender {
            void appendPatch(String path, Map<String,Object> diff) throws Exception;
        }

        /** New: Full materialization (write snapshot to base; optional verify; optional delete journal). */
        public interface Materializer {
            void materialize(String path, JSONObject snapshot, boolean verify, boolean deleteJournal) throws Exception;
        }

        private static volatile RawPatchAppender PATCH_APPENDER;
        private static volatile RawLoader LOADER;
        private static volatile RawWriter WRITER;
        private static volatile MoveToCorrupt MOVER;
        /** New: materializer delegate */
        private static volatile Materializer MATERIALIZER;

        /** Binds a loader function for reading JSON files. */
        public static void bindLoader(RawLoader l) {
            if (l == null) {
                Logger.log(Logger.TAG.ERROR, "QueueManager.RawIO: bindLoader received null.");
                throw new IllegalArgumentException("RawIO.bindLoader: loader cannot be null");
            }
            if (LOADER != null) {
                Logger.log(Logger.TAG.WARN, "QueueManager.RawIO: loader already bound; rebinding.");
            }
            LOADER = l;
            Logger.log(Logger.TAG.SYSTEM, "QueueManager.RawIO: bound loader.");
        }

        /** Binds a writer function for writing JSON files. */
        public static void bindWriter(RawWriter w) {
            if (w == null) {
                Logger.log(Logger.TAG.ERROR, "QueueManager.RawIO: bindWriter received null.");
                throw new IllegalArgumentException("RawIO.bindWriter: writer cannot be null");
            }
            if (WRITER != null) {
                Logger.log(Logger.TAG.WARN, "QueueManager.RawIO: writer already bound; rebinding.");
            }
            WRITER = w;
            Logger.log(Logger.TAG.SYSTEM, "QueueManager.RawIO: bound writer.");
        }

        /** Binds a mover function for relocating corrupt files. */
        public static void bindMover(MoveToCorrupt m) {
            if (m == null) {
                Logger.log(Logger.TAG.ERROR, "QueueManager.RawIO: bindMover received null.");
                throw new IllegalArgumentException("RawIO.bindMover: mover cannot be null");
            }
            if (MOVER != null) {
                Logger.log(Logger.TAG.WARN, "QueueManager.RawIO: mover already bound; rebinding.");
            }
            MOVER = m;
            Logger.log(Logger.TAG.SYSTEM, "QueueManager.RawIO: bound mover.");
        }

        /** Binds a patch appender for delta persistence. */
        public static void bindPatchAppender(RawPatchAppender a) {
            if (a == null) {
                Logger.log(Logger.TAG.ERROR, "QueueManager.RawIO: bindPatchAppender received null.");
                throw new IllegalArgumentException("RawIO.bindPatchAppender: appender cannot be null");
            }
            if (PATCH_APPENDER != null) {
                Logger.log(Logger.TAG.WARN, "QueueManager.RawIO: patch appender already bound; rebinding.");
            }
            PATCH_APPENDER = a;
            Logger.log(Logger.TAG.SYSTEM, "QueueManager.RawIO: bound patch appender.");
        }

        /** New: bind materializer */
        public static void bindMaterializer(Materializer m) {
            if (m == null) {
                Logger.log(Logger.TAG.ERROR, "QueueManager.RawIO: bindMaterializer received null.");
                throw new IllegalArgumentException("RawIO.bindMaterializer: materializer cannot be null");
            }
            if (MATERIALIZER != null) {
                Logger.log(Logger.TAG.WARN, "QueueManager.RawIO: materializer already bound; rebinding.");
            }
            MATERIALIZER = m;
            Logger.log(Logger.TAG.SYSTEM, "QueueManager.RawIO: bound materializer.");
        }

        /** New: delegate (QueueException wrapper) */
        static void materialize(String path, JSONObject snapshot, boolean verify, boolean deleteJournal) throws QueueException {
            if (MATERIALIZER == null) {
                Logger.log(Logger.TAG.ERROR, "RawIO.materialize called before materializer bound.");
                throw new QueueException("QueueManager.RawIO.MATERIALIZER not bound");
            }
            Logger.log(Logger.TAG.INFO, "RawIO.materialize(begin): " + path + " (verify=" + verify + ", dropJournal=" + deleteJournal + ")");
            try {
                MATERIALIZER.materialize(path, snapshot, verify, deleteJournal);
                Logger.log(Logger.TAG.INFO, "RawIO.materialize(done): " + path);
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "RawIO.materialize(failed): " + path + " — " +
                        t.getClass().getSimpleName() + ": " + t.getMessage());
                throw new QueueException("materialize failed for " + path + ": " + t.getMessage(), t);
            }
        }

        // --------- JSON validation utilities (local, no external deps) ---------
        private static void validateJsonTree(Object node) throws QueueException {
            try {
                if (node == null || node == JSONObject.NULL) {
                    return;
                }

                if (node instanceof JSONObject obj) {
                    for (String k : obj.keySet()) {
                        validateJsonTree(obj.get(k));
                    }

                } else if (node instanceof JSONArray arr) {
                    for (int i = 0; i < arr.length(); i++) {
                        validateJsonTree(arr.get(i));
                    }

                } else if (node instanceof Number n) {
                    double d = n.doubleValue();
                    if (Double.isNaN(d) || Double.isInfinite(d)) {
                        Logger.log(
                                Logger.TAG.ERROR,
                                "RawIO.validateJsonTree: invalid numeric value=" + n
                        );
                        throw new QueueException("Invalid numeric value in JSON: " + n);
                    }

                } else if (
                        node instanceof String ||
                                node instanceof Boolean
                ) {
                    // primitives are always ok, no logging

                } else {
                    Logger.log(
                            Logger.TAG.ERROR,
                            "RawIO.validateJsonTree: unsupported type=" + node.getClass().getName()
                    );
                    throw new QueueException(
                            "Unsupported JSON type: " + node.getClass().getName()
                    );
                }

            } catch (QueueException q) {
                throw q;

            } catch (Throwable t) {
                Logger.log(
                        Logger.TAG.ERROR,
                        "RawIO.validateJsonTree: failure " +
                                t.getClass().getSimpleName() + ": " + t.getMessage()
                );
                throw new QueueException(
                        "JSON validation failure: " + t.getMessage(), t
                );
            }
        }

        /**
         * Cheap round-trip to catch weird serializer states (e.g., non-finite numbers).
         */
        private static void roundTripCheck(JSONObject obj, String contextPath) throws QueueException {
            Logger.log(Logger.TAG.DEBUG, "RawIO.roundTripCheck: begin ctx=" + contextPath + " size=" + (obj == null ? -1 : obj.length()));
            try {
                String txt = obj.toString(); // serialize
                int utf8Bytes = txt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                Logger.log(Logger.TAG.DEBUG, "RawIO.roundTripCheck: serialized bytes(utf8)=" + utf8Bytes);
                JSONObject reparsed = new JSONObject(txt); // parse
                Logger.log(Logger.TAG.DEBUG, "RawIO.roundTripCheck: reparsed size=" + reparsed.length());
                // quick structural probe (size equality is a coarse check but helps catch truncation)
                if (obj.length() != reparsed.length()) {
                    Logger.log(Logger.TAG.ERROR, "RawIO.roundTripCheck: size mismatch ctx=" + contextPath
                            + " orig=" + obj.length() + " reparsed=" + reparsed.length());
                    throw new QueueException("Round-trip size mismatch for " + contextPath
                            + " (" + obj.length() + " vs " + reparsed.length() + ")");
                }
                Logger.log(Logger.TAG.DEBUG, "RawIO.roundTripCheck: ok ctx=" + contextPath);
            } catch (QueueException q) {
                throw q;
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "RawIO.roundTripCheck: failed ctx=" + contextPath + " err=" + t.getMessage());
                throw new QueueException("Round-trip parse failed for " + contextPath + ": " + t.getMessage(), t);
            }
        }

        // ----------------------------- Public I/O ------------------------------

        /**
         * Loads JSON from disk; never returns malformed JSON. Throws on parse/validation error.
         */
        static JSONObject load(String path) throws QueueException {
            if (LOADER == null) {
                Logger.log(Logger.TAG.ERROR, "RawIO.load called before loader bound (path=" + path + ")");
                throw new QueueException("QueueManager.RawIO.LOADER not bound (path=" + path + ")");
            }
            Logger.log(Logger.TAG.INFO, "RawIO.load(begin): " + path);
            try {
                Logger.log(Logger.TAG.DEBUG, "RawIO.load: delegating to bound loader path=" + path);
                JSONObject obj = LOADER.load(path); // delegate to DBM
                Logger.log(Logger.TAG.DEBUG, "RawIO.load: delegate returned " + (obj == null ? "null" : ("JSONObject size=" + obj.length())));
                if (obj == null) {
                    Logger.log(Logger.TAG.INFO, "RawIO.load: null content for " + path + " (treating as empty object)");
                    Logger.log(Logger.TAG.INFO, "RawIO.load(done): " + path + " (size=0)");
                    return new JSONObject();
                }

                Logger.log(Logger.TAG.DEBUG, "RawIO.load: validateJsonTree(start) path=" + path);
                validateJsonTree(obj);
                Logger.log(Logger.TAG.DEBUG, "RawIO.load: validateJsonTree(ok) path=" + path);

                Logger.log(Logger.TAG.DEBUG, "RawIO.load: roundTripCheck(start) path=" + path);
                roundTripCheck(obj, path);
                Logger.log(Logger.TAG.DEBUG, "RawIO.load: roundTripCheck(ok) path=" + path);

                Logger.log(Logger.TAG.INFO, "RawIO.load(done): " + path + " (size=" + obj.length() + ")");
                return obj;
            } catch (QueueException q) {
                Logger.log(Logger.TAG.ERROR, "RawIO.load(failed): " + path + " — " + q.getMessage());
                throw q;
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "RawIO.load(failed): " + path + " — " + t.getClass().getSimpleName() + ": " + t.getMessage());
                throw new QueueException("RawIO.load failed for " + path + ": " + t.getMessage(), t);
            }
        }

        /**
         * Safely writes a JSON object to the given path.
         * - Validates the JSON tree in-memory (no NaN/Infinity/unsupported types).
         * - Delegates to bound WRITER.
         * - Reads back and validates the persisted file to guard against partial/truncated writes.
         * <p>
         * Atomic rename is handled at higher levels (e.g., repair flow). This method writes to `path` exactly.
         */
        static void write(String path, JSONObject json) throws QueueException {
            if (WRITER == null) {
                Logger.log(Logger.TAG.ERROR, "RawIO.write called before writer bound.");
                throw new QueueException("QueueManager.RawIO.WRITER not bound");
            }
            if (json == null) {
                Logger.log(Logger.TAG.ERROR, "RawIO.write received null JSON for " + path);
                throw new QueueException("RawIO.write: null JSON for " + path);
            }

            Logger.log(Logger.TAG.INFO, "RawIO.write(begin): " + path + " (len=" + json.length() + ")");
            try {
                Logger.log(Logger.TAG.DEBUG, "RawIO.write: validateJsonTree(start) path=" + path);
                validateJsonTree(json);
                Logger.log(Logger.TAG.DEBUG, "RawIO.write: validateJsonTree(ok) path=" + path);

                Logger.log(Logger.TAG.DEBUG, "RawIO.write: roundTripCheck(prewrite/start) path=" + path);
                roundTripCheck(json, path + " [prewrite]");
                Logger.log(Logger.TAG.DEBUG, "RawIO.write: roundTripCheck(prewrite/ok) path=" + path);

                // Delegate write
                Logger.log(Logger.TAG.DEBUG, "RawIO.write: delegating to bound writer path=" + path);
                WRITER.write(path, json);
                Logger.log(Logger.TAG.DEBUG, "RawIO.write: writer completed path=" + path);

                // Read-back verify to ensure on-disk is healthy
                if (LOADER == null) {
                    Logger.log(Logger.TAG.WARN, "RawIO.write: LOADER not bound; skipping read-back verify for " + path);
                } else {
                    Logger.log(Logger.TAG.DEBUG, "RawIO.write: read-back verify(start) path=" + path);
                    JSONObject verify = LOADER.load(path);
                    Logger.log(Logger.TAG.DEBUG, "RawIO.write: read-back verify got " + (verify == null ? "null" : ("JSONObject size=" + verify.length())));
                    if (verify == null) {
                        Logger.log(Logger.TAG.ERROR, "RawIO.write: read-back returned null for " + path);
                        throw new QueueException("Post-write read-back returned null for " + path);
                    }

                    Logger.log(Logger.TAG.DEBUG, "RawIO.write: validateJsonTree(postwrite/start) path=" + path);
                    validateJsonTree(verify);
                    Logger.log(Logger.TAG.DEBUG, "RawIO.write: validateJsonTree(postwrite/ok) path=" + path);

                    Logger.log(Logger.TAG.DEBUG, "RawIO.write: roundTripCheck(postwrite/start) path=" + path);
                    roundTripCheck(verify, path + " [postwrite]");
                    Logger.log(Logger.TAG.DEBUG, "RawIO.write: roundTripCheck(postwrite/ok) path=" + path);
                }

                // Log real byte size for sanity
                int utf8Bytes = json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                Logger.log(Logger.TAG.INFO, "RawIO.write(done): " + path + " (verified, bytes=" + utf8Bytes + ")");
            } catch (QueueException q) {
                Logger.log(Logger.TAG.ERROR, "RawIO.write(failed): " + path + " — " + q.getMessage());
                throw q;
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "RawIO.write(failed): " + path + " — " + t.getClass().getSimpleName() + ": " + t.getMessage());
                throw new QueueException("RawIO.write failed for " + path + ": " + t.getMessage(), t);
            }
        }

        /**
         * Moves a corrupted file to its designated quarantine location.
         */
        static void moveToCorrupt(String path) throws QueueException {
            if (MOVER == null) {
                Logger.log(Logger.TAG.ERROR, "RawIO.moveToCorrupt called before mover bound.");
                Logger.logDump("MOVE_TO_CORRUPT_PREBIND\npath=" + path + "\nerr=MOVER not bound");
                throw new QueueException("QueueManager.RawIO.MOVER not bound");
            }
            Logger.log(Logger.TAG.INFO, "RawIO.moveToCorrupt(begin): " + path);
            Logger.logDump("MOVE_TO_CORRUPT_BEGIN\npath=" + path);
            try {
                Logger.log(Logger.TAG.DEBUG, "RawIO.moveToCorrupt: delegating to mover path=" + path);
                MOVER.move(path);
                Logger.log(Logger.TAG.INFO, "RawIO.moveToCorrupt(done): " + path);
                Logger.logDump("MOVE_TO_CORRUPT_DONE\npath=" + path);
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "RawIO.moveToCorrupt(failed): " + path + " — " + t.getClass().getSimpleName() + ": " + t.getMessage());
                Logger.logDump(
                        "MOVE_TO_CORRUPT_FAILED\n"
                                + "path=" + path + "\n"
                                + "errClass=" + t.getClass().getName() + "\n"
                                + "errMsg=" + (t.getMessage() == null ? "<none>" : t.getMessage())
                );
                throw new QueueException("moveToCorrupt failed for " + path + ": " + t.getMessage(), t);
            }
        }

        /** Appends a JSON patch (dot-path → value / JSONObject.NULL) to the journal. */
        static void appendPatch(String path, Map<String, Object> diff) throws QueueException {
            if (PATCH_APPENDER == null) {
                Logger.log(Logger.TAG.ERROR, "RawIO.appendPatch called before patch appender bound.");
                throw new QueueException("QueueManager.RawIO.PATCH_APPENDER not bound");
            }

            // No diff → no–op, but also scrub any stray zero-byte journal.
            if (diff == null || diff.isEmpty()) {
                Logger.log(Logger.TAG.DEBUG, "appendPatch: empty diff → no-op for " + path);
                try { cleanupEmptyJournal(path); } catch (Throwable ignore) {}
                return;
            }

            Logger.log(Logger.TAG.INFO, "RawIO.appendPatch(begin): " + path + " (ops=" + diff.size() + ")");
            try {
                PATCH_APPENDER.appendPatch(path, diff);
                Logger.log(Logger.TAG.INFO, "RawIO.appendPatch(done): " + path + " (ops=" + diff.size() + ")");

                // ---------------- Journal guard & rollover ----------------
                final java.nio.file.Path journal = java.nio.file.Paths.get(path + ".patch")
                        .toAbsolutePath().normalize();

                long bytes = 0L;
                if (java.nio.file.Files.exists(journal)) {
                    try {
                        bytes = java.nio.file.Files.size(journal);
                    } catch (Exception szEx) {
                        Logger.log(Logger.TAG.WARN, "appendPatch: could not stat journal size: " + journal + " — " + szEx.getMessage());
                    }
                }

                boolean overBytes = (bytes > QueueManager.Config.MAX_JOURNAL_BYTES);
                boolean overLines = false;
                if (!overBytes && java.nio.file.Files.exists(journal)) {
                    // Count non-blank lines; stop early if we exceed the cap
                    try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(
                            journal, java.nio.charset.StandardCharsets.UTF_8)) {
                        int lines = 0;
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (!line.isBlank()) lines++;
                            if (lines > QueueManager.Config.MAX_JOURNAL_RECORDS) {
                                overLines = true;
                                break;
                            }
                        }
                    } catch (Exception lcEx) {
                        Logger.log(Logger.TAG.WARN, "appendPatch: line-count failed for " + journal + " — " + lcEx.getMessage());
                    }
                }

                if (overBytes || overLines) {
                    Logger.log(Logger.TAG.WARN, "Journal rollover: " + journal +
                            " (bytes=" + bytes + ", overBytes=" + overBytes + ", overLines=" + overLines + ") → materialize");
                    try {
                        // Compact just this file to a clean base and truncate its journal.
                        QueueManager.flushFileMaterialized(path);
                        Logger.log(Logger.TAG.INFO, "Journal rollover materialized: " + path);
                    } catch (Throwable t) {
                        // The patch is already durable; don't fail the write on compaction issues.
                        Logger.log(Logger.TAG.ERROR, "Journal rollover: materialize failed for " + path +
                                " — " + t.getClass().getSimpleName() + ": " + t.getMessage());
                        Logger.logDump(
                                "JOURNAL_ROLLOVER_FAIL\n"
                                        + "path=" + path + "\n"
                                        + "journal=" + journal + "\n"
                                        + "errClass=" + t.getClass().getName() + "\n"
                                        + "errMsg=" + (t.getMessage() == null ? "<none>" : t.getMessage())
                        );
                    }
                }
                // ----------------------------------------------------------

            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "RawIO.appendPatch(failed): " + path + " — " +
                        t.getClass().getSimpleName() + ": " + t.getMessage());
                throw new QueueException("appendPatch failed for " + path + ": " + t.getMessage(), t);
            }
        }

        static void truncateJournal(String path) throws QueueException {
            final java.nio.file.Path j = java.nio.file.Paths.get(path + ".patch").toAbsolutePath().normalize();
            Logger.log(Logger.TAG.INFO, "RawIO.truncateJournal(begin): " + j);
            try {
                java.nio.file.Files.deleteIfExists(j);
                Logger.log(Logger.TAG.INFO, "RawIO.truncateJournal(done): " + j);
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "RawIO.truncateJournal(failed): " + j + " — "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
                throw new QueueException("truncateJournal failed for " + j + ": " + t.getMessage(), t);
            }
        }
        static void cleanupEmptyJournal(String path) {
            try {
                java.nio.file.Path j = java.nio.file.Path.of(path + ".patch").toAbsolutePath().normalize();
                if (java.nio.file.Files.exists(j) && java.nio.file.Files.size(j) == 0L) {
                    java.nio.file.Files.delete(j);
                    Logger.log(Logger.TAG.INFO, "cleanupEmptyJournal: deleted empty journal " + j);
                }
            } catch (Exception ex) {
                Logger.log(Logger.TAG.DEBUG, "cleanupEmptyJournal: skip (" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
            }
        }

        static boolean journalHasContent(String path) {
            try {
                java.nio.file.Path j = java.nio.file.Paths.get(path + ".patch")
                        .toAbsolutePath().normalize();
                return java.nio.file.Files.exists(j) && java.nio.file.Files.size(j) > 0;
            } catch (Throwable ignore) {
                return false;
            }
        }
    }

    /* ===================== Data types ===================== */

    /** Represents a single write operation (e.g., set, remove, append). */
    public static final class WriteOp {
        public final String name;                 // for debug
        public final Consumer<JSONObject> apply;  // applies mutation to a JSONObject

        public WriteOp(String name, Consumer<JSONObject> apply) {
            this.name = name;
            this.apply = apply;
            Logger.log(Logger.TAG.DEBUG, "WriteOp created: " + name);
        }
    }

    /** A batch of write operations targeting one file. */
    public static final class Batch {
        private final List<WriteOp> ops = new ArrayList<>();

        public Batch add(WriteOp op) {
            ops.add(op);
            Logger.log(Logger.TAG.DEBUG, "Batch add: " + op.name);
            return this;
        }
        public Batch addAll(Collection<WriteOp> list) {
            ops.addAll(list);
            Logger.log(Logger.TAG.DEBUG, "Batch addAll: " + list.size() + " ops");
            return this;
        }
        public List<WriteOp> ops() { return ops; }
        public int size() { return ops.size(); }
        public boolean isEmpty() { return ops.isEmpty(); }
    }

    /** Options for per-file queue behavior. */
    public static final class QueueOptions {
        public boolean enforceObjectRoot = true;
        public QueueOptions() {}
        public QueueOptions(boolean enforceObjectRoot) { this.enforceObjectRoot = enforceObjectRoot; }
    }

    /** Normalize entry path declarations. */
    private static String normalizeFsPath(String p) {
        if (p == null) return null;
        String n = Paths.get(p).toAbsolutePath().normalize().toString();
        // Windows paths are case-insensitive; normalize case to avoid duplicate cache keys.
        if (File.separatorChar == '\\') {
            n = n.toLowerCase(Locale.ROOT);
        }
        return n;
    }

    /**
     * Represents a cached file with pending write operations.
     *
     * Each CacheEntry maintains its own lock to ensure safe concurrent
     * access and is logged when created for tracking.
     */
    private static final class CacheEntry {
        final ReentrantLock lock = new ReentrantLock(true);
        final Condition notFlushing = lock.newCondition();
        boolean enforceObjectRoot = true;

        JSONObject data;
        boolean dirty;
        boolean flushing;
        long lastAccessNanos;
        long approxBytes;
        int  failCount;
        Throwable lastError;

        // Backoff state for transient failures (e.g., locked file)
        long nextEligibleNanos; // 0 = eligible now

        final Deque<WriteOp> pendingOps = new ArrayDeque<>();

        // ==== TEMP MODE FIELDS ====
        // When true, this entry is treated as a temporary cache:
        // - never enqueued for patch/materialize by threshold logic
        // - will be expired by maintenance() if inactive (read and write)
        // - if it is ever flushed, deletion is handled centrally in flushOne()
        boolean isTemp = false;

        // Updated whenever a write operation mutates the cached JSON.
        // maintenance() will use both lastAccessNanos and lastWriteNanos to determine inactivity.
        long lastWriteNanos = 0L;

        CacheEntry() {
            Logger.log(Logger.TAG.DEBUG, "CacheEntry created.");
        }
    }

    /* ===================== State ===================== */

    // 1) Per-path materialize set + helper
    private static final java.util.Set<String> MATERIALIZE_REQUESTS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void requestMaterialize(String path) {
        if (path == null || path.isBlank()) return;
        final String npath = normalizeFsPath(path);

        MATERIALIZE_REQUESTS.add(npath);
        requestFlush(npath, /*force=*/true); // ensure it runs promptly
    }

    /**
     * Holds all active file cache entries and manages the global flush queue.
     *
     * QueueManager uses a dedicated worker thread that processes
     * flush requests; FORCED jobs have a priority lane.
     */
    private static final ConcurrentHashMap<String, CacheEntry> ENTRIES = new ConcurrentHashMap<>();

    // === Final materialization flag (used during shutdown compaction) ===
    private static final java.util.concurrent.atomic.AtomicBoolean FINAL_MATERIALIZE =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // Priority lane: forced jobs bypass delays; non-blocking is fine (worker drains fully each tick).
    private static final Deque<String> FORCED_QUEUE = new ConcurrentLinkedDeque<>();

    // Regular FIFO for normal flush requests.
    private static final BlockingQueue<String> FLUSH_QUEUE = new LinkedBlockingQueue<>();

    // Track enqueued paths to avoid duplicates.
    private static final Set<String> ENQUEUED = ConcurrentHashMap.newKeySet();

    // Worker lifecycle
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);
    private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicReference<Thread> WORKER_THREAD =
            new java.util.concurrent.atomic.AtomicReference<>();

    // Single writer executor
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UC-QueueWorker");
        t.setDaemon(true);
        return t;
    });

    // Wake helper: never interrupt I/O; unparks the worker for immediate service.
    private static void wakeWorker() {
        Thread t = WORKER_THREAD.get();
        if (t != null) {
            java.util.concurrent.locks.LockSupport.unpark(t);
            Logger.log(Logger.TAG.DEBUG, "QueueManager: Worker wake signal (unpark) sent.");
        }
    }

    // Track total cached memory approx
    private static final Object MEM_LOCK = new Object();
    private static long TOTAL_CACHE_BYTES = 0L;

    static {
        // Start the worker; capture the actual running thread inside workerLoop()
        WORKER.submit(QueueManager::workerLoop);

        // Shutdown hook is registered centrally in ShutdownManager.
    }

    /* ===================== Public API ===================== */

    /**
     * Reads a value from the cached JSON for the specified path.
     *
     * If no cached entry exists, it is loaded from disk via rawLoader.
     * This method blocks if a flush is in progress for the same entry.
     *
     * @param path file path to read from
     * @param rawLoader callable that loads the raw JSON if not cached
     * @param accessor function applied to the loaded JSON to extract a value
     * @return extracted value from the cached or freshly loaded JSON
     * @throws QueueException if interrupted or I/O fails
     */
    public static <T> T readValue(String path,
                                  Callable<JSONObject> rawLoader,
                                  Function<JSONObject, T> accessor) throws QueueException {
        final String npath = normalizeFsPath(path);
        Logger.log(Logger.TAG.DEBUG, "QueueManager.readValue() called for path: " + npath);

        if (accessor == null) {
            Logger.log(Logger.TAG.ERROR, "QueueManager.readValue(): accessor is null (path=" + npath + ")");
            throw new QueueException("readValue: accessor cannot be null");
        }

        // Default to RawIO.load if no loader was provided
        final Callable<JSONObject> loader = (rawLoader != null)
                ? rawLoader
                : () -> RawIO.load(npath);
        if (rawLoader == null) {
            Logger.log(Logger.TAG.DEBUG, "readValue: defaulting rawLoader -> RawIO.load(" + npath + ")");
        }

        final CacheEntry e = ENTRIES.computeIfAbsent(npath, p -> new CacheEntry());
        e.lock.lock();
        try {
            while (e.flushing) e.notFlushing.await();

            ensureCacheLoaded(e, npath, loader);
            e.lastAccessNanos = System.nanoTime();
            T result = accessor.apply(e.data);

            Logger.log(Logger.TAG.DEBUG, "QueueManager.readValue() completed successfully for " + npath);
            return result;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Logger.log(Logger.TAG.ERROR, "QueueManager.readValue() interrupted for " + npath);
            throw new QueueException("readValue interrupted for " + npath, ie);
        } catch (Exception ex) {
            Logger.log(Logger.TAG.ERROR, "QueueManager.readValue() failed for " + npath + ": " + ex.getMessage());
            throw new QueueException("readValue failed for " + npath, ex);
        } finally {
            e.lock.unlock();
        }
    }

    /**
     *
     * Reads a value from the cached JSON for the specified path,
     * with custom queue options controlling enforcement of object roots.
     *
     * @param path file path to read from
     * @param rawLoader callable that loads the raw JSON if not cached
     * @param accessor function applied to the loaded JSON to extract a value
     * @param opts additional queue options (root enforcement, etc.)
     * @return extracted value from the cached or freshly loaded JSON
     * @throws QueueException if interrupted or I/O fails
     */
    private static <T> T readValue(String path,
                                  Callable<JSONObject> rawLoader,
                                  Function<JSONObject, T> accessor,
                                  QueueOptions opts) throws QueueException {
        final String npath = normalizeFsPath(path);
        Logger.log(Logger.TAG.DEBUG, "QueueManager.readValue(opts) called for path: " + npath);

        if (accessor == null) {
            Logger.log(Logger.TAG.ERROR, "QueueManager.readValue(opts): accessor is null (path=" + npath + ")");
            throw new QueueException("readValue(opts): accessor cannot be null");
        }

        // Default to RawIO.load if no loader was provided
        final Callable<JSONObject> loader = (rawLoader != null)
                ? rawLoader
                : () -> RawIO.load(npath);
        if (rawLoader == null) {
            Logger.log(Logger.TAG.DEBUG, "readValue(opts): defaulting rawLoader -> RawIO.load(" + npath + ")");
        }

        final CacheEntry e = ENTRIES.computeIfAbsent(npath, p -> new CacheEntry());
        e.lock.lock();
        try {
            while (e.flushing) e.notFlushing.await();

            ensureCacheLoaded(e, npath, loader, opts);
            e.lastAccessNanos = System.nanoTime();
            T result = accessor.apply(e.data);

            Logger.log(Logger.TAG.DEBUG, "QueueManager.readValue(opts) completed successfully for " + npath);
            return result;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Logger.log(Logger.TAG.ERROR, "QueueManager.readValue(opts) interrupted for " + npath);
            throw new QueueException("readValue(opts) interrupted for " + npath, ie);
        } catch (Exception ex) {
            Logger.log(Logger.TAG.ERROR, "QueueManager.readValue(opts) failed for " + npath + ": " + ex.getMessage());
            throw new QueueException("readValue(opts) failed for " + npath, ex);
        } finally {
            e.lock.unlock();
        }
    }

    /**
     * Enqueues a batch of write operations for the specified file path
     * and returns a computed value from the post-mutation state.
     *
     * Behavior:
     *   - Applies all operations to cached JSON immediately.
     *   - Adds each operation to the file’s pending queue.
     *   - Schedules a flush if the batch size exceeds threshold.
     *   - Enforces memory caps as needed.
     *
     * @param path target file path for the batch
     * @param rawLoader callable to load raw JSON if not cached
     * @param batch the collection of write operations to perform
     * @param computeAfter callback returning a value after all operations
     * @return computed result based on the mutated in-memory JSON
     * @throws QueueException if interrupted or a batch operation fails
     */
    public static <T> T enqueueBatchAndGet(String path,
                                           Callable<JSONObject> rawLoader,
                                           Batch batch,
                                           Function<JSONObject, T> computeAfter) throws QueueException {
        final String npath = normalizeFsPath(path);
        Logger.log(Logger.TAG.DEBUG, "QueueManager.enqueueBatchAndGet() called for path: " + npath);

        if (rawLoader == null) {
            rawLoader = () -> RawIO.load(npath);
            Logger.log(Logger.TAG.DEBUG, "enqueueBatchAndGet: defaulting rawLoader -> RawIO.load(" + npath + ")");
        }
        if (computeAfter == null) {
            @SuppressWarnings("unchecked")
            Function<JSONObject, T> defaultCompute = (j) -> (T) Boolean.TRUE;
            computeAfter = defaultCompute;
            Logger.log(Logger.TAG.DEBUG, "enqueueBatchAndGet: defaulting computeAfter -> Boolean.TRUE");
        }

        if (batch == null || batch.isEmpty()) {
            Logger.log(Logger.TAG.DEBUG, "Empty batch detected; performing read-only compute for " + npath);
            return readValue(npath, rawLoader, computeAfter);
        }

        final CacheEntry e = ENTRIES.computeIfAbsent(npath, p -> new CacheEntry());

        e.lock.lock();
        try {
            while (e.flushing) e.notFlushing.await();

            ensureCacheLoaded(e, npath, rawLoader);

            for (WriteOp op : batch.ops()) {
                try {
                    op.apply.accept(e.data);
                    e.lastWriteNanos = System.nanoTime();
                    Logger.log(Logger.TAG.DEBUG, "Applied batch op '" + op.name + "' to " + npath);
                } catch (Throwable t) {
                    Logger.log(Logger.TAG.ERROR, "Batch operation '" + op.name + "' failed for " + npath + ": " + t.getMessage());
                    throw new QueueException("Batch op failed (" + op.name + ") for " + npath, t);
                }
                e.pendingOps.addLast(op);
            }

            if (!e.pendingOps.isEmpty()) {
                e.dirty = true;
            }

            e.lastAccessNanos = System.nanoTime();
            refreshSize(e);

            if (!e.isTemp && e.pendingOps.size() >= Config.BATCH_JOB_FLUSH_THRESHOLD) {
                Logger.log(Logger.TAG.INFO, "Flush threshold reached for " + npath + "; requesting flush.");
                requestFlush(npath, false);
            }

            enforceMemoryCap();

            T result = computeAfter.apply(e.data);
            Logger.log(Logger.TAG.DEBUG, "enqueueBatchAndGet() completed successfully for " + npath);
            return result;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Logger.log(Logger.TAG.ERROR, "QueueManager.enqueueBatchAndGet() interrupted for " + npath);
            throw new QueueException("enqueueBatchAndGet interrupted for " + npath, ie);
        } catch (Exception ex) {
            Logger.log(Logger.TAG.ERROR, "QueueManager.enqueueBatchAndGet() failed for " + npath + ": " + ex.getMessage());
            throw new QueueException("enqueueBatchAndGet failed for " + npath, ex);
        } finally {
            e.lock.unlock();
        }
    }

    /**
     * Non-blocking: schedules this file for flush (FIFO across files).
     *
     * Adds the path to the flush queue if it is not already pending.
     * Safe to call repeatedly; duplicates are ignored.
     *
     * @param path the path of the file to be flushed
     * @param force should the cache be forced to disk
     */
    public static void requestFlush(String path, boolean force) {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "requestFlush: path is null/blank");
            return;
        }
        final String npath = normalizeFsPath(path);

        if (ENQUEUED.add(npath)) {
            if (force) {
                FORCED_QUEUE.offerFirst(npath);
                Logger.log(Logger.TAG.INFO, "requestFlush(FORCED) queued: " + npath);
                wakeWorker(); // ensures immediate pickup
            } else {
                FLUSH_QUEUE.offer(npath);
                Logger.log(Logger.TAG.DEBUG, "requestFlush queued: " + npath);
            }
        } else {
            Logger.log(Logger.TAG.DEBUG, "requestFlush: already enqueued, skipping: " + npath);
        }
    }

    // Optional convenience (reads nicer at callsites)
    public static void requestFlushNow(String path) {
        requestFlush(path, /*force=*/true);
    }


    /**
     * Blocks until the specified file is fully flushed or an error occurs
     * after the maximum number of retries.
     *
     * @param path the path of the file to flush
     * @throws QueueException if flushing fails or the worker encounters repeated errors
     */
    public static void flushFile(String path, boolean materialize) throws QueueException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "flushFile: path is null/blank");
            throw new QueueException("flushFile: path is null/blank");
        }
        final String npath = normalizeFsPath(path);
        Logger.log(Logger.TAG.DEBUG, "flushFile requested: " + npath + " (materialize=" + materialize + ")");

        // Scope the FINAL_MATERIALIZE flag to just this call
        final boolean prev = FINAL_MATERIALIZE.getAndSet(materialize);
        try {
            // Force it so we never wait behind TTL/inactivity
            requestFlush(npath, /*force=*/true);

            final CacheEntry e = ENTRIES.computeIfAbsent(npath, p -> new CacheEntry());
            e.lock.lock();
            try {
                while (e.flushing || e.dirty) {
                    if (e.failCount >= Config.MAX_TOTAL_RETRIES && e.lastError != null) {
                        Logger.log(Logger.TAG.ERROR, "flushFile failed permanently: " + npath);
                        throw new QueueException("flushFile failing for " + npath, e.lastError);
                    }
                    e.notFlushing.await(1, java.util.concurrent.TimeUnit.SECONDS);
                }
                Logger.log(Logger.TAG.INFO, "flushFile completed: " + npath + " (materialize=" + materialize + ")");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                Logger.log(Logger.TAG.WARN, "flushFile interrupted: " + npath);
                throw new QueueException("flushFile interrupted for " + npath, ie);
            } finally {
                e.lock.unlock();
            }
        } finally {
            // restore global flag
            FINAL_MATERIALIZE.set(prev);
        }
    }

    public static void flushFileMaterialized(String path) throws QueueException {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "flushFileMaterialized: path is null/blank");
            throw new QueueException("flushFileMaterialized: path is null/blank");
        }
        final boolean prev = FINAL_MATERIALIZE.getAndSet(true);
        try {
            flushFile(path, true);  // existing method (no materialize param)
        } finally {
            FINAL_MATERIALIZE.set(prev);
        }
    }

    /**
     * Flushes all dirty files and blocks until all are clean or an error occurs.
     * If materialize=true, each file will compact its journal by writing the full base
     * and truncating <file>.patch on the successful flush.
     *
     * @param materialize whether to compact/merge journal into base during this flush
     * @throws QueueException if any file fails to flush after the maximum retries
     */
    public static void flushAll(boolean materialize) throws QueueException {
        final boolean prev = FINAL_MATERIALIZE.getAndSet(materialize);
        Logger.log(Logger.TAG.SYSTEM, "flushAll invoked (materialize=" + materialize + ")");

        // Snapshot keys (should already be normalized, but we normalize again defensively)
        final List<String> keysSnapshot = new ArrayList<>(ENTRIES.keySet());
        final List<String> queued = new ArrayList<>(keysSnapshot.size());

        try {
            // Phase 1: enqueue flushes (or delete TEMP) using normalized keys only
            for (String key : keysSnapshot) {
                final String npath = normalizeFsPath(key);
                final CacheEntry e = ENTRIES.get(npath);
                if (e == null) {
                    ENQUEUED.remove(npath);
                    MATERIALIZE_REQUESTS.remove(npath);
                    continue;
                }

                e.lock.lock();
                try {
                    if (e.isTemp) {
                        // TEMP entries are deleted by flushOne() for a single deletion path
                        requestFlush(npath, /*force=*/true);
                        Logger.log(Logger.TAG.INFO, "flushAll: queued TEMP entry for delete: " + npath);
                        continue;
                    }

                    final boolean shouldEnqueue = materialize || e.dirty;
                    if (shouldEnqueue) {
                        if (materialize) {
                            // per-path final/materialize signal survives past flushAll lifetime
                            MATERIALIZE_REQUESTS.add(npath);
                        }

                        requestFlush(npath, /*force=*/true);
                        queued.add(npath);

                        Logger.log(
                                Logger.TAG.DEBUG,
                                "flushAll queued " + (e.dirty ? "dirty" : "clean") + " file (FORCED): " + npath
                        );
                    }
                } finally {
                    e.lock.unlock();
                }
            }

            // Phase 2: wait for completion (normalized keys only; do NOT create entries here)
            for (String npath : queued) {
                final CacheEntry e = ENTRIES.get(npath);
                if (e == null) {
                    // If it disappeared, treat it as done (nothing left we can wait on).
                    ENQUEUED.remove(npath);
                    MATERIALIZE_REQUESTS.remove(npath);
                    continue;
                }

                e.lock.lock();
                try {
                    while (true) {
                        final boolean flushing = e.flushing;
                        final boolean dirty = e.dirty;

                        // Job may be queued but not yet flushing
                        final boolean enqueued = ENQUEUED.contains(npath);

                        boolean journalHasBytes = false;
                        if (materialize) {
                            try {
                                journalHasBytes = QueueManager.RawIO.journalHasContent(npath);
                            } catch (Throwable ignore) {
                                // best effort
                            }
                        }

                        final boolean shouldWait = materialize
                                ? (flushing || dirty || enqueued || journalHasBytes)
                                : (flushing || dirty || enqueued);

                        if (!shouldWait) break;

                        if (e.failCount >= Config.MAX_TOTAL_RETRIES && e.lastError != null) {
                            Logger.log(Logger.TAG.ERROR, "flushAll failed permanently at " + npath);
                            throw new QueueException("flushAll failing at " + npath, e.lastError);
                        }

                        e.notFlushing.await(1, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    Logger.log(Logger.TAG.WARN, "flushAll interrupted at " + npath);
                    throw new QueueException("flushAll interrupted", ie);
                } finally {
                    e.lock.unlock();
                }
            }

            Logger.log(Logger.TAG.INFO, "flushAll completed successfully (materialize=" + materialize + ").");
        } finally {
            FINAL_MATERIALIZE.set(prev);
        }
    }

    /**
     * Flushes pending writes, stops the queue worker, and waits for it to exit.
     *
     * @return true if shutdown was initiated, false if already shutting down
     */
    public static boolean shutdown() {
        return shutdown(true);
    }

    /**
     * Flushes pending writes (optional), stops the queue worker, and waits for it to exit.
     *
     * @param flushFirst if true, flushes all caches before stopping the worker
     * @return true if shutdown was initiated, false if already shutting down
     */
    public static boolean shutdown(boolean flushFirst) {
        if (!SHUTTING_DOWN.compareAndSet(false, true)) {
            Logger.log(Logger.TAG.WARN, "QueueManager: shutdown() ignored → already shutting down.");
            return false;
        }

        if (flushFirst) {
            try {
                Logger.log(Logger.TAG.SYSTEM, "QueueManager: Final flush before shutdown...");
                flushAll(true);
                Logger.log(Logger.TAG.SYSTEM, "QueueManager: All caches flushed successfully.");
            } catch (Exception e) {
                Logger.log(Logger.TAG.ERROR, "QueueManager: Flush failed during shutdown: " + e.getMessage());
            }
        }

        RUNNING.set(false);
        // Use unpark instead of interrupt to safely wake the worker out of any park/poll
        wakeWorker();
        WORKER.shutdown(); // graceful; we've already flushed
        try {
            if (!WORKER.awaitTermination(Config.SHUTDOWN_AWAIT_MS, TimeUnit.MILLISECONDS)) {
                Logger.log(Logger.TAG.WARN,
                        "QueueManager: worker did not terminate within " +
                                Config.SHUTDOWN_AWAIT_MS + "ms (continuing shutdown).");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Logger.log(Logger.TAG.WARN, "QueueManager: shutdown interrupted while awaiting worker stop.");
        }
        synchronized (MEM_LOCK) {
            Logger.log(Logger.TAG.DEBUG,
                    "QueueManager: Worker stopping. " +
                            "Cache entries=" + ENTRIES.size() +
                            ", approx memory=" + (TOTAL_CACHE_BYTES / 1024) + " KB.");
        }
        return true;
    }

    /** For monitoring. */
    public static int getQueueSize() { return FLUSH_QUEUE.size(); }
    public static int getCacheSize() { return ENTRIES.size(); }
    public static long getApproxCacheBytes() { synchronized (MEM_LOCK) { return TOTAL_CACHE_BYTES; } }
    /** Returns true if a cache entry exists for the given path (no load, no mutation). */
    public static boolean hasCacheEntry(String path) {
        if (path == null || path.isBlank()) return false;
        final String npath = normalizeFsPath(path);
        return ENTRIES.containsKey(npath);
    }

    /* ===================== Worker ===================== */

    /**
     * Background worker thread for processing queued flush requests.
     *
     * Performs FIFO flush operations and periodic maintenance
     * such as TTL-based eviction and memory cap enforcement.
     * This method runs continuously until RUNNING is false.
     */
    private static void workerLoop() {
        final Thread me = Thread.currentThread();
        WORKER_THREAD.set(me);
        Logger.log(Logger.TAG.SYSTEM, "QueueManager worker started (" + me.getName() + ").");

        final int REGULAR_BURST = Math.max(1, Config.WORKER_REGULAR_REQUEST_BURST);

        while (RUNNING.get()) {
            try {
                boolean didWork = false;
                String path;

                // ── 1) Drain all FORCED jobs first (priority lane, no starvation) ────────────────
                while ((path = FORCED_QUEUE.poll()) != null) {
                    final long t0 = System.nanoTime();
                    ENQUEUED.remove(path);
                    Logger.log(Logger.TAG.DEBUG, "Worker: picked FORCED job: " + path);
                    try {
                        Logger.log(Logger.TAG.INFO, "Flush(begin,FORCED): " + path);
                        flushOne(path); // will throw on final failure
                        Logger.log(Logger.TAG.INFO, "Flush(done,FORCED): " + path + " ("
                                + ((System.nanoTime() - t0) / 1_000_000) + " ms)");
                    } catch (Throwable t) {
                        Logger.log(Logger.TAG.ERROR, "Flush(failed,FORCED): " + path + " — "
                                + t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "<no message>" : t.getMessage()));
                        t.printStackTrace();
                    }
                    didWork = true;
                }

                // ── 2) Process a small burst of regular jobs this tick ───────────────────────────
                int processed = 0;
                while (processed < REGULAR_BURST &&
                        (path = FLUSH_QUEUE.poll(Config.WORKER_IDLE_POLL_MS, TimeUnit.MILLISECONDS)) != null) {
                    final long t0 = System.nanoTime();
                    ENQUEUED.remove(path);
                    Logger.log(Logger.TAG.DEBUG, "Worker: picked job: " + path);
                    try {
                        Logger.log(Logger.TAG.INFO, "Flush(begin): " + path);
                        flushOne(path); // will throw on final failure
                        Logger.log(Logger.TAG.INFO, "Flush(done): " + path + " ("
                                + ((System.nanoTime() - t0) / 1_000_000) + " ms)");
                    } catch (Throwable t) {
                        Logger.log(Logger.TAG.ERROR, "Flush(failed): " + path + " — "
                                + t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "<no message>" : t.getMessage()));
                        t.printStackTrace();
                    }
                    processed++;
                    didWork = true;

                    // If a FORCED job arrived mid-burst, service it immediately.
                    if (!FORCED_QUEUE.isEmpty()) break;
                }

                // ── 3) Only run maintenance if both queues are empty this tick ────────────────────
                if (!didWork && FORCED_QUEUE.isEmpty() && FLUSH_QUEUE.isEmpty()) {
                    maintenance(); // TTL scheduling + memory cap
                }

            } catch (InterruptedException ie) {
                // We don't rely on interrupts for wakeups; treat as spurious and continue unless shutting down.
                if (!RUNNING.get()) break;
                Logger.log(Logger.TAG.DEBUG, "Worker: spurious interrupt observed; continuing loop.");
            } catch (Throwable t) {
                Logger.log(Logger.TAG.ERROR, "Worker loop error: " + t.getClass().getSimpleName() + ": "
                        + (t.getMessage() == null ? "<no message>" : t.getMessage()));
                t.printStackTrace();
            }
        }

        Logger.log(Logger.TAG.SYSTEM, "QueueManager worker stopped.");
    }

    /**
     * Marks a cached FILE as TEMP.
     *
     * CACHE-ONLY:
     * - Operates only on an entry currently present in ENTRIES (exact match).
     * - Does NOT affect disk state.
     * - No directory behavior. No inheritance. No prefix rules.
     *
     * Returns true only if an existing cached entry changed state (perm -> temp).
     */
    public static boolean makeTemporary(String path) {
        if (path == null || path.isBlank()) return false;

        final String npath = normalizeFsPath(path);

        final CacheEntry e = ENTRIES.get(npath);
        if (e == null) {
            Logger.log(Logger.TAG.DEBUG, "makeTemporary: no cached entry for " + npath);
            return false;
        }

        boolean changed = false;
        e.lock.lock();
        try {
            if (!e.isTemp) {
                e.isTemp = true;
                changed = true;
            }
        } finally {
            e.lock.unlock();
        }

        Logger.log(Logger.TAG.INFO, "makeTemporary(" + npath + ") -> " + changed);
        return changed;
    }

    /**
     * Marks a cached FILE as PERMANENT.
     *
     * CACHE-ONLY:
     * - Operates only on an entry currently present in ENTRIES (exact match).
     * - Does NOT affect disk state.
     * - No directory behavior. No inheritance. No prefix rules.
     *
     * Returns true only if an existing cached entry changed state (temp -> perm).
     */
    public static boolean makePermanent(String path) {
        if (path == null || path.isBlank()) return false;

        final String npath = normalizeFsPath(path);

        final CacheEntry e = ENTRIES.get(npath);
        if (e == null) {
            Logger.log(Logger.TAG.DEBUG, "makePermanent: no cached entry for " + npath);
            return false;
        }

        boolean changed = false;
        e.lock.lock();
        try {
            if (e.isTemp) {
                e.isTemp = false;
                changed = true;
            }
        } finally {
            e.lock.unlock();
        }

        Logger.log(Logger.TAG.INFO, "makePermanent(" + npath + ") -> " + changed);
        return changed;
    }

    /**
     * Flushes pending operations for a single file.
     *
     * Reads the on-disk JSON, applies pending WriteOps in order,
     * writes back once, and updates cache metadata.
     *
     * @param path the path of the file to flush
     */
    private static void flushOne(String path) throws QueueException {
        final String npath = normalizeFsPath(path);
        final CacheEntry e = ENTRIES.get(npath);
        if (e == null) {
            // Nothing in cache; nothing to flush.
            ENQUEUED.remove(npath);
            MATERIALIZE_REQUESTS.remove(npath);
            return;
        }

        // --- (-1) EARLY TEMP GUARD: TEMP entries are never persisted -------------------
        e.lock.lock();
        try {
            if (e.isTemp) {
                long bytes = e.approxBytes;
                e.data = null;
                e.approxBytes = 0;
                e.pendingOps.clear();
                e.dirty = false;
                e.flushing = false;

                ENTRIES.remove(npath);
                ENQUEUED.remove(npath);
                MATERIALIZE_REQUESTS.remove(npath);

                synchronized (MEM_LOCK) {
                    TOTAL_CACHE_BYTES = Math.max(0, TOTAL_CACHE_BYTES - bytes);
                }
                Logger.log(Logger.TAG.INFO, "flushOne: TEMP entry encountered → deleted instead of persisting: " + npath);
                return;
            }
        } finally {
            e.lock.unlock();
        }

        // --- 0) Honor transient backoff window (e.g., locked file) -----------------
        final long now = System.nanoTime();
        e.lock.lock();
        try {
            if (e.nextEligibleNanos > 0 && now < e.nextEligibleNanos) {
                long ms = TimeUnit.NANOSECONDS.toMillis(e.nextEligibleNanos - now);
                Logger.log(Logger.TAG.DEBUG, "flushOne: backoff active (" + ms + " ms) → requeue: " + npath);
                ENQUEUED.remove(npath); // allow re-enqueue
                requestFlush(npath, /*force=*/false);
                return;
            }
        } finally {
            e.lock.unlock();
        }

        // --- 1) Snapshot under lock and flag flushing --------------------------------
        final List<WriteOp> ops;
        final JSONObject cachedSnapshot; // authoritative in-memory snapshot to commit
        final boolean finalPass = FINAL_MATERIALIZE.get() || MATERIALIZE_REQUESTS.remove(npath);
        e.lock.lock();
        try {
            // NEW: allow compaction when finalPass==true even if entry is clean,
            // as long as there is a non-empty journal on disk.
            boolean hasJournalBytes = QueueManager.RawIO.journalHasContent(npath);

            boolean shouldSkip =
                    e.flushing ||                                // never double-flush
                            (!finalPass &&                               // in normal (non-final) mode:
                                    (!e.dirty || e.pendingOps.isEmpty()));      // skip clean/empty

            if (shouldSkip) {
                Logger.log(
                        Logger.TAG.DEBUG,
                        "flushOne: skipped (clean/empty/already flushing): " + npath +
                                " [finalPass=" + finalPass +
                                ", dirty=" + e.dirty +
                                ", pendingOps=" + e.pendingOps.size() +
                                ", journalHasBytes=" + hasJournalBytes + "]"
                );
                return;
            }

            e.flushing = true;
            e.lastError = null;

            ops = new ArrayList<>(e.pendingOps);
            cachedSnapshot = (e.data != null) ? new JSONObject(e.data.toString()) : new JSONObject();

            Logger.log(
                    Logger.TAG.DEBUG,
                    "flushOne starting (delta mode): " + npath + " (" + ops.size() + " ops)" +
                            (finalPass ? " [final/materialize]" : "")
            );
        } finally {
            e.lock.unlock();
        }

        boolean success = false;
        Throwable lastErr = null;
        int attempts = 0;

        while (!success && attempts <= Config.MAX_IMMEDIATE_RETRIES) {
            attempts++;
            try {
                // --- 2) Load current on-disk version for comparison (may throw if corrupt) -----
                JSONObject diskJson = RawIO.load(npath);

                if (diskJson == null || diskJson.isEmpty()) {
                    if (finalPass) {
                        // Final pass → materialize full base and clear journal
                        RawIO.write(npath, cachedSnapshot);
                        RawIO.truncateJournal(npath);
                        Logger.log(Logger.TAG.INFO, "flushOne: materialized fresh base (final) for " + npath);
                    } else {
                        // Normal pass → append root-replacement patch (still delta)
                        Map<String, Object> rootPatch = new LinkedHashMap<>();
                        rootPatch.put("root", cachedSnapshot);
                        RawIO.appendPatch(npath, rootPatch);
                        Logger.log(Logger.TAG.INFO, "flushOne: no existing/empty file → appended root patch for " + npath);
                    }
                } else {
                    // Compute delta between disk and cache snapshot
                    Map<String, Object> diff = JSONDiffUtil.diff(diskJson, cachedSnapshot);

                    if (diff.isEmpty()) {
                        Logger.log(Logger.TAG.DEBUG, "flushOne: no delta changes for " + npath + " → skipping write");
                    } else {
                        RawIO.appendPatch(npath, diff);
                        Logger.log(Logger.TAG.INFO, "flushOne: delta patch appended for " + npath +
                                " (" + diff.size() + " changed keys)");
                    }

                    if (finalPass) {
                        boolean hasJournalNow = false;
                        try {
                            hasJournalNow = QueueManager.RawIO.journalHasContent(npath);
                        } catch (Throwable ignore) { /* best effort */ }

                        if (hasJournalNow) {
                            try {
                                RawIO.write(npath, cachedSnapshot);
                                RawIO.truncateJournal(npath);
                                Logger.log(Logger.TAG.INFO, "flushOne: materialized base & truncated journal (final) for " + npath);
                            } catch (Throwable t) {
                                Logger.log(Logger.TAG.ERROR, "flushOne(finalize): materialize failed for " + npath + " — "
                                        + t.getClass().getSimpleName() + ": " + t.getMessage());
                                Logger.logDump(
                                        "FINAL_MATERIALIZE_FAIL\n"
                                                + "path=" + npath + "\n"
                                                + "errClass=" + t.getClass().getName() + "\n"
                                                + "errMsg=" + (t.getMessage() == null ? "<none>" : t.getMessage())
                                );
                            }
                        } else {
                            Logger.log(Logger.TAG.DEBUG, "flushOne(final): no journal + no changes → skipping full write for " + npath);
                        }
                    }
                }

                success = true; // normal path succeeded

            } catch (Throwable loadOrWriteFailure) {
                // Anything thrown here means disk state is unusable (parse error, IO error, etc.)
                lastErr = loadOrWriteFailure;
                Logger.log(Logger.TAG.ERROR, "flushOne: base load/patch failed for " + npath + " — "
                        + loadOrWriteFailure.getClass().getSimpleName() + ": " + loadOrWriteFailure.getMessage()
                        + " | entering repair flow (attempt " + attempts + "/" + Config.MAX_IMMEDIATE_RETRIES + ")");

                Logger.logDump(
                        "FLUSH_FAIL base load/patch\n"
                                + "path=" + npath + "\n"
                                + "errClass=" + loadOrWriteFailure.getClass().getName() + "\n"
                                + "errMsg=" + (loadOrWriteFailure.getMessage() == null ? "<none>" : loadOrWriteFailure.getMessage())
                );

                try {
                    // --- Repair flow: quarantine old base, then full materialize from sanitized cache ----
                    try {
                        RawIO.moveToCorrupt(npath);
                        Logger.log(Logger.TAG.SYSTEM, "flushOne(repair): moved corrupt file → corrupt/: " + npath);
                        Logger.logDump(
                                "REPAIR_MOVE_TO_CORRUPT\n"
                                        + "path=" + npath + "\n"
                                        + "prevErrClass=" + (lastErr == null ? "<none>" : lastErr.getClass().getName()) + "\n"
                                        + "prevErrMsg=" + (lastErr == null ? "<none>" : String.valueOf(lastErr.getMessage()))
                        );
                    } catch (Throwable moverFail) {
                        lastErr = moverFail;
                        Logger.log(Logger.TAG.ERROR, "flushOne(repair): moveToCorrupt failed for " + npath + " — " + moverFail.getMessage());
                        Logger.logDump(
                                "REPAIR_MOVE_FAILED\n"
                                        + "path=" + npath + "\n"
                                        + "moverErrClass=" + moverFail.getClass().getName() + "\n"
                                        + "moverErrMsg=" + (moverFail.getMessage() == null ? "<none>" : moverFail.getMessage())
                        );
                        throw moverFail; // cannot safely proceed
                    }

                    // In-memory sanitize of the authoritative snapshot (no queue, no disk read)
                    DatabaseManager.SanitizeResult sr =
                            DatabaseManager.trySanitizeSnapshot(cachedSnapshot, /*fixArrays=*/true);
                    final JSONObject material = (sr != null && sr.snapshot != null) ? sr.snapshot : cachedSnapshot;

                    // Policy: once disk was corrupt/unusable, perform a FULL WRITE + truncate journal
                    RawIO.write(npath, material);
                    RawIO.truncateJournal(npath);
                    Logger.log(Logger.TAG.INFO, "flushOne(repair): materialized sanitized base & truncated journal for " + npath);

                    Logger.logDump(
                            "REPAIR_APPLIED\n"
                                    + "path=" + npath + "\n"
                                    + "sanitized=" + (sr != null && sr.modified) + "\n"
                                    + "opsApplied=" + ops.size()
                    );

                    success = true; // repair path succeeded

                } catch (Throwable repairFail) {
                    lastErr = repairFail;
                    Logger.log(Logger.TAG.ERROR, "flushOne(repair) failed for " + npath + " — "
                            + repairFail.getClass().getSimpleName() + ": " + repairFail.getMessage());

                    if (isTransientIO(repairFail)) {
                        long backoffMs = computeBackoffMs(e.failCount);
                        e.lock.lock();
                        try {
                            e.nextEligibleNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(backoffMs);
                            Logger.log(Logger.TAG.DEBUG, "flushOne: transient failure → backoff " + backoffMs + " ms for " + npath);
                        } finally { e.lock.unlock(); }
                        break; // stop immediate retries to avoid hammering
                    }
                    // else: allow next immediate retry (up to MAX_IMMEDIATE_RETRIES)
                }
            }
        }

        // --- 3) Finalize state under lock & signal waiters ----------------------------
        e.lock.lock();
        try {
            if (success) {
                e.pendingOps.clear();
                e.dirty = false;
                e.flushing = false;
                e.failCount = 0;
                e.lastError = null;
                e.nextEligibleNanos = 0L;
                Logger.log(Logger.TAG.INFO, "flushOne(done): " + npath);
            } else {
                e.flushing = false;
                e.dirty = true; // still dirty; will retry later via maintenance or explicit force
                e.failCount++;
                e.lastError = lastErr;

                if (e.failCount >= Config.MAX_TOTAL_RETRIES) {
                    try {
                        RawIO.moveToCorrupt(npath);
                        Logger.log(Logger.TAG.WARN, "flushOne: moved to corrupt after repeated failures: " + npath
                                + " (failCount=" + e.failCount + ")");
                    } catch (Throwable moverEx) {
                        Logger.log(Logger.TAG.ERROR, "flushOne: corrupt move failed for " + npath + " — " + moverEx.getMessage());
                    }

                    Logger.logDump(
                            "FLUSH_PERMANENT_FAILURE\n"
                                    + "path=" + npath + "\n"
                                    + "attempts=" + attempts + "\n"
                                    + "failCount=" + e.failCount + "\n"
                                    + "lastErrClass=" + (lastErr == null ? "<none>" : lastErr.getClass().getName()) + "\n"
                                    + "lastErrMsg=" + (lastErr == null ? "<none>" : String.valueOf(lastErr.getMessage()))
                    );

                    Logger.log(Logger.TAG.ERROR, "flushOne failed permanently for " + npath
                            + " (attempts=" + attempts + ", totalFailCount=" + e.failCount + "): " + lastErr);
                } else {
                    Logger.log(Logger.TAG.ERROR, "flushOne failed for " + npath
                            + " (attempts=" + attempts + "): " + lastErr);
                }
            }

            e.notFlushing.signalAll();
        } finally {
            e.lock.unlock();
        }

        if (!success) {
            throw new QueueException("flushOne failed for " + npath, lastErr);
        }
    }

    /* ---------- helpers for transient failures & backoff ---------- */

    private static boolean isTransientIO(Throwable t) {
        // Common transient types: file locked, channel closed by old interrupt, access denied
        if (t instanceof java.nio.file.AccessDeniedException) return true;
        if (t instanceof java.nio.channels.ClosedByInterruptException) return true;
        if (t.getCause() != null) return isTransientIO(t.getCause());
        return false;
    }

    private static long computeBackoffMs(int failCount) {
        long base = Config.BACKOFF_BASE_MS;
        long max  = Config.BACKOFF_MAX_MS;
        long jitter = Config.BACKOFF_JITTER_MS;

        long exp = base * (1L << Math.min(6, Math.max(0, failCount))); // cap at 2^6
        long withJitter = exp + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        if (withJitter < base) withJitter = base;
        if (withJitter > max)  withJitter = max;
        return withJitter;
    }

    /* ===================== Maintenance ===================== */

    /**
     * Periodically performs maintenance tasks for cached entries.
     *
     * Tasks include:
     *   - Scheduling flushes for stale or large batches.
     *   - Enforcing memory limits by evicting or flushing data.
     *
     * Called internally on worker cycles to keep cache healthy.
     */
    private static void maintenance() {
        final long now = System.nanoTime();
        Logger.log(Logger.TAG.DEBUG, "QueueManager.maintenance() cycle started.");

        // Collect actions out of locks to avoid re-entrancy surprises.
        final List<String> toPatchFlush = new ArrayList<>();
        final List<String> toMaterialize = new ArrayList<>();
        // TEMP-only: collect entries to delete (handled via flushOne)
        final List<String> toExpireTemp = new ArrayList<>();

        for (Map.Entry<String, CacheEntry> it : ENTRIES.entrySet()) {
            final String path = it.getKey();
            final CacheEntry e = it.getValue();

            e.lock.lock();
            try {
                // Respect transient backoff window for any writes
                if (e.nextEligibleNanos > 0 && now < e.nextEligibleNanos) {
                    long ms = TimeUnit.NANOSECONDS.toMillis(e.nextEligibleNanos - now);
                    Logger.log(Logger.TAG.DEBUG, "Maintenance: skip (backoff " + ms + " ms) for " + path);
                    continue;
                }

                final boolean isDirty = e.dirty && !e.pendingOps.isEmpty();
                final long ageMs = TimeUnit.NANOSECONDS.toMillis(now - e.lastAccessNanos);

                // ===== TEMP MODE: expiry & scheduling suppression =====
                if (e.isTemp) {
                    // Compute inactivity by BOTH read and write. If lastWriteNanos is 0 (never written), treat it as lastAccess.
                    long writeRef = (e.lastWriteNanos > 0L) ? e.lastWriteNanos : e.lastAccessNanos;
                    final long ageSecAccess = TimeUnit.NANOSECONDS.toSeconds(now - e.lastAccessNanos);
                    final long ageSecWrite  = TimeUnit.NANOSECONDS.toSeconds(now - writeRef);

                    // Delete TEMP if inactive for MATERIALIZE_INACTIVE_SECS on BOTH axes
                    if (ageSecAccess >= Math.max(1, Config.MATERIALIZE_INACTIVE_SECS)
                            && ageSecWrite >= Math.max(1, Config.MATERIALIZE_INACTIVE_SECS)) {
                        Logger.log(Logger.TAG.INFO, "TEMP expired (inactive) → delete: " + path
                                + " [ageAccessSec=" + ageSecAccess + ", ageWriteSec=" + ageSecWrite + "]");
                        toExpireTemp.add(path);
                    }

                    // Do NOT schedule TTL patch flush for TEMP
                    // Do NOT schedule long-idle materialize for TEMP
                    // Continue to next entry
                    continue;
                }

                // --- 1) Existing behavior: schedule a non-forced patch write after TTL ---
                if (isDirty && !e.flushing) {
                    if (ageMs >= Config.CACHE_TTL_MILLIS) {
                        Logger.log(Logger.TAG.DEBUG, "Scheduling TTL flush for " + path);
                        toPatchFlush.add(path); // non-forced background write (patch)
                    }
                    if (e.pendingOps.size() >= Config.BATCH_JOB_FLUSH_THRESHOLD) {
                        Logger.log(Logger.TAG.DEBUG, "Scheduling flush (batch threshold) for " + path);
                        toPatchFlush.add(path); // non-forced background write (patch)
                    }
                }

                // --- 2) Long-idle compaction/materialize after X seconds ---
                if (!e.flushing) {
                    boolean hasJournal = false;
                    try {
                        hasJournal = QueueManager.RawIO.journalHasContent(path);
                    } catch (Throwable ignore) { /* best effort */ }

                    final long ageSec = TimeUnit.NANOSECONDS.toSeconds(now - e.lastAccessNanos);
                    if (ageSec >= Math.max(1, Config.MATERIALIZE_INACTIVE_SECS)
                            && (isDirty || hasJournal)) {
                        Logger.log(Logger.TAG.DEBUG, "Scheduling MATERIALIZE (long idle) for " + path
                                + " [ageSec=" + ageSec + ", dirty=" + isDirty + ", journal=" + hasJournal + "]");
                        toMaterialize.add(path);
                    }
                }

            } finally {
                e.lock.unlock();
            }
        }

        // Perform actions outside of locks.
        for (String p : toPatchFlush) {
            requestFlush(p, /*force=*/false);       // patch-only; normal lane
        }
        for (String p : toMaterialize) {
            requestMaterialize(p);                  // forced; per-path final/materialize
        }

        // TEMP deletions outside locks and map-iteration (handled by flushOne)
        for (String p : toExpireTemp) {
            requestFlush(p, /*force=*/true);
        }

        // Enforce memory cap (flush oldest dirty first; if none, evict oldest clean)
        enforceMemoryCap();

        Logger.log(Logger.TAG.DEBUG, "QueueManager.maintenance() cycle complete.");
    }

    /**
     * Clears the TEMP flag on a cached entry so it behaves like a regular cache file.
     * No flush is scheduled or forced here; the entry will be handled by maintenance as usual.
     *
     * Semantics:
     * - If the entry doesn't exist: returns false.
     * - If the entry exists but is already non-TEMP: returns true (idempotent).
     * - If the entry exists and is TEMP: sets isTemp=false, bumps lastAccessNanos, returns true.
     *
     * @param path the logical cache key (file path)
     * @return true if the entry exists and is now non-TEMP; false if no such entry
     */
    public static boolean promoteTemp(String path) {
        if (path == null || path.isBlank()) {
            Logger.log(Logger.TAG.ERROR, "promoteTemp: path is null/blank");
            return false;
        }
        final String npath = normalizeFsPath(path);
        final CacheEntry e = ENTRIES.get(npath);
        if (e == null) {
            Logger.log(Logger.TAG.DEBUG, "promoteTemp: no cache entry found for: " + npath);
            return false;
        }

        e.lock.lock();
        try {
            if (!e.isTemp) {
                Logger.log(Logger.TAG.DEBUG, "promoteTemp: entry already non-TEMP: " + npath);
                return true; // idempotent
            }

            e.isTemp = false;
            e.lastAccessNanos = System.nanoTime(); // nudge so TTL/materialize timers start fresh
            Logger.log(Logger.TAG.INFO, "promoteTemp: cleared TEMP flag; entry now regular: " + npath);
            // No enqueue, no force. Regular maintenance will handle it.
            return true;
        } finally {
            e.lock.unlock();
        }
    }

    /**
     * Ensures total memory usage remains under configured limits.
     *
     * Flushes or evicts cached entries based on activity and cleanliness.
     * Preference order:
     *   1) Flush oldest dirty file (that isn't in backoff).
     *   2) Evict oldest clean file if still over memory cap.
     */
    private static void enforceMemoryCap() {
        synchronized (MEM_LOCK) {
            if (Config.CACHE_MEMORY_CAP_BYTES <= 0 ||
                    TOTAL_CACHE_BYTES <= Config.CACHE_MEMORY_CAP_BYTES) {
                return;
            }
        }

        Logger.log(Logger.TAG.WARN, "Memory cap exceeded; initiating enforcement.");

        final long now = System.nanoTime();

        // 1) Try to flush the oldest dirty NON-TEMP file (skip those in backoff)
        String oldestDirty = null;
        long oldestTs = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> it : ENTRIES.entrySet()) {
            String path = it.getKey();
            CacheEntry e = it.getValue();
            e.lock.lock();
            try {
                // Skip TEMP entries entirely for the "flush oldest dirty" stage
                if (!e.isTemp && e.dirty && !e.flushing) {
                    if (e.nextEligibleNanos > 0 && now < e.nextEligibleNanos) {
                        // backoff in effect; skip for now
                    } else if (e.lastAccessNanos < oldestTs) {
                        oldestTs = e.lastAccessNanos;
                        oldestDirty = path;
                    }
                }
            } finally { e.lock.unlock(); }
        }

        if (oldestDirty != null) {
            Logger.log(Logger.TAG.DEBUG, "Memory cap enforcement: flushing oldest dirty cache (" + oldestDirty + ")");
            requestFlush(oldestDirty, false); // non-forced is fine; worker is active
            return;
        }

        // 2) Prefer to evict/delete a TEMP cache first (clean TEMP, then any TEMP as last resort)
        String oldestCleanTemp = null;
        long oldestCleanTempTs = Long.MAX_VALUE;

        String oldestCleanRegular = null;
        long oldestCleanRegularTs = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> it : ENTRIES.entrySet()) {
            String path = it.getKey();
            CacheEntry e = it.getValue();
            e.lock.lock();
            try {
                if (!e.flushing && e.data != null) {
                    if (!e.dirty) {
                        // Clean entries — keep the oldest temp and the oldest regular separately
                        if (e.isTemp) {
                            if (e.lastAccessNanos < oldestCleanTempTs) {
                                oldestCleanTempTs = e.lastAccessNanos;
                                oldestCleanTemp = path;
                            }
                        } else {
                            if (e.lastAccessNanos < oldestCleanRegularTs) {
                                oldestCleanRegularTs = e.lastAccessNanos;
                                oldestCleanRegular = path;
                            }
                        }
                    }
                }
            } finally { e.lock.unlock(); }
        }

        // 2a) If we have a clean TEMP cache, schedule deletion (handled by flushOne)
        if (oldestCleanTemp != null) {
            final String npath = normalizeFsPath(oldestCleanTemp);
            CacheEntry e = ENTRIES.get(npath);
            if (e != null) {
                e.lock.lock();
                try {
                    if (e.isTemp && !e.flushing && e.data != null && !e.dirty) {
                        requestFlush(npath, /*force=*/true);
                        Logger.log(Logger.TAG.WARN, "Memory cap: queued CLEAN TEMP cache for delete: " + npath);
                        return;
                    }
                } finally { e.lock.unlock(); }
            }
        }

        // 2b) Otherwise, evict oldest CLEAN regular cache (drop JSON only)
        if (oldestCleanRegular != null) {
            CacheEntry e = ENTRIES.get(oldestCleanRegular);
            if (e != null) {
                e.lock.lock();
                try {
                    if (!e.isTemp && !e.dirty && !e.flushing && e.data != null) {
                        long bytes = e.approxBytes;
                        e.data = null; // drop JSON
                        e.approxBytes = 0;
                        synchronized (MEM_LOCK) {
                            TOTAL_CACHE_BYTES = Math.max(0, TOTAL_CACHE_BYTES - bytes);
                        }
                        Logger.log(Logger.TAG.WARN, "Evicted clean cache to free memory: " + oldestCleanRegular);
                        return;
                    }
                } finally { e.lock.unlock(); }
            }
        }

        // 3) LAST RESORT: if still over cap and no clean victims,
        // queue the oldest TEMP entry (any state) for deletion to protect memory.
        String oldestTempAny = null;
        long oldestTempAnyTs = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> it : ENTRIES.entrySet()) {
            String path = it.getKey();
            CacheEntry e = it.getValue();
            e.lock.lock();
            try {
                if (e.isTemp && !e.flushing && e.data != null) {
                    if (e.lastAccessNanos < oldestTempAnyTs) {
                        oldestTempAnyTs = e.lastAccessNanos;
                        oldestTempAny = path;
                    }
                }
            } finally { e.lock.unlock(); }
        }

        if (oldestTempAny != null) {
            final String npath = normalizeFsPath(oldestTempAny);
            CacheEntry e = ENTRIES.get(npath);
            if (e != null) {
                e.lock.lock();
                try {
                    if (e.isTemp && !e.flushing && e.data != null) {
                        requestFlush(npath, /*force=*/true);
                        Logger.log(Logger.TAG.WARN, "Memory cap: queued TEMP cache for delete (last resort): " + npath);
                        return;
                    }
                } finally { e.lock.unlock(); }
            }
        }

        // If we get here, nothing was eligible to flush/evict/delete (rare).
        Logger.log(Logger.TAG.DEBUG, "Memory cap enforcement found no eligible candidates this cycle.");
    }

    /* ===================== Helpers ===================== */

    public static void onPathRenamed(String oldPath, String newPath) {
        final String o = normalizeFsPath(oldPath);
        final String n = normalizeFsPath(newPath);
        if (o == null || n == null || o.equals(n)) return;

        CacheEntry entry;
        synchronized (ENTRIES) {
            entry = ENTRIES.remove(o);
            if (entry != null) {
                ENTRIES.put(n, entry);
            }
        }
        if (entry != null) {
            ENQUEUED.remove(o);
            Logger.log(Logger.TAG.DEBUG, "QueueManager.onPathRenamed: rebound cache " + o + " → " + n);
        }
    }

    /**
     * Ensures that the cache entry is loaded into memory and up-to-date.
     *
     * Loads the file from disk if necessary using the provided loader,
     * or uses the cached data if already present. Updates enforcement
     * flags and memory usage as needed.
     *
     * @param e         the cache entry
     * @param path      file path associated with this cache
     * @param rawLoader optional custom loader
     * @param opt       queue options to override root enforcement
     * @throws Exception if the load operation fails
     */
    private static void ensureCacheLoaded(CacheEntry e,
                                          String path,
                                          Callable<JSONObject> rawLoader,
                                          QueueOptions... opt) throws Exception {
        boolean enforceRoot = (opt != null && opt.length > 0 && opt[0] != null)
                ? opt[0].enforceObjectRoot
                : e.enforceObjectRoot;

        // 1) If cache exists, stick with it — never reload a dirty cache.
        if (e.data != null) {
            e.enforceObjectRoot = enforceRoot;
            Logger.log(Logger.TAG.DEBUG, "ensureCacheLoaded: cache hit (" + (e.dirty ? "dirty" : "clean") + ") → using in-memory for: " + path);
            return;
        }

        // 2) No cache yet → always delegate to loader (merges base + journal if present).
        Logger.log(Logger.TAG.DEBUG, "ensureCacheLoaded: performing disk load via loader for: " + path);
        Object raw;
        try {
            raw = (rawLoader != null) ? rawLoader.call() : RawIO.load(path);
        } catch (Exception ex) {
            Logger.log(Logger.TAG.WARN, "ensureCacheLoaded: loader failed, initializing empty JSON for " + path + " — " + ex.getMessage());
            raw = null;
        }

        JSONObject loaded;
        if (raw == null) {
            loaded = new JSONObject();
        } else if (raw instanceof JSONObject obj) {
            loaded = obj;
        } else if (raw instanceof org.json.JSONArray arr) {
            // Keep wrapper consistent so root is predictable even if not enforcing
            loaded = new JSONObject().put("root", arr);
            Logger.log(Logger.TAG.DEBUG, "ensureCacheLoaded: wrapped array root into object for " + path);
        } else {
            Logger.log(Logger.TAG.WARN, "ensureCacheLoaded: unexpected root type (" + raw.getClass().getSimpleName() + "); using empty object.");
            loaded = new JSONObject();
        }

        e.data = loaded;
        e.enforceObjectRoot = enforceRoot;
        refreshSize(e);
        e.lastAccessNanos = System.nanoTime();
        Logger.log(Logger.TAG.DEBUG, "ensureCacheLoaded: cache initialized for " + path + " (size=" + e.approxBytes + " bytes)");
    }

    /**
     * Updates estimated memory usage for a cache entry.
     *
     * Uses serialized length of the JSON data as an approximate size.
     *
     * @param e cache entry whose size should be refreshed
     */
    private static void refreshSize(CacheEntry e) {
        long newSize = 0L;
        if (e.data != null) {
            newSize = e.data.toString().getBytes(StandardCharsets.UTF_8).length;
        }
        synchronized (MEM_LOCK) {
            TOTAL_CACHE_BYTES = TOTAL_CACHE_BYTES - e.approxBytes + newSize;
        }
        e.approxBytes = newSize;

        Logger.log(Logger.TAG.DEBUG, "Refreshed cache size: " + newSize + " bytes (total=" + TOTAL_CACHE_BYTES + ")");
    }

    /** DEPRECATED
     *
     * Inform the queue that a file was deleted externally (DBM-level delete).
     */
    public static void onExternalDelete(String path) {
        final String n = normalizeFsPath(path);
        CacheEntry e = ENTRIES.remove(n);
        if (e != null) {
            ENQUEUED.remove(n);
            FLUSH_QUEUE.remove(n);
            FORCED_QUEUE.remove(n);
            Logger.log(Logger.TAG.DEBUG, "QueueManager.onExternalDelete: dropped cache & queues for " + n);
        }
    }

    /* ===================== JSON Patch Writer ===================== */

    /**
     * Utility for detecting and applying incremental JSON differences.
     *
     * Recurses over JSONObject/JSONArray structures. Paths are dot-separated object keys.
     * Arrays are compared element-wise; any difference replaces the array at its path.
     * Type changes (e.g., object→array, number→string) are treated as changes.
     */
    private static final class JSONDiffUtil {

        /**
         * Computes a recursive map of changed paths between two JSON structures.
         * If either input is null or a non-JSONObject (e.g., JSONArray or primitive),
         * a single "root" replacement entry is returned.
         */
        static Map<String, Object> diff(Object oldJson, Object newJson) {
            Map<String, Object> changes = new LinkedHashMap<>();

            // Handle null roots (no changes)
            if (oldJson == null && newJson == null) {
                Logger.log(Logger.TAG.DEBUG, "JSONDiffUtil.diff: both roots null → no changes.");
                return changes;
            }

            // If either is not a JSONObject, do a root replace when unequal
            if (!(oldJson instanceof JSONObject oldObj) || !(newJson instanceof JSONObject newObj)) {
                if (!jsonEqual(oldJson, newJson)) {
                    changes.put("root", deepCopyJson(newJson));
                    Logger.log(Logger.TAG.DEBUG, "JSONDiffUtil.diff: non-object root → full replace required.");
                }
                return changes;
            }

            // Recursive diffing for objects
            diffRecursive("", oldObj, newObj, changes);
            Logger.log(Logger.TAG.DEBUG, "JSONDiffUtil.diff: computed " + changes.size() + " changes.");
            return changes;
        }

        private static void diffRecursive(String prefix, JSONObject oldObj, JSONObject newObj, Map<String, Object> out) {
            // Keys present in newObj
            for (String key : newObj.keySet()) {
                Object newVal = newObj.opt(key);
                Object oldVal = oldObj.has(key) ? oldObj.opt(key) : null;
                String path = prefix.isEmpty() ? key : prefix + "." + key;

                // Same by deep JSON semantics → no change
                if (jsonEqual(oldVal, newVal)) continue;

                // Both objects → recurse
                if (newVal instanceof JSONObject && oldVal instanceof JSONObject) {
                    diffRecursive(path, (JSONObject) oldVal, (JSONObject) newVal, out);
                    // If recursion produced no nested diffs, it was equal; otherwise nested paths were added
                    continue;
                }

                // Both arrays → element-wise compare; if any change, replace array at path
                if (newVal instanceof JSONArray && oldVal instanceof JSONArray) {
                    if (!arraysEqual((JSONArray) oldVal, (JSONArray) newVal)) {
                        out.put(path, deepCopyJson(newVal));
                        Logger.log(Logger.TAG.DEBUG, "Array diff detected at path: " + path);
                    }
                    continue;
                }

                // Type differs or primitive value differs → set new value
                out.put(path, deepCopyJson(newVal));
                Logger.log(Logger.TAG.DEBUG, "Value changed at path: " + path + " → " + summarize(newVal));
            }

            // Keys removed
            for (String key : oldObj.keySet()) {
                if (!newObj.has(key)) {
                    String path = prefix.isEmpty() ? key : prefix + "." + key;
                    out.put(path, JSONObject.NULL);
                    Logger.log(Logger.TAG.DEBUG, "Key removed: " + path);
                }
            }
        }

        private static boolean arraysEqual(JSONArray a, JSONArray b) {
            if (a.length() != b.length()) return false;
            for (int i = 0; i < a.length(); i++) {
                Object va = a.opt(i);
                Object vb = b.opt(i);

                // Deep equal for JSON types
                if (jsonEqual(va, vb)) continue;

                // If both are objects → recurse check via nested diff
                if (va instanceof JSONObject && vb instanceof JSONObject) {
                    Map<String, Object> inner = new LinkedHashMap<>();
                    diffRecursive("", (JSONObject) va, (JSONObject) vb, inner);
                    if (!inner.isEmpty()) return false;
                    continue;
                }

                // If both are arrays → recurse element-wise
                if (va instanceof JSONArray && vb instanceof JSONArray) {
                    if (!arraysEqual((JSONArray) va, (JSONArray) vb)) return false;
                    continue;
                }

                // Primitive/type mismatch → not equal
                return false;
            }
            return true;
        }

        /**
         * DEPRICATED
         *
         * Applies a recursive diff map to a base JSONObject.
         * If the base is null or a "root" replacement is detected, replaces entirely.
         */
        static JSONObject apply(JSONObject base, Map<String, Object> changes) {
            if (changes == null || changes.isEmpty()) {
                Logger.log(Logger.TAG.DEBUG, "JSONDiffUtil.apply: no changes to apply.");
                return base;
            }

            // Full root replacement
            if (changes.size() == 1 && changes.containsKey("root")) {
                Object val = changes.get("root");
                Logger.log(Logger.TAG.DEBUG, "JSONDiffUtil.apply: performing full root replacement.");
                if (val instanceof JSONObject obj) return new JSONObject(obj.toString());
                if (val instanceof JSONArray arr) return new JSONObject().put("root", arr);
                if (val == JSONObject.NULL) return new JSONObject();
                return new JSONObject().put("root", val);
            }

            // Deep patching (dot-paths over objects only)
            for (Map.Entry<String, Object> e : changes.entrySet()) {
                String path = e.getKey();
                if ("root".equals(path)) continue; // handled above
                Object val = e.getValue();

                String[] tokens = path.split("\\.");
                JSONObject target = base != null ? base : new JSONObject();
                for (int i = 0; i < tokens.length - 1; i++) {
                    String t = tokens[i];
                    Object mid = target.opt(t);
                    if (!(mid instanceof JSONObject)) {
                        mid = new JSONObject();
                        target.put(t, mid);
                    }
                    target = (JSONObject) mid;
                }

                String last = tokens[tokens.length - 1];
                if (val == JSONObject.NULL) {
                    target.remove(last);
                    Logger.log(Logger.TAG.DEBUG, "Removed key at: " + path);
                } else {
                    target.put(last, deepCopyJson(val));
                    Logger.log(Logger.TAG.DEBUG, "Patched key: " + path + " → " + summarize(val));
                }
            }

            Logger.log(Logger.TAG.DEBUG, "JSONDiffUtil.apply: applied " + changes.size() + " total changes.");
            return base;
        }

        /* -------------------- helpers -------------------- */

        /** Type-aware JSON equality (objects, arrays, primitives, null). */
        private static boolean jsonEqual(Object a, Object b) {
            if (a == b) return true;
            if (a == null || b == null) return false;

            // JSONObject: deep compare via key sets + values
            if (a instanceof JSONObject ao && b instanceof JSONObject bo) {
                if (!ao.keySet().equals(bo.keySet())) return false;
                for (String k : ao.keySet()) {
                    if (!jsonEqual(ao.opt(k), bo.opt(k))) return false;
                }
                return true;
            }

            // JSONArray: element-wise
            if (a instanceof JSONArray aa && b instanceof JSONArray bb) {
                return arraysEqual(aa, bb);
            }

            // Numbers: compare by numeric value (not string) to avoid "1" vs "1.0" mismatches
            if (a instanceof Number na && b instanceof Number nb) {
                double da = na.doubleValue(), db = nb.doubleValue();
                if (Double.isNaN(da) && Double.isNaN(db)) return true; // both NaN count as equal here (they shouldn't be present post-validation)
                return Double.doubleToLongBits(da) == Double.doubleToLongBits(db);
            }

            // Booleans/Strings: normal equals
            if ((a instanceof String && b instanceof String) ||
                    (a instanceof Boolean && b instanceof Boolean)) {
                return a.equals(b);
            }

            // Different types → not equal
            return false;
        }

        /** Returns a defensive deep copy for JSON values (objects/arrays) to avoid aliasing. */
        private static Object deepCopyJson(Object v) {
            if (v == null || v == JSONObject.NULL) return v;
            if (v instanceof JSONObject obj) return new JSONObject(obj.toString());
            if (v instanceof JSONArray arr) return new JSONArray(arr.toString());
            return v; // primitives are immutable
        }

        /** Short value summary for logs (keeps noise down). */
        private static String summarize(Object v) {
            if (v == null) return "null";
            if (v == JSONObject.NULL) return "JSONObject.NULL";
            if (v instanceof JSONObject o) return "JSONObject(keys=" + o.keySet().size() + ")";
            if (v instanceof JSONArray a) return "JSONArray(len=" + a.length() + ")";
            if (v instanceof String s) return "String(len=" + s.length() + ")";
            return v.getClass().getSimpleName() + "(" + String.valueOf(v) + ")";
        }
    }
}
