package ucadmin.simulation.generation;

public final class DungeonGenerationConfig {
    private final int chunkSize;
    private final int chunksWide;
    private final int chunksHigh;
    private final int maxPlacements;
    private final int maxFailedFrontiers;

    public DungeonGenerationConfig(
            int chunkSize,
            int chunksWide,
            int chunksHigh,
            int maxPlacements,
            int maxFailedFrontiers
    ) {
        if (chunkSize < 1) throw new IllegalArgumentException("chunkSize must be positive.");
        if (chunksWide < 1) throw new IllegalArgumentException("chunksWide must be positive.");
        if (chunksHigh < 1) throw new IllegalArgumentException("chunksHigh must be positive.");
        if (maxPlacements < 1) throw new IllegalArgumentException("maxPlacements must be positive.");
        if (maxFailedFrontiers < 1) throw new IllegalArgumentException("maxFailedFrontiers must be positive.");
        this.chunkSize = chunkSize;
        this.chunksWide = chunksWide;
        this.chunksHigh = chunksHigh;
        this.maxPlacements = maxPlacements;
        this.maxFailedFrontiers = maxFailedFrontiers;
    }

    public static DungeonGenerationConfig defaultConfig() {
        return new DungeonGenerationConfig(50, 20, 20, 900, 4_000);
    }

    public int getChunkSize() { return chunkSize; }
    public int getChunksWide() { return chunksWide; }
    public int getChunksHigh() { return chunksHigh; }
    public int getMaxPlacements() { return maxPlacements; }
    public int getMaxFailedFrontiers() { return maxFailedFrontiers; }

    public String cacheKey() {
        return chunkSize + ":" + chunksWide + ":" + chunksHigh + ":" + maxPlacements + ":" + maxFailedFrontiers;
    }

    public DungeonRect getWorldBounds() {
        int halfWidth = (chunksWide * chunkSize) / 2;
        int halfHeight = (chunksHigh * chunkSize) / 2;
        return new DungeonRect(-halfWidth, -halfHeight, halfWidth, halfHeight);
    }

    public DungeonRect getChunkBounds(int chunkX, int chunkY) {
        int halfWide = chunksWide / 2;
        int halfHigh = chunksHigh / 2;
        int minX = (chunkX - halfWide) * chunkSize;
        int minY = (chunkY - halfHigh) * chunkSize;
        return new DungeonRect(minX, minY, minX + chunkSize, minY + chunkSize);
    }
}
