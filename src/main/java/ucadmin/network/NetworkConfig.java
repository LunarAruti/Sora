package ucadmin.network;

import ucadmin.util.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/**
 * Global configuration and policy holder for the UC Admin network module.
 *
 * <p>This class centralizes cross-cutting network policies that are not specific
 * to a single {@link NetworkRequest}, including:</p>
 *
 * <ul>
 *     <li>Host allow/deny lists (whitelist / blacklist).</li>
 *     <li>Default rate limiter buckets per service or host.</li>
 *     <li>Default response size limits per service or host.</li>
 * </ul>
 *
 * <p>The intent is that low-level components such as {@code HttpExecutor},
 * {@code RateLimiterRegistry}, and {@code CircuitBreakerRegistry} consult
 * this configuration when no more specific settings are provided on the
 * {@link NetworkRequest} itself.</p>
 *
 * <p>All mutating methods are thread-safe and primarily intended for startup
 * initialization or tests. In normal operation you would usually configure
 * this once during process boot.</p>
 */
public final class NetworkConfig {

    /**
     * If the whitelist is non-empty, only hosts present in the whitelist
     * will be allowed (unless explicitly blacklisted). If the whitelist is
     * empty, all hosts are allowed except those in the blacklist.
     */
    private static final Set<String> HOST_WHITELIST =
            Collections.synchronizedSet(new LinkedHashSet<>());

    /** Hosts that are never allowed, regardless of whitelist state. */
    private static final Set<String> HOST_BLACKLIST =
            Collections.synchronizedSet(new LinkedHashSet<>());

    /**
     * Default rate limiter buckets keyed by logical service name or host.
     * For example:
     * <ul>
     *     <li>{@code "roblox" -> "roblox.read"}</li>
     *     <li>{@code "apis.roblox.com" -> "roblox.global"}</li>
     * </ul>
     */
    private static final Map<String, String> DEFAULT_RATE_BUCKETS =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * Default maximum response size in bytes, keyed by logical service name
     * or host. These are only used when a {@link NetworkRequest} does not
     * provide an explicit {@code maxResponseBytes}.
     */
    private static final Map<String, Long> DEFAULT_MAX_RESPONSE_BYTES =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * Optional global hard cap for response sizes. If non-null, no individual
     * request may exceed this many bytes even if its own or service-level
     * limit is higher.
     */
    private static volatile Long GLOBAL_MAX_RESPONSE_BYTES = null;

    private NetworkConfig() {
        // no instances
    }

    // ----------------------------------------------------------------------
    // Host allow / deny
    // ----------------------------------------------------------------------

    /**
     * Adds a hostname to the global host whitelist.
     *
     * <p>
     * When the whitelist is non-empty, <strong>only hosts explicitly included</strong>
     * in the whitelist are considered allowed, unless the same host also appears in
     * the blacklist. If the whitelist is empty, all hosts are allowed except for
     * those blacklisted.
     * </p>
     *
     * <p>
     * This method is typically called during application startup to configure which
     * domains the network system is permitted to communicate with. It is a safeguard
     * against misconfigured or malicious outbound requests.
     * </p>
     *
     * <p><strong>Usage example:</strong></p>
     * <pre>
     * NetworkConfig.addWhitelistedHost("apis.roblox.com");
     * </pre>
     *
     * <p><strong>Notes:</strong></p>
     * <ul>
     *   <li>Hostnames are normalized to lowercase.</li>
     *   <li>Invalid or blank input is ignored without error.</li>
     *   <li>Thread-safe.</li>
     * </ul>
     *
     * @param host the hostname to allow (e.g., "example.com")
     * @return true if the whitelist changed, false otherwise
     */
    public static boolean addWhitelistedHost(String host) {
        if (host == null || host.isBlank()) {
            Logger.log(Logger.TAG.WARN, "NetworkConfig.addWhitelistedHost: invalid host (null/blank)");
            return false;
        }
        String h = host.trim().toLowerCase();
        boolean changed = HOST_WHITELIST.add(h);
        if (changed) {
            Logger.log(Logger.TAG.SYSTEM,
                    "NetworkConfig: added WHITELIST host=" + h);
        } else {
            Logger.log(Logger.TAG.WARN,
                    "NetworkConfig: WHITELIST no-op (already present) host=" + h);
        }
        return changed;
    }

