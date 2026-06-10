package ucadmin.simulation.generation;

public enum DungeonDirection {
    NORTH,
    EAST,
    SOUTH,
    WEST;

    public DungeonDirection opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case SOUTH -> NORTH;
            case WEST -> EAST;
        };
    }

    public DungeonDirection rotateClockwise(int rotationDegrees) {
        int turns = Math.floorMod(rotationDegrees, 360) / 90;
        DungeonDirection direction = this;
        for (int i = 0; i < turns; i++) {
            direction = switch (direction) {
                case NORTH -> EAST;
                case EAST -> SOUTH;
                case SOUTH -> WEST;
                case WEST -> NORTH;
            };
        }
        return direction;
    }
}
