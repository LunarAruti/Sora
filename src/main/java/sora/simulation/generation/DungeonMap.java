package sora.simulation.generation;

import java.util.ArrayList;
import java.util.List;

public final class DungeonMap {
    private final long seed;
    private final long artifactFingerprint;
    private final DungeonGenerationConfig config;
    private final List<DungeonPlacedArtifact> placements;

    DungeonMap(
            long seed,
            long artifactFingerprint,
            DungeonGenerationConfig config,
            List<DungeonPlacedArtifact> placements
    ) {
        this.seed = seed;
        this.artifactFingerprint = artifactFingerprint;
        this.config = config;
        this.placements = List.copyOf(placements);
    }

    public long getSeed() { return seed; }
    public long getArtifactFingerprint() { return artifactFingerprint; }
    public DungeonGenerationConfig getConfig() { return config; }
    public List<DungeonPlacedArtifact> getPlacements() { return placements; }

    public List<DungeonPlacedArtifact> getPlacementsInChunk(int chunkX, int chunkY) {
        return getPlacementsIntersecting(config.getChunkBounds(chunkX, chunkY));
    }

    public List<DungeonPlacedArtifact> getPlacementsIntersecting(DungeonRect area) {
        List<DungeonPlacedArtifact> visible = new ArrayList<>();
        for (DungeonPlacedArtifact placement : placements) {
            if (placement.intersects(area)) {
                visible.add(placement);
            }
        }
        return List.copyOf(visible);
    }

    public List<DungeonLine> getWallsIntersecting(DungeonRect area) {
        List<DungeonLine> walls = new ArrayList<>();
        for (DungeonPlacedArtifact placement : getPlacementsIntersecting(area)) {
            walls.addAll(placement.getWorldWalls());
        }
        return List.copyOf(walls);
    }

    public List<DungeonLine> getSealedOpeningWallsIntersecting(DungeonRect area) {
        List<DungeonLine> walls = new ArrayList<>();
        for (DungeonPlacedArtifact placement : getPlacementsIntersecting(area)) {
            walls.addAll(placement.getWorldSealedOpeningWalls());
        }
        return List.copyOf(walls);
    }
}
