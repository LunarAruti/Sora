package sora.simulation.editor;

import java.util.ArrayList;
import java.util.List;

final class ArtifactSelection {
    private final ArtifactPoint center;
    private final List<ArtifactWall> walls;
    private final List<ArtifactOpening> openings;
    private final List<ArtifactOccupiedArea> occupiedAreas;
    private final List<ArtifactItem> items;

    ArtifactSelection(
            ArtifactPoint center,
            List<ArtifactWall> walls,
            List<ArtifactOpening> openings,
            List<ArtifactOccupiedArea> occupiedAreas,
            List<ArtifactItem> items
    ) {
        this.center = center;
        this.walls = walls == null ? List.of() : List.copyOf(walls);
        this.openings = openings == null ? List.of() : List.copyOf(openings);
        this.occupiedAreas = occupiedAreas == null ? List.of() : List.copyOf(occupiedAreas);
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    ArtifactPoint getCenter() { return center; }
    List<ArtifactWall> getWalls() { return walls; }
    List<ArtifactOpening> getOpenings() { return openings; }
    List<ArtifactOccupiedArea> getOccupiedAreas() { return occupiedAreas; }
    List<ArtifactItem> getItems() { return items; }

    boolean isEmpty() {
        return center == null &&
                walls.isEmpty() &&
                openings.isEmpty() &&
                occupiedAreas.isEmpty() &&
                items.isEmpty();
    }

    ArtifactSelection translateNear(ArtifactPoint target) {
        if (target == null || isEmpty()) {
            return this;
        }

        ArtifactPoint origin = boundsCenter();
        int dx = target.x() - origin.x();
        int dy = target.y() - origin.y();
        return translate(dx, dy);
    }

    ArtifactSelection translate(int dx, int dy) {
        ArtifactPoint translatedCenter = center == null ? null : new ArtifactPoint(center.x() + dx, center.y() + dy);

        List<ArtifactWall> translatedWalls = new ArrayList<>();
        for (ArtifactWall wall : walls) {
            translatedWalls.add(new ArtifactWall(
                    translate(wall.start(), dx, dy),
                    translate(wall.end(), dx, dy)
            ));
        }

        List<ArtifactOpening> translatedOpenings = new ArrayList<>();
        for (ArtifactOpening opening : openings) {
            translatedOpenings.add(new ArtifactOpening(
                    translate(opening.position(), dx, dy),
                    opening.direction(),
                    opening.width()
            ));
        }

        List<ArtifactOccupiedArea> translatedAreas = new ArrayList<>();
        for (ArtifactOccupiedArea area : occupiedAreas) {
            List<ArtifactPoint> points = new ArrayList<>();
            for (ArtifactPoint point : area.points()) {
                points.add(translate(point, dx, dy));
            }
            translatedAreas.add(new ArtifactOccupiedArea(points));
        }

        List<ArtifactItem> translatedItems = new ArrayList<>();
        for (ArtifactItem item : items) {
            translatedItems.add(item.translate(dx, dy));
        }

        return new ArtifactSelection(
                translatedCenter,
                translatedWalls,
                translatedOpenings,
                translatedAreas,
                translatedItems
        );
    }

    private ArtifactPoint boundsCenter() {
        ArtifactSelectionBounds bounds = bounds();
        return bounds == null ? new ArtifactPoint(0, 0) : bounds.center();
    }

    private ArtifactSelectionBounds bounds() {
        List<ArtifactPoint> points = new ArrayList<>();
        if (center != null) points.add(center);
        for (ArtifactWall wall : walls) {
            points.add(wall.start());
            points.add(wall.end());
        }
        for (ArtifactOpening opening : openings) {
            points.add(opening.position());
        }
        for (ArtifactOccupiedArea area : occupiedAreas) {
            points.addAll(area.points());
        }
        for (ArtifactItem item : items) {
            points.add(item.position());
        }
        if (points.isEmpty()) {
            return null;
        }

        int minX = points.get(0).x();
        int minY = points.get(0).y();
        int maxX = minX;
        int maxY = minY;
        for (ArtifactPoint point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        return new ArtifactSelectionBounds(minX, minY, maxX, maxY);
    }

    private static ArtifactPoint translate(ArtifactPoint point, int dx, int dy) {
        return new ArtifactPoint(point.x() + dx, point.y() + dy);
    }
}
