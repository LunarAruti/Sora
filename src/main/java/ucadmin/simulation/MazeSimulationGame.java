package ucadmin.simulation;

import ucadmin.simulation.generation.DungeonGenerationConfig;
import ucadmin.simulation.generation.DungeonGenerationService;
import ucadmin.simulation.generation.DungeonLine;
import ucadmin.simulation.generation.DungeonLoadedArea;
import ucadmin.simulation.generation.DungeonOccupiedArea;
import ucadmin.simulation.generation.DungeonOpening;
import ucadmin.simulation.generation.DungeonPlacedArtifact;
import ucadmin.simulation.generation.DungeonRect;
import ucadmin.util.Logger;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.security.SecureRandom;

/**
 * Dungeon simulation game object.
 */
public final class MazeSimulationGame implements SimulationGame {
    private static final int SCREEN_SIZE = 1000;
    private static final double VISIBLE_BLOCKS_ACROSS = 45.0;
    private static final int VIEW_BUFFER = 10;
    private static final double PLAYER_SPEED = 5.0;
    private static final double PLAYER_SHIFT_SPEED = 8.0;
    private static final double PLAYER_SIZE = 2.0;
    private static final float WALL_STROKE_PIXELS = 6.0f;
    private static final float SEALED_WALL_STROKE_PIXELS = 8.0f;
    private static final float OCCUPIED_FILL_STROKE_PIXELS = 3.0f;
    private static final int OPENING_NODE_RADIUS_PIXELS = 5;
    private static final Font ARTIFACT_LABEL_FONT = new Font("SansSerif", Font.BOLD, 12);
    private static final SecureRandom SEED_SOURCE = new SecureRandom();

    private final long seed;
    private final SimulationEngine engine;
    private final DungeonGenerationService generationService = new DungeonGenerationService();
    private final DungeonGenerationConfig generationConfig = DungeonGenerationConfig.defaultConfig();
    private DungeonLoadedArea loadedArea;
    private double cameraX;
    private double cameraY;
    private double playerX;
    private double playerY;
    private boolean moveUp;
    private boolean moveLeft;
    private boolean moveDown;
    private boolean moveRight;
    private boolean shiftHeld;

    public MazeSimulationGame() {
        this(SEED_SOURCE.nextLong());
    }

    public MazeSimulationGame(long seed) {
        this.seed = seed;
        this.engine = new SimulationEngine(defaultConfig(), this);
    }

    public boolean run() {
        return engine.run();
    }

    public void exit() {
        engine.exit();
    }

    public boolean isRunning() {
        return engine.isRunning();
    }

    public SimulationEngine getEngine() {
        return engine;
    }

    public long getSeed() {
        return seed;
    }

    @Override
    public void onStart(SimulationContext context) {
        playerX = 0.0;
        playerY = 0.0;
        centerCameraOnPlayer();
        loadVisibleArea(context);
        Logger.log(Logger.TAG.INFO, "MazeSimulationGame: onStart seed=" + seed
                + " loadedPlacements=" + loadedArea.getPlacements().size());
    }

    @Override
    public void update(SimulationContext context, double deltaSeconds) {
        movePlayer(context, deltaSeconds);
        centerCameraOnPlayer();
        loadVisibleArea(context);
    }

