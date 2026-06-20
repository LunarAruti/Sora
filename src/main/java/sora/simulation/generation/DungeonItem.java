package sora.simulation.generation;

public record DungeonItem(String id, DungeonPoint position, DungeonDirection direction) {
    public DungeonItem {
        id = (id == null || id.isBlank()) ? "unknown" : id.trim();
        if (position == null) {
            position = new DungeonPoint(0, 0);
        }
        if (direction == null) {
            direction = DungeonDirection.NORTH;
        }
    }

    public DungeonItem rotateClockwise(int rotationDegrees) {
        return new DungeonItem(
                id,
                position.rotateClockwise(rotationDegrees),
                direction.rotateClockwise(rotationDegrees)
        );
    }

    public DungeonItem rotateCellClockwise(int rotationDegrees) {
        return new DungeonItem(
                id,
                rotateCellPositionClockwise(position, rotationDegrees),
                direction.rotateClockwise(rotationDegrees)
        );
    }

    public DungeonItem mirrorVertically() {
        return new DungeonItem(id, position.mirrorVertically(), direction.mirrorVertically());
    }

    public DungeonItem mirrorCellVertically() {
        return new DungeonItem(
                id,
                new DungeonPoint(-position.x() - 1, position.y()),
                direction.mirrorVertically()
        );
    }

    public DungeonItem translate(DungeonPoint offset) {
        return new DungeonItem(id, position.translate(offset), direction);
    }

    private static DungeonPoint rotateCellPositionClockwise(DungeonPoint position, int rotationDegrees) {
        int normalized = Math.floorMod(rotationDegrees, 360);
        return switch (normalized) {
            case 0 -> position;
            case 90 -> new DungeonPoint(-position.y() - 1, position.x());
            case 180 -> new DungeonPoint(-position.x() - 1, -position.y() - 1);
            case 270 -> new DungeonPoint(position.y(), -position.x() - 1);
            default -> throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270.");
        };
    }
}
