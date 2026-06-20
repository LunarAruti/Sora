package sora.simulation.generation;

import java.util.ArrayList;
import java.util.List;

public final class DungeonArtifactMove {
    private final DungeonArtifactTemplate template;
    private final int entranceOpeningIndex;
    private final int exitOpeningIndex;
    private final int rotationDegrees;
    private final boolean mirroredVertically;
    private final DungeonDirection entranceDirection;
    private final DungeonDirection exitDirection;
    private final DungeonPoint exitOffsetFromEntrance;
    private final List<DungeonOccupiedArea> occupiedAreasFromEntrance;

    DungeonArtifactMove(
            DungeonArtifactTemplate template,
            int entranceOpeningIndex,
            int exitOpeningIndex,
            int rotationDegrees,
            boolean mirroredVertically
    ) {
        this.template = template;
        this.entranceOpeningIndex = entranceOpeningIndex;
        this.exitOpeningIndex = exitOpeningIndex;
        this.rotationDegrees = rotationDegrees;
        this.mirroredVertically = mirroredVertically;

        DungeonOpening entrance = transform(template.getOpenings().get(entranceOpeningIndex));
        this.entranceDirection = entrance.direction();
        if (hasExit()) {
            DungeonOpening exit = transform(template.getOpenings().get(exitOpeningIndex));
            this.exitDirection = exit.direction();
            this.exitOffsetFromEntrance = new DungeonPoint(
                    exit.position().x() - entrance.position().x(),
                    exit.position().y() - entrance.position().y()
            );
        } else {
            this.exitDirection = null;
            this.exitOffsetFromEntrance = new DungeonPoint(0, 0);
        }

        DungeonPoint entranceToOrigin = new DungeonPoint(-entrance.position().x(), -entrance.position().y());
        List<DungeonOccupiedArea> areas = new ArrayList<>();
        for (DungeonOccupiedArea area : template.getOccupiedAreas()) {
            areas.add(transform(area).translate(entranceToOrigin));
        }
        this.occupiedAreasFromEntrance = List.copyOf(areas);
    }

    public DungeonArtifactTemplate getTemplate() { return template; }
    public int getEntranceOpeningIndex() { return entranceOpeningIndex; }
    public int getExitOpeningIndex() { return exitOpeningIndex; }
    public int getRotationDegrees() { return rotationDegrees; }
    public boolean isMirroredVertically() { return mirroredVertically; }
    public DungeonDirection getEntranceDirection() { return entranceDirection; }
    public DungeonDirection getExitDirection() { return exitDirection; }
    public DungeonPoint getExitOffsetFromEntrance() { return exitOffsetFromEntrance; }
    public List<DungeonOccupiedArea> getOccupiedAreasFromEntrance() { return occupiedAreasFromEntrance; }

    public boolean hasExit() {
        return exitOpeningIndex >= 0;
    }

    public int getManhattanDistance() {
        return Math.abs(exitOffsetFromEntrance.x()) + Math.abs(exitOffsetFromEntrance.y());
    }

    public boolean startsFrom(DungeonDirection openDirection) {
        return entranceDirection == openDirection.opposite();
    }

    public DungeonPlacedArtifact toPlacement(int placementIndex, DungeonOpening sourceOpening) {
        DungeonOpening entrance = transform(template.getOpenings().get(entranceOpeningIndex));
        DungeonPoint center = new DungeonPoint(
                sourceOpening.position().x() - entrance.position().x(),
                sourceOpening.position().y() - entrance.position().y()
        );
        return new DungeonPlacedArtifact(placementIndex, template, center, rotationDegrees, mirroredVertically);
    }

    private DungeonOpening transform(DungeonOpening opening) {
        DungeonOpening transformed = mirroredVertically ? opening.mirrorVertically() : opening;
        return transformed.rotateClockwise(rotationDegrees);
    }

    private DungeonOccupiedArea transform(DungeonOccupiedArea area) {
        DungeonOccupiedArea transformed = mirroredVertically ? area.mirrorVertically() : area;
        return transformed.rotateClockwise(rotationDegrees);
    }
}
