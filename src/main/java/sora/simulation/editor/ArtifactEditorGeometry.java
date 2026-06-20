package sora.simulation.editor;

import java.util.List;

final class ArtifactEditorGeometry {
    private ArtifactEditorGeometry() {}

    private static final double LINE_TOUCH_TOLERANCE_SQUARED = 0.25;

    static ArtifactPoint relative(ArtifactPoint point, ArtifactPoint center) {
        if (point == null || center == null) {
            return new ArtifactPoint(0, 0);
        }
        return new ArtifactPoint(point.x() - center.x(), point.y() - center.y());
    }

    static int distanceSquared(ArtifactPoint a, ArtifactPoint b) {
        int dx = a.x() - b.x();
        int dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    static boolean touchesLine(ArtifactPoint point, ArtifactPoint start, ArtifactPoint end) {
        return distanceSquaredToSegment(point, start, end) <= LINE_TOUCH_TOLERANCE_SQUARED;
    }

    static boolean containsPoint(List<ArtifactPoint> polygon, ArtifactPoint point) {
        if (polygon == null || polygon.size() < 3 || point == null) {
            return false;
        }

        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            if (touchesLine(point, polygon.get(j), polygon.get(i))) {
                return true;
            }
        }

        boolean inside = false;
        double x = point.x();
        double y = point.y();
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            ArtifactPoint a = polygon.get(i);
            ArtifactPoint b = polygon.get(j);
            boolean crossesY = (a.y() > y) != (b.y() > y);
            if (crossesY) {
                double intersectX = (double) (b.x() - a.x()) * (y - a.y()) / (double) (b.y() - a.y()) + a.x();
                if (x < intersectX) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static double distanceSquaredToSegment(ArtifactPoint point, ArtifactPoint start, ArtifactPoint end) {
        if (point == null || start == null || end == null) {
            return Double.MAX_VALUE;
        }

        double vx = end.x() - start.x();
        double vy = end.y() - start.y();
        double wx = point.x() - start.x();
        double wy = point.y() - start.y();
        double lengthSquared = vx * vx + vy * vy;
        if (lengthSquared == 0.0) {
            return distanceSquared(point, start);
        }

        double t = (wx * vx + wy * vy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = start.x() + t * vx;
        double closestY = start.y() + t * vy;
        double dx = point.x() - closestX;
        double dy = point.y() - closestY;
        return dx * dx + dy * dy;
    }

    static boolean segmentsIntersect(ArtifactPoint a, ArtifactPoint b, ArtifactPoint c, ArtifactPoint d) {
        if (a == null || b == null || c == null || d == null) {
            return false;
        }

        int o1 = orientation(a, b, c);
        int o2 = orientation(a, b, d);
        int o3 = orientation(c, d, a);
        int o4 = orientation(c, d, b);

        if (o1 != o2 && o3 != o4) {
            return true;
        }
        return o1 == 0 && onSegment(a, c, b) ||
                o2 == 0 && onSegment(a, d, b) ||
                o3 == 0 && onSegment(c, a, d) ||
                o4 == 0 && onSegment(c, b, d);
    }

    static ArtifactDirection wallDirectionForTouchedCell(ArtifactPoint cell, List<ArtifactWall> walls) {
        return wallDirectionForTouchedCell(cell, walls, false);
    }

    static ArtifactDirection flatWallDirectionForTouchedCell(ArtifactPoint cell, List<ArtifactWall> walls) {
        return wallDirectionForTouchedCell(cell, walls, true);
    }

    private static ArtifactDirection wallDirectionForTouchedCell(
            ArtifactPoint cell,
            List<ArtifactWall> walls,
            boolean requireFlatWall
    ) {
        if (cell == null || walls == null || walls.isEmpty()) {
            return null;
        }

        double centerX = cell.x() + 0.5;
        double centerY = cell.y() + 0.5;
        ClosestWallPoint closest = null;
        for (ArtifactWall wall : walls) {
            if (requireFlatWall && !isFlatWall(wall)) {
                continue;
            }
            if (!segmentTouchesCell(wall.start(), wall.end(), cell)) {
                continue;
            }
            ClosestWallPoint candidate = closestPointOnSegment(centerX, centerY, wall.start(), wall.end());
            if (closest == null || candidate.distanceSquared() < closest.distanceSquared()) {
                closest = candidate;
            }
        }

        if (closest == null) {
            return null;
        }

        double dx = centerX - closest.x();
        double dy = centerY - closest.y();
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0.0 ? ArtifactDirection.EAST : ArtifactDirection.WEST;
        }
        return dy >= 0.0 ? ArtifactDirection.SOUTH : ArtifactDirection.NORTH;
    }

    private static boolean isFlatWall(ArtifactWall wall) {
        return wall != null &&
                wall.start() != null &&
                wall.end() != null &&
                (wall.start().x() == wall.end().x() || wall.start().y() == wall.end().y());
    }

    private static boolean segmentTouchesCell(ArtifactPoint start, ArtifactPoint end, ArtifactPoint cell) {
        if (start == null || end == null || cell == null) {
            return false;
        }

        double minX = cell.x();
        double minY = cell.y();
        double maxX = cell.x() + 1.0;
        double maxY = cell.y() + 1.0;
        if (pointInsideCell(start, minX, minY, maxX, maxY) ||
                pointInsideCell(end, minX, minY, maxX, maxY)) {
            return true;
        }

        ArtifactPoint nw = new ArtifactPoint(cell.x(), cell.y());
        ArtifactPoint ne = new ArtifactPoint(cell.x() + 1, cell.y());
        ArtifactPoint se = new ArtifactPoint(cell.x() + 1, cell.y() + 1);
        ArtifactPoint sw = new ArtifactPoint(cell.x(), cell.y() + 1);
        return segmentsIntersect(start, end, nw, ne) ||
                segmentsIntersect(start, end, ne, se) ||
                segmentsIntersect(start, end, sw, se) ||
                segmentsIntersect(start, end, nw, sw);
    }

    private static boolean pointInsideCell(ArtifactPoint point, double minX, double minY, double maxX, double maxY) {
        return point.x() >= minX &&
                point.x() <= maxX &&
                point.y() >= minY &&
                point.y() <= maxY;
    }

    private static ClosestWallPoint closestPointOnSegment(
            double pointX,
            double pointY,
            ArtifactPoint start,
            ArtifactPoint end
    ) {
        double vx = end.x() - start.x();
        double vy = end.y() - start.y();
        double wx = pointX - start.x();
        double wy = pointY - start.y();
        double lengthSquared = vx * vx + vy * vy;
        if (lengthSquared == 0.0) {
            double dx = pointX - start.x();
            double dy = pointY - start.y();
            return new ClosestWallPoint(start.x(), start.y(), dx * dx + dy * dy);
        }

        double t = (wx * vx + wy * vy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = start.x() + t * vx;
        double closestY = start.y() + t * vy;
        double dx = pointX - closestX;
        double dy = pointY - closestY;
        return new ClosestWallPoint(closestX, closestY, dx * dx + dy * dy);
    }

    private record ClosestWallPoint(double x, double y, double distanceSquared) {}

    private static int orientation(ArtifactPoint a, ArtifactPoint b, ArtifactPoint c) {
        long value = (long) (b.y() - a.y()) * (c.x() - b.x()) -
                (long) (b.x() - a.x()) * (c.y() - b.y());
        if (value == 0L) return 0;
        return value > 0L ? 1 : 2;
    }

    private static boolean onSegment(ArtifactPoint a, ArtifactPoint b, ArtifactPoint c) {
        return b.x() <= Math.max(a.x(), c.x()) &&
                b.x() >= Math.min(a.x(), c.x()) &&
                b.y() <= Math.max(a.y(), c.y()) &&
                b.y() >= Math.min(a.y(), c.y());
    }
}
