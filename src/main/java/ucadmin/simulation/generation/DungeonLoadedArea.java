package ucadmin.simulation.generation;

import java.util.ArrayList;
import java.util.List;

public final class DungeonLoadedArea {
    private final DungeonRect loadedBounds;
    private final List<DungeonPlacedArtifact> placements;

    DungeonLoadedArea(DungeonRect loadedBounds, List<DungeonPlacedArtifact> placements) {
        this.loadedBounds = loadedBounds;
        this.placements = List.copyOf(placements);
    }

    public DungeonRect getLoadedBounds() { return loadedBounds; }
    public List<DungeonPlacedArtifact> getPlacements() { return placements; }

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

    public List<DungeonPlacedArtifact> getPlacementsIntersecting(DungeonRect area) {
        List<DungeonPlacedArtifact> visible = new ArrayList<>();
        for (DungeonPlacedArtifact placement : placements) {
            if (placement.intersects(area)) {
                visible.add(placement);
            }
        }
        return List.copyOf(visible);
    }
}
