package ucadmin.simulation.generation;

public record DungeonPoint(int x, int y) {
    public DungeonPoint rotateClockwise(int rotationDegrees) {
        int normalized = Math.floorMod(rotationDegrees, 360);
        return switch (normalized) {
            case 0 -> this;
            case 90 -> new DungeonPoint(-y, x);
            case 180 -> new DungeonPoint(-x, -y);
            case 270 -> new DungeonPoint(y, -x);
            default -> throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270.");
        };
    }

    public DungeonPoint mirrorVertically() {
        return new DungeonPoint(-x, y);
    }

    public DungeonPoint translate(DungeonPoint offset) {
        return new DungeonPoint(x + offset.x, y + offset.y);
    }
}
