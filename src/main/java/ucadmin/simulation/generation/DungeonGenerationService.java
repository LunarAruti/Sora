package ucadmin.simulation.generation;

public final class DungeonGenerationService {
    private DungeonArtifactCatalog cachedCatalog;
    private DungeonMap cachedMap;
    private long cachedSeed;
    private String cachedConfigKey;
    private long cachedArtifactFingerprint;

    public DungeonMap generate(long seed) {
        return generate(seed, DungeonGenerationConfig.defaultConfig());
    }

    public DungeonMap generate(long seed, DungeonGenerationConfig config) {
        if (cachedCatalog == null) {
            cachedCatalog = DungeonArtifactCatalogLoader.loadDefaultCatalog();
        }
        String configKey = config.cacheKey();
        if (cachedMap != null &&
                cachedSeed == seed &&
                cachedArtifactFingerprint == cachedCatalog.getFingerprint() &&
                configKey.equals(cachedConfigKey)) {
            return cachedMap;
        }

        cachedMap = new DungeonGenerator(cachedCatalog, config).generate(seed);
        cachedSeed = seed;
        cachedConfigKey = configKey;
        cachedArtifactFingerprint = cachedCatalog.getFingerprint();
        return cachedMap;
    }

    public DungeonLoadedArea loadArea(long seed, DungeonGenerationConfig config, DungeonRect loadedBounds) {
        DungeonMap map = generate(seed, config);
        return new DungeonLoadedArea(loadedBounds, map.getPlacementsIntersecting(loadedBounds));
    }

    public void reloadCatalog() {
        cachedCatalog = DungeonArtifactCatalogLoader.loadDefaultCatalog();
        cachedMap = null;
        cachedConfigKey = null;
    }

    public void clear() {
        cachedCatalog = null;
        cachedMap = null;
        cachedConfigKey = null;
        cachedArtifactFingerprint = 0L;
        cachedSeed = 0L;
    }
}
