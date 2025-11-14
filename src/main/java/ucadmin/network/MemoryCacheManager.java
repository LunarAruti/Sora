package ucadmin.network;

import ucadmin.exceptions.NetworkException;
import static ucadmin.exceptions.NetworkException.*;
import ucadmin.util.Logger;
import ucadmin.util.Logger.TAG;

import java.util.concurrent.ConcurrentHashMap;

public final class MemoryCacheManager {
    private static final ConcurrentHashMap<String, Object> CACHE = new ConcurrentHashMap<>();

    public static boolean put(String path, Object json) throws CacheWriteException {
        try {
            CACHE.put(path, json);
            Logger.log(TAG.DEBUG, "MemoryCacheManager: stored " + path);
            return true;
        } catch (Exception e) {
            throw new CacheWriteException("CACHE_PUT_FAILED: " + e.getMessage(),
                    "<mem>", "<put>", "<none>", path, "MEMORY", e);
        }
    }

    public static Object get(String path) { return CACHE.get(path); }
    public static boolean contains(String path) { return CACHE.containsKey(path); }
    public static void remove(String path) { CACHE.remove(path); }
    public static void clearAll() { CACHE.clear(); }
}
