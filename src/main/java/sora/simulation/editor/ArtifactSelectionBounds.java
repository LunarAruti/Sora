package sora.simulation.editor;

import java.util.List;

record ArtifactSelectionBounds(int minX, int minY, int maxX, int maxY) {
    static ArtifactSelectionBounds fromPoints(ArtifactPoint a, ArtifactPoint b) {
        return new ArtifactSelectionBounds(
                Math.min(a.x(), b.x()),
                Math.min(a.y(), b.y()),
                Math.max(a.x(), b.x()),
                Math.max(a.y(), b.y())
        );
    }

    boolean contains(ArtifactPoint point) {
        return point != null &&
                point.x() >= minX &&
                point.x() <= maxX &&
                point.y() >= minY &&
                point.y() <= maxY;
    }

    boolean containsCell(ArtifactPoint cell) {
        return cell != null &&
                cell.x() >= minX &&
                cell.x() < maxX &&
                cell.y() >= minY &&
                cell.y() < maxY;
    }

    boolean intersects(ArtifactWall wall) {
        if (wall == null) {
            return false;
        }
        if (contains(wall.start()) || contains(wall.end())) {
            return true;
        }
        ArtifactPoint nw = new ArtifactPoint(minX, minY);
        ArtifactPoint ne = new ArtifactPoint(maxX, minY);
        ArtifactPoint se = new ArtifactPoint(maxX, maxY);
        ArtifactPoint sw = new ArtifactPoint(minX, maxY);
        return ArtifactEditorGeometry.segmentsIntersect(wall.start(), wall.end(), nw, ne) ||
                ArtifactEditorGeometry.segmentsIntersect(wall.start(), wall.end(), ne, se) ||
                ArtifactEditorGeometry.segmentsIntersect(wall.start(), wall.end(), se, sw) ||
                ArtifactEditorGeometry.segmentsIntersect(wall.start(), wall.end(), sw, nw);
    }

    boolean intersectsPolygon(List<ArtifactPoint> points) {
        if (points == null || points.isEmpty()) {
            return false;
        }
        for (ArtifactPoint point : points) {
            if (contains(point)) {
                return true;
            }
        }
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            if (intersects(new ArtifactWall(points.get(j), points.get(i)))) {
                return true;
            }
        }
        return ArtifactEditorGeometry.containsPoint(points, new ArtifactPoint((minX + maxX) / 2, (minY + maxY) / 2));
    }

    ArtifactPoint center() {
        return new ArtifactPoint((minX + maxX) / 2, (minY + maxY) / 2);
    }
}