    @Override
    public void render(SimulationContext context, Graphics2D graphics) {
        if (loadedArea == null) {
            return;
        }

        DungeonRect view = visibleWorldArea(context);
        ViewTransform transform = ViewTransform.from(context, cameraX, cameraY, pixelsPerBlock(context));
        drawOccupiedAreas(graphics, view, transform);

        graphics.setStroke(new BasicStroke(WALL_STROKE_PIXELS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(212, 214, 218));
        for (DungeonLine wall : loadedArea.getWallsIntersecting(view)) {
            drawLine(graphics, transform, wall);
        }

        graphics.setStroke(new BasicStroke(SEALED_WALL_STROKE_PIXELS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(238, 190, 92));
        for (DungeonLine wall : loadedArea.getSealedOpeningWallsIntersecting(view)) {
            drawLine(graphics, transform, wall);
        }

        drawOpeningNodes(graphics, view, transform);
        drawArtifactLabels(graphics, view, transform);
        drawPlayer(graphics, transform);
    }

    @Override
    public void onKeyPressed(SimulationContext context, int keyCode) {
        setMovementKey(keyCode, true);
    }

    @Override
    public void onKeyReleased(SimulationContext context, int keyCode) {
        setMovementKey(keyCode, false);
    }

    @Override
    public void onStop(SimulationContext context) {
        loadedArea = null;
        generationService.clear();
        moveUp = false;
        moveLeft = false;
        moveDown = false;
        moveRight = false;
        shiftHeld = false;
        Logger.log(Logger.TAG.INFO, "MazeSimulationGame: onStop.");
    }

    private void setMovementKey(int keyCode, boolean pressed) {
        switch (keyCode) {
            case KeyEvent.VK_W -> moveUp = pressed;
            case KeyEvent.VK_A -> moveLeft = pressed;
            case KeyEvent.VK_S -> moveDown = pressed;
            case KeyEvent.VK_D -> moveRight = pressed;
            case KeyEvent.VK_SHIFT -> shiftHeld = pressed;
            default -> {
                // Ignore non-movement keys for now.
            }
        }
    }

    private void movePlayer(SimulationContext context, double deltaSeconds) {
        double dx = 0.0;
        double dy = 0.0;
        if (moveUp) dy -= 1.0;
        if (moveDown) dy += 1.0;
        if (moveLeft) dx -= 1.0;
        if (moveRight) dx += 1.0;
        if (dx == 0.0 && dy == 0.0) {
            return;
        }

        double length = Math.sqrt(dx * dx + dy * dy);
        double distance = (shiftHeld ? PLAYER_SHIFT_SPEED : PLAYER_SPEED) * deltaSeconds;
        double stepX = dx / length * distance;
        double stepY = dy / length * distance;

        loadAreaForPlayerCollision(context, stepX, stepY);
        if (!collidesAt(playerX + stepX, playerY)) {
            playerX += stepX;
        }
        if (!collidesAt(playerX, playerY + stepY)) {
            playerY += stepY;
        }
    }

    private void loadAreaForPlayerCollision(SimulationContext context, double stepX, double stepY) {
        double oldCameraX = cameraX;
        double oldCameraY = cameraY;
        cameraX = playerX + stepX;
        cameraY = playerY + stepY;
        loadVisibleArea(context);
        cameraX = oldCameraX;
        cameraY = oldCameraY;
    }

    private boolean collidesAt(double centerX, double centerY) {
        if (loadedArea == null) {
            return false;
        }
        DungeonRect bounds = playerBounds(centerX, centerY);
        for (DungeonLine wall : loadedArea.getWallsIntersecting(bounds)) {
            if (lineIntersectsRect(wall, centerX, centerY, PLAYER_SIZE, PLAYER_SIZE)) {
                return true;
            }
        }
        for (DungeonLine wall : loadedArea.getSealedOpeningWallsIntersecting(bounds)) {
            if (lineIntersectsRect(wall, centerX, centerY, PLAYER_SIZE, PLAYER_SIZE)) {
                return true;
            }
        }
        return false;
    }

    private DungeonRect visibleWorldArea(SimulationContext context) {
        double pixelsPerBlock = pixelsPerBlock(context);
        int halfWidth = (int) Math.ceil(context.getConfig().getWidth() / (2.0 * pixelsPerBlock));
        int halfHeight = (int) Math.ceil(context.getConfig().getHeight() / (2.0 * pixelsPerBlock));
        int minX = (int) Math.floor(cameraX - halfWidth - VIEW_BUFFER);
        int minY = (int) Math.floor(cameraY - halfHeight - VIEW_BUFFER);
        int maxX = (int) Math.ceil(cameraX + halfWidth + VIEW_BUFFER);
        int maxY = (int) Math.ceil(cameraY + halfHeight + VIEW_BUFFER);
        return new DungeonRect(minX, minY, maxX, maxY);
    }

    private void drawLine(Graphics2D graphics, ViewTransform transform, DungeonLine line) {
        graphics.drawLine(
                transform.worldToScreenX(line.start().x()),
                transform.worldToScreenY(line.start().y()),
                transform.worldToScreenX(line.end().x()),
                transform.worldToScreenY(line.end().y())
        );
    }

    private void drawOccupiedAreas(Graphics2D graphics, DungeonRect view, ViewTransform transform) {
        Object oldAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setColor(new Color(42, 44, 48));
        graphics.setStroke(new BasicStroke(OCCUPIED_FILL_STROKE_PIXELS, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonOccupiedArea area : placement.getWorldOccupiedAreas()) {
                int[] xPoints = new int[area.getPoints().size()];
                int[] yPoints = new int[area.getPoints().size()];
                for (int i = 0; i < area.getPoints().size(); i++) {
                    xPoints[i] = transform.worldToScreenX(area.getPoints().get(i).x());
                    yPoints[i] = transform.worldToScreenY(area.getPoints().get(i).y());
                }
                graphics.fillPolygon(xPoints, yPoints, area.getPoints().size());
                graphics.drawPolygon(xPoints, yPoints, area.getPoints().size());
            }
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
    }

    private void drawOpeningNodes(Graphics2D graphics, DungeonRect view, ViewTransform transform) {
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (int i = 0; i < placement.getTemplate().getOpenings().size(); i++) {
                DungeonOpening opening = placement.getWorldOpening(i);
                int x = transform.worldToScreenX(opening.position().x());
                int y = transform.worldToScreenY(opening.position().y());
                graphics.setColor(placement.isOpeningConnected(i)
                        ? new Color(80, 185, 255)
                        : new Color(255, 120, 88));
                graphics.fillOval(
                        x - OPENING_NODE_RADIUS_PIXELS,
                        y - OPENING_NODE_RADIUS_PIXELS,
                        OPENING_NODE_RADIUS_PIXELS * 2,
                        OPENING_NODE_RADIUS_PIXELS * 2
                );
            }
        }
    }

    private void drawArtifactLabels(Graphics2D graphics, DungeonRect view, ViewTransform transform) {
        graphics.setFont(ARTIFACT_LABEL_FONT);
        FontMetrics metrics = graphics.getFontMetrics();
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            String name = placement.getTemplate().getName();
            int x = transform.worldToScreenX(placement.getCenter().x());
            int y = transform.worldToScreenY(placement.getCenter().y());
            int width = metrics.stringWidth(name);

            graphics.setColor(new Color(0, 0, 0, 170));
            graphics.fillRect(x - width / 2 - 3, y - metrics.getAscent(), width + 6, metrics.getHeight());
            graphics.setColor(new Color(235, 238, 242));
            graphics.drawString(name, x - width / 2, y);
        }
    }

    private void drawPlayer(Graphics2D graphics, ViewTransform transform) {
        int x1 = transform.worldToScreenX(playerX - PLAYER_SIZE / 2.0);
        int y1 = transform.worldToScreenY(playerY - PLAYER_SIZE / 2.0);
        int x2 = transform.worldToScreenX(playerX + PLAYER_SIZE / 2.0);
        int y2 = transform.worldToScreenY(playerY + PLAYER_SIZE / 2.0);
        graphics.setColor(new Color(158, 78, 255));
        graphics.fillRect(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.max(1, Math.abs(x2 - x1)),
                Math.max(1, Math.abs(y2 - y1))
        );
    }

    private double pixelsPerBlock(SimulationContext context) {
        return context.getConfig().getWidth() / VISIBLE_BLOCKS_ACROSS;
    }

    private void loadVisibleArea(SimulationContext context) {
        DungeonRect view = visibleWorldArea(context);
        if (loadedArea != null && loadedArea.getLoadedBounds().contains(view)) {
            return;
        }
        DungeonRect loadBounds = chunkAlignedLoadBounds(view);
        loadedArea = generationService.loadArea(seed, generationConfig, loadBounds);
        Logger.log(Logger.TAG.DEBUG, "MazeSimulationGame: loaded chunks bounds="
                + loadBounds.minX() + "," + loadBounds.minY() + " to "
                + loadBounds.maxX() + "," + loadBounds.maxY()
                + " placements=" + loadedArea.getPlacements().size());
    }

    private void centerCameraOnPlayer() {
        cameraX = playerX;
        cameraY = playerY;
    }

    private DungeonRect playerBounds(double centerX, double centerY) {
        double half = PLAYER_SIZE / 2.0;
        return new DungeonRect(
                (int) Math.floor(centerX - half - 1.0),
                (int) Math.floor(centerY - half - 1.0),
                (int) Math.ceil(centerX + half + 1.0),
                (int) Math.ceil(centerY + half + 1.0)
        );
    }

    private boolean lineIntersectsRect(DungeonLine line, double centerX, double centerY, double width, double height) {
        double minX = centerX - width / 2.0;
        double minY = centerY - height / 2.0;
        double maxX = centerX + width / 2.0;
        double maxY = centerY + height / 2.0;
        double x1 = line.start().x();
        double y1 = line.start().y();
        double x2 = line.end().x();
        double y2 = line.end().y();

        if (pointInRect(x1, y1, minX, minY, maxX, maxY) || pointInRect(x2, y2, minX, minY, maxX, maxY)) {
            return true;
        }
        return segmentsIntersect(x1, y1, x2, y2, minX, minY, maxX, minY) ||
                segmentsIntersect(x1, y1, x2, y2, maxX, minY, maxX, maxY) ||
                segmentsIntersect(x1, y1, x2, y2, maxX, maxY, minX, maxY) ||
                segmentsIntersect(x1, y1, x2, y2, minX, maxY, minX, minY);
    }

    private boolean pointInRect(double x, double y, double minX, double minY, double maxX, double maxY) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    private boolean segmentsIntersect(
            double ax,
            double ay,
            double bx,
            double by,
            double cx,
            double cy,
            double dx,
            double dy
    ) {
        double d1 = direction(cx, cy, dx, dy, ax, ay);
        double d2 = direction(cx, cy, dx, dy, bx, by);
        double d3 = direction(ax, ay, bx, by, cx, cy);
        double d4 = direction(ax, ay, bx, by, dx, dy);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private double direction(double ax, double ay, double bx, double by, double cx, double cy) {
        return (cx - ax) * (by - ay) - (cy - ay) * (bx - ax);
    }

    private DungeonRect chunkAlignedLoadBounds(DungeonRect view) {
        int chunkSize = generationConfig.getChunkSize();
        int minChunkX = Math.floorDiv(view.minX(), chunkSize) - 1;
        int minChunkY = Math.floorDiv(view.minY(), chunkSize) - 1;
        int maxChunkX = Math.floorDiv(view.maxX(), chunkSize) + 1;
        int maxChunkY = Math.floorDiv(view.maxY(), chunkSize) + 1;
        return new DungeonRect(
                minChunkX * chunkSize,
                minChunkY * chunkSize,
                (maxChunkX + 1) * chunkSize,
                (maxChunkY + 1) * chunkSize
        );
    }

    private static SimulationConfig defaultConfig() {
        return new SimulationConfig(
                "Dungeon Simulation",
                SCREEN_SIZE,
                SCREEN_SIZE,
                60,
                Color.BLACK,
                25L,
                true
        );
    }

    private record ViewTransform(double pixelsPerBlock, int originX, int originY) {
        static ViewTransform from(
                SimulationContext context,
                double cameraX,
                double cameraY,
                double pixelsPerBlock
        ) {
            int originX = (int) Math.round(context.getConfig().getWidth() / 2.0 - cameraX * pixelsPerBlock);
            int originY = (int) Math.round(context.getConfig().getHeight() / 2.0 - cameraY * pixelsPerBlock);
            return new ViewTransform(pixelsPerBlock, originX, originY);
        }

        int worldToScreenX(int worldX) {
            return (int) Math.round(originX + worldX * pixelsPerBlock);
        }

        int worldToScreenY(int worldY) {
            return (int) Math.round(originY + worldY * pixelsPerBlock);
        }

        int worldToScreenX(double worldX) {
            return (int) Math.round(originX + worldX * pixelsPerBlock);
        }

        int worldToScreenY(double worldY) {
            return (int) Math.round(originY + worldY * pixelsPerBlock);
        }
    }
}
