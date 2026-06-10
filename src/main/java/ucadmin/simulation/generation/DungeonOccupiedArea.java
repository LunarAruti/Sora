package ucadmin.simulation.generation;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DungeonOccupiedArea {
    private final List<DungeonPoint> points;
    private final DungeonRect bounds;

    public DungeonOccupiedArea(List<DungeonPoint> points) {
        if (points == null || points.size() < 3) {
            throw new IllegalArgumentException("Occupied area polygon must have at least three points.");
        }
        this.points = List.copyOf(points);
        this.bounds = calculateBounds(this.points);
    }

    public static DungeonOccupiedArea fromRect(DungeonRect rect) {
        return new DungeonOccupiedArea(List.of(
                new DungeonPoint(rect.minX(), rect.minY()),
                new DungeonPoint(rect.maxX(), rect.minY()),
                new DungeonPoint(rect.maxX(), rect.maxY()),
                new DungeonPoint(rect.minX(), rect.maxY())
        ));
    }

    public List<DungeonPoint> getPoints() { return points; }
    public DungeonRect getBounds() { return bounds; }

    public DungeonOccupiedArea rotateClockwise(int rotationDegrees) {
        List<DungeonPoint> rotated = new ArrayList<>();
        for (DungeonPoint point : points) {
            rotated.add(point.rotateClockwise(rotationDegrees));
        }
        return new DungeonOccupiedArea(rotated);
    }

    public DungeonOccupiedArea mirrorVertically() {
        List<DungeonPoint> mirrored = new ArrayList<>();
        for (DungeonPoint point : points) {
            mirrored.add(point.mirrorVertically());
        }
        return new DungeonOccupiedArea(mirrored);
    }

    public DungeonOccupiedArea translate(DungeonPoint offset) {
        List<DungeonPoint> translated = new ArrayList<>();
        for (DungeonPoint point : points) {
            translated.add(point.translate(offset));
        }
        return new DungeonOccupiedArea(translated);
    }

    public boolean intersects(DungeonOccupiedArea other) {
        if (other == null || !bounds.intersects(other.bounds)) {
            return false;
        }
        Area intersection = toArea();
        intersection.intersect(other.toArea());
        return !intersection.isEmpty();
    }

    public boolean intersects(DungeonRect area) {
        return area != null && bounds.intersects(area);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DungeonOccupiedArea area)) return false;
        return points.equals(area.points);
    }

    @Override
    public int hashCode() {
        return Objects.hash(points);
    }

    private Area toArea() {
        Path2D.Double path = new Path2D.Double();
        DungeonPoint first = points.get(0);
        path.moveTo(first.x(), first.y());
        for (int i = 1; i < points.size(); i++) {
            DungeonPoint point = points.get(i);
            path.lineTo(point.x(), point.y());
        }
        path.closePath();
        return new Area(path);
    }

    private static DungeonRect calculateBounds(List<DungeonPoint> points) {
        int minX = points.get(0).x();
        int minY = points.get(0).y();
        int maxX = minX;
        int maxY = minY;
        for (DungeonPoint point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        return new DungeonRect(minX, minY, maxX, maxY);
    }

}
