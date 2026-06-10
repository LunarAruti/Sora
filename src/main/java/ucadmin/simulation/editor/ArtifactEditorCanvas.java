package ucadmin.simulation.editor;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

final class ArtifactEditorCanvas extends JPanel {
    private static final int MIN_CELL_SIZE = 2;
    private static final int MAX_CELL_SIZE = 40;
    private static final int POINT_RADIUS = 5;

    private final ArtifactEditorConfig config;
    private final ArtifactEditorState state;
    private double cellSize = 4.0;
    private double panX = 80.0;
    private double panY = 40.0;
    private ArtifactPoint previewStart;
    private ArtifactPoint previewEnd;
    private List<ArtifactPoint> polygonPreviewPoints = List.of();
    private ArtifactPoint polygonPreviewCursor;

    ArtifactEditorCanvas(ArtifactEditorConfig config, ArtifactEditorState state) {
        this.config = config;
        this.state = state;
        setBackground(config.getBackgroundColor());
        setFocusable(true);
    }

    ArtifactPoint screenToGrid(int screenX, int screenY) {
        int x = (int) Math.round((screenX - panX) / cellSize);
        int y = (int) Math.round((screenY - panY) / cellSize);
        x = Math.max(0, Math.min(config.getCanvasCellsWide() - 1, x));
        y = Math.max(0, Math.min(config.getCanvasCellsHigh() - 1, y));
        return new ArtifactPoint(x, y);
    }

    void panBy(int dx, int dy) {
        panX += dx;
        panY += dy;
    }

