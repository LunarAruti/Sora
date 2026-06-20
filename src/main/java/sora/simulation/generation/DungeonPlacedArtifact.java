package sora.simulation.generation;

import sora.simulation.items.DungeonItemDefinition;
import sora.simulation.items.DungeonItemLibrary;

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
    private final boolean mirroredVertically;
    private final Set<Integer> connectedOpeningIndexes = new HashSet<>();

    DungeonPlacedArtifact(
            int placementIndex,
            DungeonArtifactTemplate template,
            DungeonPoint center,
            int rotationDegrees
    ) {
        this(placementIndex, template, center, rotationDegrees, false);
    }

    DungeonPlacedArtifact(
            int placementIndex,
            DungeonArtifactTemplate template,
            DungeonPoint center,
            int rotationDegrees,
            boolean mirroredVertically
    ) {
        this.placementIndex = placementIndex;
        this.template = Objects.requireNonNull(template, "template");
        this.center = Objects.requireNonNull(center, "center");
        this.rotationDegrees = Math.floorMod(rotationDegrees, 360);
        this.mirroredVertically = mirroredVertically;
    }

    public int getPlacementIndex() { return placementIndex; }
    public DungeonArtifactTemplate getTemplate() { return template; }
    public DungeonPoint getCenter() { return center; }
    public int getRotationDegrees() { return rotationDegrees; }
    public boolean isMirroredVertically() { return mirroredVertically; }

    public List<DungeonLine> getWorldWalls() {
        List<DungeonLine> lines = new ArrayList<>();
        for (DungeonLine wall : template.getWalls()) {
            lines.add(transform(wall).translate(center));
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
            areas.add(transform(area).translate(center));
        }
        return List.copyOf(areas);
    }

    public List<DungeonItem> getWorldItems() {
        List<DungeonItem> items = new ArrayList<>();
        for (DungeonItem item : template.getItems()) {
            items.add(transform(item).translate(center));
        }
        return List.copyOf(items);
    }

    public DungeonOpening getWorldOpening(int openingIndex) {
        return transform(template.getOpenings().get(openingIndex)).translate(center);
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

    private DungeonLine transform(DungeonLine line) {
        DungeonLine transformed = mirroredVertically ? line.mirrorVertically() : line;
        return transformed.rotateClockwise(rotationDegrees);
    }

    private DungeonOpening transform(DungeonOpening opening) {
        DungeonOpening transformed = mirroredVertically ? opening.mirrorVertically() : opening;
        return transformed.rotateClockwise(rotationDegrees);
    }

    private DungeonOccupiedArea transform(DungeonOccupiedArea area) {
        DungeonOccupiedArea transformed = mirroredVertically ? area.mirrorVertically() : area;
        return transformed.rotateClockwise(rotationDegrees);
    }

    private DungeonItem transform(DungeonItem item) {
        boolean cellBased = DungeonItemLibrary.find(item.id())
                .map(DungeonItemDefinition::isCellBased)
                .orElse(true);
        DungeonItem transformed = mirroredVertically
                ? (cellBased ? item.mirrorCellVertically() : item.mirrorVertically())
                : item;
        return cellBased
                ? transformed.rotateCellClockwise(rotationDegrees)
                : transformed.rotateClockwise(rotationDegrees);
    }
}