    /**
     * Adds a hostname to the blacklist.
     *
     * <p>
     * Blacklisted hosts are always denied, even if the whitelist is empty.
     * </p>
     *
     * @param host hostname to add to the blacklist
     * @return true if the blacklist changed, false otherwise
     */
    public static boolean addBlacklistedHost(String host) {
        if (host == null || host.isBlank()) {
            Logger.log(Logger.TAG.WARN, "NetworkConfig.addBlacklistedHost: invalid host (null/blank)");
            return false;
        }
        String h = host.trim().toLowerCase();
        boolean changed = HOST_BLACKLIST.add(h);
        if (changed) {
            Logger.log(Logger.TAG.SYSTEM,
                    "NetworkConfig: added BLACKLIST host=" + h);
        } else {
            Logger.log(Logger.TAG.WARN,
                    "NetworkConfig: BLACKLIST no-op (already present) host=" + h);
        }
        return changed;
    }

    /**
     * Removes a hostname from the whitelist.
     *
     * <p>
     * If the whitelist becomes empty after this call, the system defaults to allowing
     * all hosts except those in the blacklist.
     * </p>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>
     * NetworkConfig.removeWhitelistedHost("apis.roblox.com");
     * </pre>
     *
     * @param host the hostname to remove
     * @return true if the whitelist changed, false otherwise
     */
    public static boolean removeWhitelistedHost(String host) {
        if (host == null) {
            Logger.log(Logger.TAG.WARN, "NetworkConfig.removeWhitelistedHost: invalid host (null)");
            return false;
        }
        String h = host.trim().toLowerCase();
        boolean changed = HOST_WHITELIST.remove(h);
        if (changed) {
            Logger.log(Logger.TAG.SYSTEM,
                    "NetworkConfig: removed WHITELIST host=" + h);
        } else {
            Logger.log(Logger.TAG.WARN,
                    "NetworkConfig: WHITELIST no-op (not present) host=" + h);
        }
        return changed;
    }

    /**
     * Removes a hostname from the blacklist.
     *
     * <p>
     * Once removed, the host becomes eligible for requests again (unless the whitelist
     * is non-empty and this host is not in the whitelist).
     * </p>
     *
     * @param host hostname to remove from the blacklist
     * @return true if the blacklist changed, false otherwise
     */
    public static boolean removeBlacklistedHost(String host) {
        if (host == null) {
            Logger.log(Logger.TAG.WARN, "NetworkConfig.removeBlacklistedHost: invalid host (null)");
            return false;
        }
        String h = host.trim().toLowerCase();
        boolean changed = HOST_BLACKLIST.remove(h);
        if (changed) {
            Logger.log(Logger.TAG.SYSTEM,
                    "NetworkConfig: removed BLACKLIST host=" + h);
        } else {
            Logger.log(Logger.TAG.WARN,
                    "NetworkConfig: BLACKLIST no-op (not present) host=" + h);
        }
        return changed;
    }