    void zoomAt(int wheelRotation, int screenX, int screenY) {
        double oldSize = cellSize;
        double scale = wheelRotation < 0 ? 1.12 : 0.88;
        cellSize = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, cellSize * scale));
        double gridX = (screenX - panX) / oldSize;
        double gridY = (screenY - panY) / oldSize;
        panX = screenX - gridX * cellSize;
        panY = screenY - gridY * cellSize;
    }

    void setPreview(ArtifactPoint start, ArtifactPoint end) {
        previewStart = start;
        previewEnd = end;
    }

    void clearPreview() {
        previewStart = null;
        previewEnd = null;
    }

    void setPolygonPreview(List<ArtifactPoint> points, ArtifactPoint cursor) {
        polygonPreviewPoints = points == null ? List.of() : List.copyOf(points);
        polygonPreviewCursor = cursor;
    }

    void clearPolygonPreview() {
        polygonPreviewPoints = List.of();
        polygonPreviewCursor = null;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!(graphics instanceof Graphics2D g)) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawGrid(g);

        ArtifactEditorTool tool = state.getActiveTool();
        drawOccupied(g, tool == ArtifactEditorTool.OCCUPIED);
        drawWalls(g, tool == ArtifactEditorTool.WALL);
        drawOpenings(g, tool == ArtifactEditorTool.OPENING);
        drawCenter(g, tool == ArtifactEditorTool.CENTER);
        drawPreview(g);
        drawPolygonPreview(g);
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(config.getGridColor());
        g.setStroke(new BasicStroke(1f));
        int width = config.getCanvasCellsWide();
        int height = config.getCanvasCellsHigh();
        for (int x = 0; x <= width; x++) {
            int sx = gridToScreenX(x);
            g.drawLine(sx, gridToScreenY(0), sx, gridToScreenY(height));
        }
        for (int y = 0; y <= height; y++) {
            int sy = gridToScreenY(y);
            g.drawLine(gridToScreenX(0), sy, gridToScreenX(width), sy);
        }
    }

    private void drawWalls(Graphics2D g, boolean active) {
        setLayerAlpha(g, active);
        g.setColor(new Color(225, 225, 225));
        g.setStroke(new BasicStroke(5f));
        for (ArtifactWall wall : state.getDraft().getWalls()) {
            ArtifactPoint start = displayPoint(wall.start());
            ArtifactPoint end = displayPoint(wall.end());
            g.drawLine(
                    gridToScreenX(start.x()),
                    gridToScreenY(start.y()),
                    gridToScreenX(end.x()),
                    gridToScreenY(end.y())
            );
        }
        resetAlpha(g);
    }

    private void drawOccupied(Graphics2D g, boolean active) {
        setLayerAlpha(g, active);
        g.setColor(new Color(70, 150, 95, active ? 120 : 70));
        for (ArtifactOccupiedArea area : state.getDraft().getOccupiedAreas()) {
            drawPolygon(g, displayPoints(area.points()), true);
        }
        g.setColor(new Color(86, 210, 125));
        g.setStroke(new BasicStroke(2f));
        for (ArtifactOccupiedArea area : state.getDraft().getOccupiedAreas()) {
            drawPolygon(g, displayPoints(area.points()), false);
        }
        resetAlpha(g);
    }

    private void drawOpenings(Graphics2D g, boolean active) {
        setLayerAlpha(g, active);
        g.setColor(new Color(90, 170, 255));
        g.setStroke(new BasicStroke(2f));
        for (ArtifactOpening opening : state.getDraft().getOpenings()) {
            ArtifactPoint position = displayPoint(opening.position());
            int x = gridToScreenX(position.x());
            int y = gridToScreenY(position.y());
            g.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
            drawDirectionArrow(g, x, y, rotateDirection(opening.direction()));
        }
        resetAlpha(g);
    }

    private void drawPolygonPreview(Graphics2D g) {
        if (polygonPreviewPoints.isEmpty()) return;

        List<ArtifactPoint> points = new ArrayList<>(polygonPreviewPoints);
        if (polygonPreviewCursor != null) {
            points.add(polygonPreviewCursor);
        }

        g.setComposite(AlphaComposite.SrcOver.derive(0.6f));
        g.setColor(new Color(86, 210, 125));
        g.setStroke(new BasicStroke(2f));
        drawPolyline(g, points);

        ArtifactPoint first = polygonPreviewPoints.get(0);
        int x = gridToScreenX(first.x());
        int y = gridToScreenY(first.y());
        g.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
        resetAlpha(g);
    }

    private void drawCenter(Graphics2D g, boolean active) {
        ArtifactPoint center = state.getDraft().getCenter();
        if (center == null) return;
        setLayerAlpha(g, active);
        int x = gridToScreenX(center.x());
        int y = gridToScreenY(center.y());
        g.setColor(new Color(255, 210, 80));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x - 8, y, x + 8, y);
        g.drawLine(x, y - 8, x, y + 8);
        g.drawOval(x - 7, y - 7, 14, 14);
        resetAlpha(g);
    }

    private void drawPreview(Graphics2D g) {
        if (previewStart == null || previewEnd == null) return;
        ArtifactEditorTool tool = state.getActiveTool();
        if (tool != ArtifactEditorTool.WALL &&
                tool != ArtifactEditorTool.OCCUPIED &&
                tool != ArtifactEditorTool.OPENING) return;

        g.setComposite(AlphaComposite.SrcOver.derive(0.55f));
        g.setColor(tool == ArtifactEditorTool.WALL ? Color.WHITE : new Color(86, 210, 125));
        g.setStroke(new BasicStroke(2f));
        if (tool == ArtifactEditorTool.WALL) {
            g.drawLine(
                    gridToScreenX(previewStart.x()),
                    gridToScreenY(previewStart.y()),
                    gridToScreenX(previewEnd.x()),
                    gridToScreenY(previewEnd.y())
            );
        } else if (tool == ArtifactEditorTool.OCCUPIED) {
            drawRect(g, previewStart, previewEnd, false);
        } else {
            int x = gridToScreenX(previewStart.x());
            int y = gridToScreenY(previewStart.y());
            int tx = gridToScreenX(previewEnd.x());
            int ty = gridToScreenY(previewEnd.y());
            ArtifactDirection direction = previewDirection(previewStart, previewEnd);
            g.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
            drawDirectionArrow(g, x, y, direction);
            g.drawLine(x, y, tx, ty);
        }
        resetAlpha(g);
    }

    private ArtifactDirection previewDirection(ArtifactPoint start, ArtifactPoint end) {
        int dx = end.x() - start.x();
        int dy = end.y() - start.y();
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx >= 0 ? ArtifactDirection.EAST : ArtifactDirection.WEST;
        }
        return dy >= 0 ? ArtifactDirection.SOUTH : ArtifactDirection.NORTH;
    }

    private void drawDirectionArrow(Graphics2D g, int x, int y, ArtifactDirection direction) {
        int dx = 0;
        int dy = 0;
        switch (direction) {
            case NORTH -> dy = -14;
            case EAST -> dx = 14;
            case SOUTH -> dy = 14;
            case WEST -> dx = -14;
        }
        g.drawLine(x, y, x + dx, y + dy);
    }

    private void drawRect(Graphics2D g, ArtifactPoint a, ArtifactPoint b, boolean fill) {
        int x1 = gridToScreenX(Math.min(a.x(), b.x()));
        int y1 = gridToScreenY(Math.min(a.y(), b.y()));
        int x2 = gridToScreenX(Math.max(a.x(), b.x()));
        int y2 = gridToScreenY(Math.max(a.y(), b.y()));
        int w = Math.max(1, x2 - x1);
        int h = Math.max(1, y2 - y1);
        if (fill) {
            g.fillRect(x1, y1, w, h);
        } else {
            g.drawRect(x1, y1, w, h);
        }
    }

    private void drawPolygon(Graphics2D g, List<ArtifactPoint> points, boolean fill) {
        if (points == null || points.size() < 3) {
            return;
        }

        Polygon polygon = new Polygon();
        for (ArtifactPoint point : points) {
            polygon.addPoint(gridToScreenX(point.x()), gridToScreenY(point.y()));
        }

        if (fill) {
            g.fillPolygon(polygon);
        } else {
            g.drawPolygon(polygon);
        }
    }

    private void drawPolyline(Graphics2D g, List<ArtifactPoint> points) {
        if (points == null || points.size() < 2) {
            return;
        }
        for (int i = 1; i < points.size(); i++) {
            ArtifactPoint previous = points.get(i - 1);
            ArtifactPoint current = points.get(i);
            g.drawLine(
                    gridToScreenX(previous.x()),
                    gridToScreenY(previous.y()),
                    gridToScreenX(current.x()),
                    gridToScreenY(current.y())
            );
        }
    }

    private List<ArtifactPoint> displayPoints(List<ArtifactPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }

        List<ArtifactPoint> out = new ArrayList<>(points.size());
        for (ArtifactPoint point : points) {
            out.add(displayPoint(point));
        }
        return out;
    }

    private ArtifactPoint displayPoint(ArtifactPoint point) {
        ArtifactPoint center = state.getDraft().getCenter();
        int turns = state.getRotationPreviewTurns();
        if (point == null || center == null || turns == 0) {
            return point;
        }

        int dx = point.x() - center.x();
        int dy = point.y() - center.y();
        return switch (turns) {
            case 1 -> new ArtifactPoint(center.x() - dy, center.y() + dx);
            case 2 -> new ArtifactPoint(center.x() - dx, center.y() - dy);
            case 3 -> new ArtifactPoint(center.x() + dy, center.y() - dx);
            default -> point;
        };
    }

    private ArtifactDirection rotateDirection(ArtifactDirection direction) {
        int turns = state.getRotationPreviewTurns();
        if (turns == 0) {
            return direction;
        }
        ArtifactDirection[] order = {
                ArtifactDirection.NORTH,
                ArtifactDirection.EAST,
                ArtifactDirection.SOUTH,
                ArtifactDirection.WEST
        };
        int idx = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i] == direction) {
                idx = i;
                break;
            }
        }
        return order[(idx + turns) % order.length];
    }

    private int gridToScreenX(int gridX) {
        return (int) Math.round(panX + gridX * cellSize);
    }

    private int gridToScreenY(int gridY) {
        return (int) Math.round(panY + gridY * cellSize);
    }

    private void setLayerAlpha(Graphics2D g, boolean active) {
        g.setComposite(AlphaComposite.SrcOver.derive(active ? 1.0f : 0.28f));
    }

    private void resetAlpha(Graphics2D g) {
        g.setComposite(AlphaComposite.SrcOver);
    }
}
