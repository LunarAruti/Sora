package sora.simulation.generation;

public final class DungeonGenerationService {
    private DungeonArtifactCatalog cachedCatalog;
    private DungeonMap cachedMap;
    private long cachedSeed;
    private DungeonGenerationConfig cachedConfig;

    private DungeonMap generateMap(long seed, DungeonGenerationConfig config) {
        if (cachedCatalog == null) {
            cachedCatalog = DungeonArtifactCatalogLoader.loadDefaultCatalog();
        }
        if (cachedMap != null && cachedSeed == seed && sameConfig(cachedConfig, config)) {
            return cachedMap;
        }
        cachedMap = new DungeonGenerator(cachedCatalog, config).generate(seed);
        cachedSeed = seed;
        cachedConfig = config;
        return cachedMap;
    }

    public DungeonLoadedArea loadArea(long seed, DungeonGenerationConfig config, DungeonRect loadedBounds) {
        DungeonMap map = generateMap(seed, config);
        return new DungeonLoadedArea(loadedBounds, map.getPlacementsIntersecting(loadedBounds));
    }

    public void reloadCatalog() {
        cachedCatalog = DungeonArtifactCatalogLoader.loadDefaultCatalog();
        cachedMap = null;
        cachedConfig = null;
    }

    public void clear() {
        cachedCatalog = null;
        cachedMap = null;
        cachedConfig = null;
    }

    private boolean sameConfig(DungeonGenerationConfig left, DungeonGenerationConfig right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.getChunkSize() == right.getChunkSize() &&
                left.getChunksWide() == right.getChunksWide() &&
                left.getChunksHigh() == right.getChunksHigh() &&
                left.getMaxPlacements() == right.getMaxPlacements() &&
                left.getMaxFailedFrontiers() == right.getMaxFailedFrontiers();
    }
}