    /**
     * Returns an immutable snapshot of the current whitelist.
     *
     * <p>
     * This is safe to call from any thread and provides a consistent view of the
     * whitelist at the time of the call.
     * </p>
     *
     * @return an immutable set of whitelisted hostnames
     */
    public static Set<String> getWhitelistedHosts() {
        synchronized (HOST_WHITELIST) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(HOST_WHITELIST));
        }
    }

    /**
     * Returns an immutable snapshot of the current blacklist.
     *
     * <p>
     * Useful for admin/debug tools or for external configuration UIs.
     * </p>
     *
     * @return immutable set of blacklisted hosts
     */
    public static Set<String> getBlacklistedHosts() {
        synchronized (HOST_BLACKLIST) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(HOST_BLACKLIST));
        }
    }

    /**
     * Evaluates whether the given hostname is permitted based on the current
     * whitelist/blacklist configuration.
     *
     * <p><strong>Rule summary:</strong></p>
     * <ul>
     *   <li>If {@code host} is null/blank → <b>false</b>.</li>
     *   <li>If host is in blacklist → <b>false</b>.</li>
     *   <li>If whitelist is empty → <b>true</b> (allow everything not blacklisted).</li>
     *   <li>If whitelist is non-empty → only allow hosts listed in it.</li>
     * </ul>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>
     * if (!NetworkConfig.isHostAllowed(host)) {
     *     throw new SecurityException("Outbound host not permitted.");
     * }
     * </pre>
     *
     * @param host the hostname to test
     * @return true if allowed, false otherwise
     */
    public static boolean isHostAllowed(String host) {
        if (host == null || host.isBlank()) {
            Logger.log(Logger.TAG.DEBUG, "NetworkConfig: hostAllowed=false (null host)");
            return false;
        }

        String key = host.trim().toLowerCase();

        if (HOST_BLACKLIST.contains(key)) {
            Logger.log(Logger.TAG.REQUEST,
                    "NetworkConfig: hostAllowed=false (BLACKLIST) host=" + key);
            return false;
        }

        if (HOST_WHITELIST.isEmpty()) {
            Logger.log(Logger.TAG.DEBUG,
                    "NetworkConfig: hostAllowed=true (no whitelist) host=" + key);
            return true;
        }

        boolean allowed = HOST_WHITELIST.contains(key);
        Logger.log(Logger.TAG.DEBUG,
                "NetworkConfig: hostAllowed=" + allowed +
                        " (whitelist mode) host=" + key);
        return allowed;
    }


    // ----------------------------------------------------------------------
    // Default rate buckets
    // ----------------------------------------------------------------------

    /**
     * Assigns a default rate limiter bucket to a logical service or host key.
     *
     * <p>
     * This lets an entire category of requests share a common rate limit without
     * requiring each {@link NetworkRequest} to explicitly set a rate bucket.
     * </p>
     *
     * <p><strong>Typical uses:</strong></p>
     * <ul>
     *   <li>Assign all "roblox" API calls to a shared bucket.</li>
     *   <li>Throttle calls to a specific domain like "api.example.com".</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>
     * NetworkConfig.setDefaultRateBucket("roblox", "roblox.read");
     * </pre>
     *
     * <p><strong>Note:</strong> Does not validate whether the bucket exists.
     * It only sets mapping. The rate bucket must be configured in
     * {@link ucadmin.network.RateLimiterRegistry#configureBucket}.
     * </p>
     *
     * @param key logical service or host
     * @param bucket the rate bucket name
     * @return true if the mapping changed, false otherwise
     */
    public static boolean setDefaultRateBucket(String key, String bucket) {
        if (key == null || key.isBlank() || bucket == null || bucket.isBlank()) {
            Logger.log(Logger.TAG.WARN, "NetworkConfig.setDefaultRateBucket: invalid key/bucket");
            return false;
        }
        String k = key.trim();
        String b = bucket.trim();
        String prev = DEFAULT_RATE_BUCKETS.put(k, b);
        boolean changed = !Objects.equals(prev, b);
        if (changed) {
            Logger.log(Logger.TAG.SYSTEM,
                    "NetworkConfig: setDefaultRateBucket key=" + k + " bucket=" + b);
        } else {
            Logger.log(Logger.TAG.WARN,
                    "NetworkConfig: setDefaultRateBucket no-op (unchanged) key=" + k);
        }
        return changed;
    }

    /**
     * Removes the default rate bucket mapping for a service/host key.
     *
     * <p>
     * After removal, requests for this key will have no implicit rate bucket unless
     * specified directly on a {@link NetworkRequest}.
     * </p>
     *
     * @param key service or host
     * @return true if a mapping was removed, false otherwise
     */
    public static boolean removeDefaultRateBucket(String key) {
        if (key == null) {
            Logger.log(Logger.TAG.WARN, "NetworkConfig.removeDefaultRateBucket: invalid key (null)");
            return false;
        }
        String k = key.trim();
        String prev = DEFAULT_RATE_BUCKETS.remove(k);
        boolean changed = (prev != null);
        if (changed) {
            Logger.log(Logger.TAG.SYSTEM,
                    "NetworkConfig: removedDefaultRateBucket key=" + k);
        } else {
            Logger.log(Logger.TAG.WARN,
                    "NetworkConfig: removeDefaultRateBucket no-op (not present) key=" + k);
        }
        return changed;
    }

    /**
     * Retrieves the default rate bucket associated with the given service/host key.
     *
     * <p>May return null if:</p>
     * <ul>
     *   <li>No mapping exists.</li>
     *   <li>The key is null or blank.</li>
     * </ul>
     *
     * @param key logical service or host
     * @return bucket name or null
     */
    public static String getDefaultRateBucket(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return DEFAULT_RATE_BUCKETS.get(key.trim());
    }

    /**
     * Returns an immutable snapshot of the entire default rate bucket mapping table.
     *
     * <p>
     * Useful for debugging, configuration dashboards, or exporting current state.
     * </p>
     *
     * @return unmodifiable map of service/host → bucket
     */
    public static Map<String, String> getAllDefaultRateBuckets() {
        synchronized (DEFAULT_RATE_BUCKETS) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(DEFAULT_RATE_BUCKETS));
        }
    }

    // ----------------------------------------------------------------------
    // Default response size limits
    // ----------------------------------------------------------------------

    /**
     * Sets a default per-service/host response size limit (in bytes).
     *
     * <p>
     * This is used when a {@link NetworkRequest} does not specify its own
     * {@code maxResponseBytes}. If {@code maxBytes} is null or ≤ 0, the limit for
     * that key is removed.
     * </p>
     *
     * <p><strong>Usage:</strong></p>
     * <pre>
     * NetworkConfig.setDefaultMaxResponseBytes("roblox", 2_000_000L);
     * </pre>
     *
     * @param key logical service/host
     * @param maxBytes maximum allowed bytes (null → remove limit)
     * @return true if the mapping changed, false otherwise
     */
    public static boolean setDefaultMaxResponseBytes(String key, Long maxBytes) {
        if (key == null || key.isBlank()) {
            Logger.log(Logger.TAG.WARN, "NetworkConfig.setDefaultMaxResponseBytes: invalid key (null/blank)");
            return false;
        }
        String k = key.trim();

        if (maxBytes == null || maxBytes <= 0L) {
            Long prev = DEFAULT_MAX_RESPONSE_BYTES.remove(k);
            boolean changed = (prev != null);
            if (changed) {
                Logger.log(Logger.TAG.SYSTEM,
                        "NetworkConfig: removedDefaultMaxBytes key=" + k);
            } else {
                Logger.log(Logger.TAG.WARN,
                        "NetworkConfig: removeDefaultMaxBytes no-op (not present) key=" + k);
            }
            return changed;
        } else {
            Long prev = DEFAULT_MAX_RESPONSE_BYTES.put(k, maxBytes);
            boolean changed = !Objects.equals(prev, maxBytes);
            if (changed) {
                Logger.log(Logger.TAG.SYSTEM,
                        "NetworkConfig: setDefaultMaxBytes key=" + k + " bytes=" + maxBytes);
            } else {
                Logger.log(Logger.TAG.WARN,
                        "NetworkConfig: setDefaultMaxBytes no-op (unchanged) key=" + k);
            }
            return changed;
        }
    }

    /**
     * Retrieves the default max response size for a given service/host.
     *
     * <p>Returns null if:</p>
     * <ul>
     *   <li>No limit exists.</li>
     *   <li>The key is null/blank.</li>
     * </ul>
     *
     * @param key logical service/host
     * @return max bytes or null
     */
    public static Long getDefaultMaxResponseBytes(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return DEFAULT_MAX_RESPONSE_BYTES.get(key.trim());
    }

    /**
     * Returns an immutable snapshot of all configured default response size limits.
     *
     * <p>
     * This is useful for debugging or displaying configuration in a UI.
     * </p>
     *
     * @return map of key → max bytes
     */
    public static Map<String, Long> getAllDefaultMaxResponseBytes() {
        synchronized (DEFAULT_MAX_RESPONSE_BYTES) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(DEFAULT_MAX_RESPONSE_BYTES));
        }
    }

    /**
     * Sets a global hard cap on response sizes for ALL requests.
     *
     * <p>
     * If set, this global cap overrides:
     * </p>
     * <ul>
     *   <li>Per-request {@code maxResponseBytes}</li>
     *   <li>Per-service default limits</li>
     *   <li>Absence of limits</li>
     * </ul>
     *
     * <p>Passing null disables the global cap.</p>
     *
     * @param maxBytes new global limit, or null to disable
     * @return true if the global value changed, false otherwise
     */
    public static boolean setGlobalMaxResponseBytes(Long maxBytes) {
        Long next = (maxBytes != null && maxBytes > 0L) ? maxBytes : null;
        boolean changed = !Objects.equals(GLOBAL_MAX_RESPONSE_BYTES, next);
        GLOBAL_MAX_RESPONSE_BYTES = next;
        if (changed) {
            Logger.log(Logger.TAG.SYSTEM,
                    "NetworkConfig: setGlobalMaxRespBytes=" + GLOBAL_MAX_RESPONSE_BYTES);
        } else {
            Logger.log(Logger.TAG.WARN,
                    "NetworkConfig: setGlobalMaxRespBytes no-op (unchanged)");
        }
        return changed;
    }

    /**
     * Returns the current global maximum response size, or null if none is
     * configured.
     *
     * @return global limit or null
     */
    public static Long getGlobalMaxResponseBytes() {
        return GLOBAL_MAX_RESPONSE_BYTES;
    }

    /**
     * Computes the effective maximum response size for a service or host key,
     * taking into account the global override and per-service/host defaults.
     *
     * <p><strong>Priority order:</strong></p>
     * <ol>
     *   <li>If a global limit is set → return it.</li>
     *   <li>Else if service/host has a configured default → return it.</li>
     *   <li>Else → return null (no enforced limit at this configuration layer).</li>
     * </ol>
     *
     * <p>
     * This method does not consider per-request overrides — those are applied
     * earlier in {@link NetworkRequest}.
     * </p>
     *
     * @param key logical service/host
     * @return effective max bytes, or null for unlimited
     */
    public static Long computeEffectiveMaxResponseBytes(String key) {
        Long global = GLOBAL_MAX_RESPONSE_BYTES;
        if (global != null && global > 0L) {
            Logger.log(Logger.TAG.DEBUG,
                    "NetworkConfig: effectiveMaxBytes (global)=" + global +
                            " key=" + key);
            return global;
        }

        Long serviceLimit = getDefaultMaxResponseBytes(key);
        Logger.log(Logger.TAG.DEBUG,
                "NetworkConfig: effectiveMaxBytes (service)=" + serviceLimit +
                        " key=" + key);
        return serviceLimit;
    }

    // ----------------------------------------------------------------------
    // Debug helpers
    // ----------------------------------------------------------------------

    /**
     * Logs a concise summary of the current NetworkConfig state, including:
     * <ul>
     *   <li>whitelist size</li>
     *   <li>blacklist size</li>
     *   <li>rate bucket mapping count</li>
     *   <li>default response size count</li>
     *   <li>global response size setting</li>
     * </ul>
     *
     * <p>
     * Typically invoked during application startup to confirm configuration has
     * been loaded correctly.
     * </p>
     */
    public static void logSummary() {
        Logger.log(Logger.TAG.SYSTEM,
                "[NetworkConfig] whitelist=" + HOST_WHITELIST.size() +
                        ", blacklist=" + HOST_BLACKLIST.size() +
                        ", rateBuckets=" + DEFAULT_RATE_BUCKETS.size() +
                        ", maxRespBytes=" + DEFAULT_MAX_RESPONSE_BYTES.size() +
                        ", globalMaxRespBytes=" + GLOBAL_MAX_RESPONSE_BYTES);
    }
}
