package sora.simulation.generation;

public record DungeonRect(int minX, int minY, int maxX, int maxY) {
    public DungeonRect {
        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("DungeonRect max values must be >= min values.");
        }
    }

    public static DungeonRect fromPoints(DungeonPoint a, DungeonPoint b) {
        return new DungeonRect(
                Math.min(a.x(), b.x()),
                Math.min(a.y(), b.y()),
                Math.max(a.x(), b.x()),
                Math.max(a.y(), b.y())
        );
    }

    public DungeonRect rotateClockwise(int rotationDegrees) {
        DungeonPoint a = new DungeonPoint(minX, minY).rotateClockwise(rotationDegrees);
        DungeonPoint b = new DungeonPoint(maxX, minY).rotateClockwise(rotationDegrees);
        DungeonPoint c = new DungeonPoint(maxX, maxY).rotateClockwise(rotationDegrees);
        DungeonPoint d = new DungeonPoint(minX, maxY).rotateClockwise(rotationDegrees);
        int newMinX = Math.min(Math.min(a.x(), b.x()), Math.min(c.x(), d.x()));
        int newMinY = Math.min(Math.min(a.y(), b.y()), Math.min(c.y(), d.y()));
        int newMaxX = Math.max(Math.max(a.x(), b.x()), Math.max(c.x(), d.x()));
        int newMaxY = Math.max(Math.max(a.y(), b.y()), Math.max(c.y(), d.y()));
        return new DungeonRect(newMinX, newMinY, newMaxX, newMaxY);
    }

    public DungeonRect translate(DungeonPoint offset) {
        return new DungeonRect(
                minX + offset.x(),
                minY + offset.y(),
                maxX + offset.x(),
                maxY + offset.y()
        );
    }

    public boolean intersects(DungeonRect other) {
        return other != null &&
                minX < other.maxX &&
                maxX > other.minX &&
                minY < other.maxY &&
                maxY > other.minY;
    }

    public boolean contains(DungeonRect other) {
        return other != null &&
                other.minX >= minX &&
                other.maxX <= maxX &&
                other.minY >= minY &&
                other.maxY <= maxY;
    }
}
