package ucadmin.simulation.generation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DungeonPlacedArtifact {
    private final int placementIndex;
    private final DungeonArtifactTemplate template;
    private final DungeonPoint center;
    private final int rotationDegrees;
    private final Set<Integer> connectedOpeningIndexes = new HashSet<>();

    DungeonPlacedArtifact(
            int placementIndex,
            DungeonArtifactTemplate template,
            DungeonPoint center,
            int rotationDegrees
    ) {
        this.placementIndex = placementIndex;
        this.template = Objects.requireNonNull(template, "template");
        this.center = Objects.requireNonNull(center, "center");
        this.rotationDegrees = Math.floorMod(rotationDegrees, 360);
    }

    public int getPlacementIndex() { return placementIndex; }
    public DungeonArtifactTemplate getTemplate() { return template; }
    public DungeonPoint getCenter() { return center; }
    public int getRotationDegrees() { return rotationDegrees; }

    public List<DungeonLine> getWorldWalls() {
        List<DungeonLine> lines = new ArrayList<>();
        for (DungeonLine wall : template.getWalls()) {
            lines.add(wall.rotateClockwise(rotationDegrees).translate(center));
        }
        return List.copyOf(lines);
    }

    public List<DungeonLine> getWorldSealedOpeningWalls() {
        List<DungeonLine> lines = new ArrayList<>();
        for (int i = 0; i < template.getOpenings().size(); i++) {
            if (!connectedOpeningIndexes.contains(i)) {
                lines.add(getWorldOpening(i).sealedWall());
            }
        }
        return List.copyOf(lines);
    }

    public List<DungeonOpening> getWorldOpenings() {
        List<DungeonOpening> openings = new ArrayList<>();
        for (int i = 0; i < template.getOpenings().size(); i++) {
            openings.add(getWorldOpening(i));
        }
        return List.copyOf(openings);
    }

    public List<DungeonOccupiedArea> getWorldOccupiedAreas() {
        List<DungeonOccupiedArea> areas = new ArrayList<>();
        for (DungeonOccupiedArea area : template.getOccupiedAreas()) {
            areas.add(area.rotateClockwise(rotationDegrees).translate(center));
        }
        return List.copyOf(areas);
    }

    public DungeonOpening getWorldOpening(int openingIndex) {
        return template.getOpenings()
                .get(openingIndex)
                .rotateClockwise(rotationDegrees)
                .translate(center);
    }

    public boolean isOpeningConnected(int openingIndex) {
        return connectedOpeningIndexes.contains(openingIndex);
    }

    void connectOpening(int openingIndex) {
        connectedOpeningIndexes.add(openingIndex);
    }

    public boolean intersects(DungeonRect area) {
        for (DungeonOccupiedArea occupied : getWorldOccupiedAreas()) {
            if (occupied.intersects(area)) {
                return true;
            }
        }
        return false;
    }
}
