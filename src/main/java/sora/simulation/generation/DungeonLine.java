package sora.simulation.generation;

public record DungeonLine(DungeonPoint start, DungeonPoint end) {
    public DungeonLine rotateClockwise(int rotationDegrees) {
        return new DungeonLine(
                start.rotateClockwise(rotationDegrees),
                end.rotateClockwise(rotationDegrees)
        );
    }

    public DungeonLine mirrorVertically() {
        return new DungeonLine(start.mirrorVertically(), end.mirrorVertically());
    }

    public DungeonLine translate(DungeonPoint offset) {
        return new DungeonLine(start.translate(offset), end.translate(offset));
    }
}
