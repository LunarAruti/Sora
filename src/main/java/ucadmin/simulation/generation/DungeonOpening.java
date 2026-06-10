package ucadmin.simulation.generation;

public record DungeonOpening(DungeonPoint position, DungeonDirection direction, int width) {
    private static final int SEALED_WALL_LENGTH = 6;

    public DungeonOpening {
        if (position == null) {
            throw new IllegalArgumentException("Opening position cannot be null.");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Opening direction cannot be null.");
        }
        if (width < 1) {
            throw new IllegalArgumentException("Opening width must be positive.");
        }
    }

    public DungeonOpening rotateClockwise(int rotationDegrees) {
        return new DungeonOpening(
                position.rotateClockwise(rotationDegrees),
                direction.rotateClockwise(rotationDegrees),
                width
        );
    }

    public DungeonOpening mirrorVertically() {
        return new DungeonOpening(
                position.mirrorVertically(),
                direction.mirrorVertically(),
                width
        );
    }

    public DungeonOpening translate(DungeonPoint offset) {
        return new DungeonOpening(position.translate(offset), direction, width);
    }

    public DungeonLine sealedWall() {
        int half = SEALED_WALL_LENGTH / 2;
        return switch (direction) {
            case NORTH, SOUTH -> new DungeonLine(
                    new DungeonPoint(position.x() - half, position.y()),
                    new DungeonPoint(position.x() + half, position.y())
            );
            case EAST, WEST -> new DungeonLine(
                    new DungeonPoint(position.x(), position.y() - half),
                    new DungeonPoint(position.x(), position.y() + half)
            );
        };
    }
}
