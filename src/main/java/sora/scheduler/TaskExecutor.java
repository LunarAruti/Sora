package sora.scheduler;

import java.util.*;

/**
 * Resolves and executes whitelisted task operations.
 * opArgs is a comma-separated list; quoted tokens preserve commas.
 */
public final class TaskExecutor {

    /**
     * Keep results small. Scheduler stores this as lastResult.
     */
    public static final class TaskResult {
        public final boolean ok;
        public final String msg;

        /** Internal constructor for result values. */
        private TaskResult(boolean ok, String msg) {
            this.ok = ok;
            this.msg = msg;
        }

        /** Returns a success result with a short message. */
        public static TaskResult ok(String msg) { return new TaskResult(true, msg); }
        /** Returns a failure result with a short message. */
        public static TaskResult fail(String msg) { return new TaskResult(false, msg); }
    }

    /**
     * One op = one handler.
     * Handler must:
     * - validate args
     * - run a bounded predefined operation
     * - return a short result
     */
    public interface TaskOpHandler {
        /** Executes a whitelisted op with parsed arguments. */
        TaskResult run(Args args) throws Exception;
    }

    /**
     * Called by your scheduler's executor layer.
     *
     * @param opKey  the operation keyword stored on the task
     * @param opArgs the easy-mode args string stored on the task
     */
    public static TaskResult execute(String opKey, String opArgs) throws Exception {
        Objects.requireNonNull(opKey, "opKey");
        String normalizedKey = opKey.trim().toUpperCase(Locale.ROOT);
        TaskOpHandler handler = ExeWhitelist.get(normalizedKey);
        if (handler == null) {
            sora.util.Logger.log(sora.util.Logger.TAG.WARN,
                    "[12001] TaskExecutor: opKey not whitelisted opKey=" + normalizedKey);
            return TaskResult.fail("Unknown opKey (not whitelisted): " + normalizedKey);
        }

        Args parsed;
        try {
            parsed = Args.parse(opArgs);
        } catch (IllegalArgumentException e) {
            sora.util.Logger.log(sora.util.Logger.TAG.ERROR,
                    "[12002] TaskExecutor: args parse failed opKey=" + normalizedKey + " err=" + e.getMessage());
            throw e;
        }

        try {
            return handler.run(parsed);
        } catch (Exception e) {
            sora.util.Logger.log(sora.util.Logger.TAG.ERROR,
                    "[12003] TaskExecutor: handler failed opKey=" + normalizedKey + " err=" + e.getMessage());
            throw e;
        }
    }

    /** Returns all whitelisted operation keys. */
    public static Set<String> listOpKeys() {
        return ExeWhitelist.listOpKeys();
    }

    /**
     * Args = parsed list + tiny typed validation helpers.
     *
     * This avoids each op reinventing parsing and common validation.
     */
    public static final class Args {
        private final List<String> list;

        /** Internal constructor for parsed args. */
        private Args(List<String> list) {
            this.list = list;
        }

        /** Parses a comma-separated argument string into a list. */
        public static Args parse(String raw) {
            if (raw == null || raw.trim().isEmpty()) return new Args(new ArrayList<>());

            List<String> tokens = tokenize(raw);
            List<String> out = new ArrayList<>();

            for (String tok : tokens) {
                String token = tok.trim();
                if (token.isEmpty()) {
                    throw new IllegalArgumentException("Empty arg token");
                }

                String val;
                if (token.startsWith("\"") || token.endsWith("\"")) {
                    if (token.length() < 2 || !token.startsWith("\"") || !token.endsWith("\"")) {
                        throw new IllegalArgumentException("Unmatched quote in arg token");
                    }
                    val = token.substring(1, token.length() - 1);
                } else {
                    val = token;
                }

                out.add(val);
            }
            return new Args(out);
        }

        /** Splits by commas unless inside quotes; quotes are literal and not escaped. */
        private static List<String> tokenize(String raw) {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inQuotes = false;

            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);

                if (c == '"') {
                    inQuotes = !inQuotes;
                    cur.append(c);
                    continue;
                }

                if (!inQuotes && c == ',') {
                    if (cur.length() > 0) {
                        out.add(cur.toString());
                        cur.setLength(0);
                    }
                    continue;
                }

                cur.append(c);
            }

            if (inQuotes) throw new IllegalArgumentException("Unclosed quote in args");

            if (cur.length() > 0) out.add(cur.toString());
            return out;
        }

        /**
         * Returns a required argument by index.
         * Throws if the index is missing.
         */
        public Arg req(int index) {
            if (index < 0 || index >= list.size()) {
                throw new IllegalArgumentException("Missing required arg at index: " + index);
            }
            return new Arg(index, list.get(index), true);
        }

        /**
         * Returns an optional argument by index.
         * Use def(...) to supply a default when missing.
         */
        public Arg opt(int index) {
            if (index < 0 || index >= list.size()) {
                return new Arg(index, null, false);
            }
            return new Arg(index, list.get(index), true);
        }

        /** Returns the argument count. */
        public int size() {
            return list.size();
        }

        /** Returns an immutable view of all arguments. */
        public List<String> list() {
            return Collections.unmodifiableList(list);
        }
    }

    /**
     * Arg = chainable validators.
     * Keep these tiny and strict.
     */
    public static final class Arg {
        private final String raw;
        private String value;
        private boolean present;

        private final int index;

        /** Internal constructor for a single argument wrapper. */
        private Arg(int index, String raw, boolean present) {
            this.index = index;
            this.raw = raw;
            this.present = present;
            this.value = raw;
        }

        /** Applies a default value if the argument is missing. */
        public Arg def(String defaultValue) {
            if (!present) {
                this.present = true;
                this.value = defaultValue;
            }
            return this;
        }

        /** Enforces a maximum length when present. */
        public Arg maxLen(int max) {
            if (present && value.length() > max) {
                throw new IllegalArgumentException("Arg[" + index + "] too long (max " + max + ")");
            }
            return this;
        }

        /** Enforces that the argument matches one of the allowed values. */
        public Arg oneOf(String... allowed) {
            if (!present) return this;
            for (String a : allowed) {
                if (a.equals(value)) return this;
            }
            throw new IllegalArgumentException("Arg[" + index + "] invalid value: " + value);
        }

        /** Validates a numeric id string with a small length limit. */
        public String asId() {
            if (!present) throw new IllegalArgumentException("Missing arg at index: " + index);
            if (value.length() > 32) throw new IllegalArgumentException("Arg[" + index + "] id too long");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < '0' || c > '9') throw new IllegalArgumentException("Arg[" + index + "] not numeric id");
            }
            return value;
        }

        /** Returns the argument value or throws if missing. */
        public String value() {
            if (!present) throw new IllegalArgumentException("Missing arg at index: " + index);
            return value;
        }
    }

    /** Prevents instantiation. */
    private TaskExecutor() {}
}
