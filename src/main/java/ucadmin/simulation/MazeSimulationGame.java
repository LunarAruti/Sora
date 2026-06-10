package ucadmin.simulation;

import ucadmin.simulation.generation.DungeonGenerationConfig;
import ucadmin.simulation.generation.DungeonGenerationService;
import ucadmin.simulation.generation.DungeonDirection;
import ucadmin.simulation.generation.DungeonItem;
import ucadmin.simulation.generation.DungeonLine;
import ucadmin.simulation.generation.DungeonLoadedArea;
import ucadmin.simulation.generation.DungeonOccupiedArea;
import ucadmin.simulation.generation.DungeonOpening;
import ucadmin.simulation.generation.DungeonPlacedArtifact;
import ucadmin.simulation.generation.DungeonPoint;
import ucadmin.simulation.generation.DungeonRect;
import ucadmin.util.Logger;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dungeon simulation game object.
 */
public final class MazeSimulationGame implements SimulationGame {
    private static final int SCREEN_SIZE = 1000;
    private static final double VISIBLE_BLOCKS_ACROSS = 45.0;
    private static final int VIEW_BUFFER = 10;
    private static final double PLAYER_SPEED = 8.0;
    private static final double PLAYER_SHIFT_SPEED = 11.0;
    private static final double ADMIN_SPEED = 24.0;
    private static final double ADMIN_SHIFT_SPEED = 40.0;
    private static final double PLAYER_SIZE = 2.0;
    private static final double MAX_VIEW_DISTANCE_BLOCKS = 20.0;
    private static final double DISTANCE_FADE_START_BLOCKS = 3.0;
    private static final double WALL_LIGHT_RADIUS_BLOCKS = 10.0;
    private static final double WALL_LIGHT_SEEN_DISTANCE_BLOCKS = 25.0;
    private static final double WALL_LIGHT_FULL_DISTANCE_BLOCKS = 20.0;
    private static final double WALL_LIGHT_SIZE_BLOCKS = 0.72;
    private static final double LIGHT_RAY_EPSILON = 0.00035;
    private static final String WALL_LIGHT_ID = "wall_light";
    private static final float WALL_STROKE_PIXELS = 6.0f;
    private static final float SEALED_WALL_STROKE_PIXELS = 8.0f;
    private static final float OCCUPIED_FILL_STROKE_PIXELS = 3.0f;
    private static final int OPENING_NODE_RADIUS_PIXELS = 5;
    private static final Font ARTIFACT_LABEL_FONT = new Font("SansSerif", Font.BOLD, 12);
    private static final SecureRandom SEED_SOURCE = new SecureRandom();

    private final long seed;
    private final boolean adminMode;
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
        this(SEED_SOURCE.nextLong(), false);
    }

    public MazeSimulationGame(long seed) {
        this(seed, false);
    }

    public MazeSimulationGame(boolean adminMode) {
        this(SEED_SOURCE.nextLong(), adminMode);
    }

    public MazeSimulationGame(long seed, boolean adminMode) {
        this.seed = seed;
        this.adminMode = adminMode;
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

    public boolean isAdminMode() {
        return adminMode;
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

        drawWallLights(graphics, view, transform);
        if (adminMode) {
            drawOpeningNodes(graphics, view, transform);
            drawArtifactLabels(graphics, view, transform);
        } else {
            drawLineOfSightMask(graphics, context, view, transform);
        }
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
        double speed = adminMode
                ? (shiftHeld ? ADMIN_SHIFT_SPEED : ADMIN_SPEED)
                : (shiftHeld ? PLAYER_SHIFT_SPEED : PLAYER_SPEED);
        double distance = speed * deltaSeconds;
        double stepX = dx / length * distance;
        double stepY = dy / length * distance;

        loadAreaForPlayerCollision(context, stepX, stepY);
        if (adminMode) {
            playerX += stepX;
            playerY += stepY;
            return;
        }
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

    private void drawLineOfSightMask(
            Graphics2D graphics,
            SimulationContext context,
            DungeonRect view,
            ViewTransform transform
    ) {
        DungeonRect sightView = expand(view, (int) Math.ceil(WALL_LIGHT_SEEN_DISTANCE_BLOCKS + WALL_LIGHT_RADIUS_BLOCKS));
        List<DungeonLine> blockers = collectSightBlockers(sightView);
        Shape visibleShape = buildVisibleShape(playerX, playerY, MAX_VIEW_DISTANCE_BLOCKS, blockers, transform);
        Shape lightSightShape = buildVisibleShape(
                playerX,
                playerY,
                WALL_LIGHT_SEEN_DISTANCE_BLOCKS + WALL_LIGHT_RADIUS_BLOCKS,
                blockers,
                transform
        );
        DungeonRect lightView = expand(view, (int) Math.ceil(WALL_LIGHT_RADIUS_BLOCKS + 1.0));
        List<LightVisibility> lights = collectVisibleLights(lightView, blockers, transform, lightSightShape);
        Area clearArea = new Area(visibleShape);
        for (LightVisibility light : lights) {
            clearArea.add(new Area(light.shape()));
        }

        Area darkness = new Area(new Rectangle2D.Double(
                0,
                0,
                context.getConfig().getWidth(),
                context.getConfig().getHeight()
        ));
        darkness.subtract(clearArea);

        Object oldAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.BLACK);
        graphics.fill(darkness);
        drawDistanceFade(graphics, visibleShape, transform);
        for (LightVisibility light : lights) {
            drawLightFade(graphics, light, transform);
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
    }

    private List<DungeonLine> collectSightBlockers(DungeonRect view) {
        List<DungeonLine> blockers = new ArrayList<>();
        blockers.addAll(loadedArea.getWallsIntersecting(view));
        blockers.addAll(loadedArea.getSealedOpeningWallsIntersecting(view));
        return blockers;
    }

    private DungeonRect expand(DungeonRect rect, int blocks) {
        return new DungeonRect(
                rect.minX() - blocks,
                rect.minY() - blocks,
                rect.maxX() + blocks,
                rect.maxY() + blocks
        );
    }

    private List<LightVisibility> collectVisibleLights(
            DungeonRect view,
            List<DungeonLine> blockers,
            ViewTransform transform,
            Shape lightSightShape
    ) {
        List<LightVisibility> lights = new ArrayList<>();
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                if (!WALL_LIGHT_ID.equals(item.id())) {
                    continue;
                }
                double lightX = itemCellCenterX(item);
                double lightY = itemCellCenterY(item);
                double distance = distance(playerX, playerY, lightX, lightY);
                double strength = lightStrength(distance);
                if (strength <= 0.0) {
                    continue;
                }
                Shape shape = buildVisibleShape(
                        lightX,
                        lightY,
                        WALL_LIGHT_RADIUS_BLOCKS,
                        blockers,
                        transform
                );
                Area visibleLightArea = new Area(shape);
                visibleLightArea.intersect(new Area(lightSightShape));
                if (!visibleLightArea.isEmpty()) {
                    lights.add(new LightVisibility(item, visibleLightArea, strength));
                }
            }
        }
        return lights;
    }

    private void drawDistanceFade(Graphics2D graphics, Shape visibleShape, ViewTransform transform) {
        Shape oldClip = graphics.getClip();
        graphics.setClip(visibleShape);

        double pixelsPerBlock = transform.pixelsPerBlock();
        double startRadius = DISTANCE_FADE_START_BLOCKS * pixelsPerBlock;
        double maxRadius = MAX_VIEW_DISTANCE_BLOCKS * pixelsPerBlock;
        int playerScreenX = transform.worldToScreenX(playerX);
        int playerScreenY = transform.worldToScreenY(playerY);

        paintRadialFade(
                graphics,
                playerScreenX,
                playerScreenY,
                startRadius,
                maxRadius,
                new Color(0, 0, 0, 0),
                new Color(0, 0, 0, 238)
        );

        graphics.setClip(oldClip);
    }

    private void drawLightFade(Graphics2D graphics, LightVisibility light, ViewTransform transform) {
        Shape oldClip = graphics.getClip();
        graphics.setClip(light.shape());

        double pixelsPerBlock = transform.pixelsPerBlock();
        double maxRadius = WALL_LIGHT_RADIUS_BLOCKS * pixelsPerBlock;
        double lightX = itemCellCenterX(light.item());
        double lightY = itemCellCenterY(light.item());
        int lightScreenX = transform.worldToScreenX(lightX);
        int lightScreenY = transform.worldToScreenY(lightY);

        int warmAlpha = (int) Math.round(96 * light.strength());
        paintRadialFade(
                graphics,
                lightScreenX,
                lightScreenY,
                0.0,
                maxRadius,
                new Color(255, 224, 92, warmAlpha),
                new Color(255, 224, 92, 0)
        );
        int edgeAlpha = (int) Math.round(185 - 110 * light.strength());
        paintRadialFade(
                graphics,
                lightScreenX,
                lightScreenY,
                maxRadius * 0.35,
                maxRadius,
                new Color(0, 0, 0, 0),
                new Color(0, 0, 0, Math.max(45, edgeAlpha))
        );

        graphics.setClip(oldClip);
    }

    private void paintRadialFade(
            Graphics2D graphics,
            double centerX,
            double centerY,
            double clearRadius,
            double maxRadius,
            Color inner,
            Color outer
    ) {
        if (maxRadius <= 0.0) {
            return;
        }
        float clearStop = (float) Math.max(0.0, Math.min(0.98, clearRadius / maxRadius));
        float[] fractions = clearStop <= 0.0f
                ? new float[] { 0.0f, 1.0f }
                : new float[] { 0.0f, clearStop, 1.0f };
        Color[] colors = clearStop <= 0.0f
                ? new Color[] { inner, outer }
                : new Color[] { inner, inner, outer };
        RadialGradientPaint paint = new RadialGradientPaint(
                new Point2D.Double(centerX, centerY),
                (float) maxRadius,
                fractions,
                colors,
                MultipleGradientPaint.CycleMethod.NO_CYCLE
        );
        graphics.setPaint(paint);
        graphics.fillRect(
                (int) Math.floor(centerX - maxRadius),
                (int) Math.floor(centerY - maxRadius),
                (int) Math.ceil(maxRadius * 2.0),
                (int) Math.ceil(maxRadius * 2.0)
        );
    }

    private Shape buildVisibleShape(
            double sourceX,
            double sourceY,
            double maxDistance,
            List<DungeonLine> blockers,
            ViewTransform transform
    ) {
        List<Double> angles = visibilityAngles(sourceX, sourceY, blockers);
        if (angles.isEmpty()) {
            return fullVisibilityCircle(sourceX, sourceY, transform, maxDistance);
        }

        List<RayHit> hits = new ArrayList<>();
        for (double angle : angles) {
            hits.add(castVisibilityRay(sourceX, sourceY, angle, blockers, maxDistance));
        }
        hits.sort(Comparator.comparingDouble(RayHit::angle));

        Path2D.Double path = new Path2D.Double();
        RayHit first = hits.get(0);
        path.moveTo(transform.worldToScreenX(first.x()), transform.worldToScreenY(first.y()));
        for (int i = 1; i < hits.size(); i++) {
            RayHit hit = hits.get(i);
            path.lineTo(transform.worldToScreenX(hit.x()), transform.worldToScreenY(hit.y()));
        }
        path.closePath();
        return path;
    }

    private List<Double> visibilityAngles(double sourceX, double sourceY, List<DungeonLine> blockers) {
        List<Double> angles = new ArrayList<>();
        int radialSamples = 64;
        for (int i = 0; i < radialSamples; i++) {
            angles.add((Math.PI * 2.0 * i) / radialSamples);
        }
        for (DungeonLine blocker : blockers) {
            addVisibilityAngles(angles, sourceX, sourceY, blocker.start().x(), blocker.start().y());
            addVisibilityAngles(angles, sourceX, sourceY, blocker.end().x(), blocker.end().y());
        }
        return angles;
    }

    private void addVisibilityAngles(List<Double> angles, double sourceX, double sourceY, double x, double y) {
        double angle = Math.atan2(y - sourceY, x - sourceX);
        angles.add(normalizeAngle(angle - LIGHT_RAY_EPSILON));
        angles.add(normalizeAngle(angle));
        angles.add(normalizeAngle(angle + LIGHT_RAY_EPSILON));
    }

    private RayHit castVisibilityRay(
            double sourceX,
            double sourceY,
            double angle,
            List<DungeonLine> blockers,
            double maxDistance
    ) {
        double rayDx = Math.cos(angle);
        double rayDy = Math.sin(angle);
        double bestDistance = maxDistance;
        double bestX = sourceX + rayDx * maxDistance;
        double bestY = sourceY + rayDy * maxDistance;

        for (DungeonLine blocker : blockers) {
            Double distance = raySegmentDistance(sourceX, sourceY, rayDx, rayDy, blocker);
            if (distance != null && distance >= 0.0 && distance < bestDistance) {
                bestDistance = distance;
                bestX = sourceX + rayDx * distance;
                bestY = sourceY + rayDy * distance;
            }
        }
        return new RayHit(angle, bestX, bestY);
    }

    private Double raySegmentDistance(double rayX, double rayY, double rayDx, double rayDy, DungeonLine segment) {
        double segmentX = segment.start().x();
        double segmentY = segment.start().y();
        double segmentDx = segment.end().x() - segment.start().x();
        double segmentDy = segment.end().y() - segment.start().y();
        double cross = cross(rayDx, rayDy, segmentDx, segmentDy);
        if (Math.abs(cross) < 0.0000001) {
            return null;
        }

        double offsetX = segmentX - rayX;
        double offsetY = segmentY - rayY;
        double rayDistance = cross(offsetX, offsetY, segmentDx, segmentDy) / cross;
        double segmentDistance = cross(offsetX, offsetY, rayDx, rayDy) / cross;
        if (rayDistance < 0.0 || segmentDistance < 0.0 || segmentDistance > 1.0) {
            return null;
        }
        return rayDistance;
    }

    private double normalizeAngle(double angle) {
        double fullTurn = Math.PI * 2.0;
        double normalized = angle % fullTurn;
        return normalized < 0.0 ? normalized + fullTurn : normalized;
    }

    private Shape fullVisibilityCircle(double sourceX, double sourceY, ViewTransform transform, double radius) {
        Path2D.Double path = new Path2D.Double();
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2.0 * i) / segments;
            double x = sourceX + Math.cos(angle) * radius;
            double y = sourceY + Math.sin(angle) * radius;
            if (i == 0) {
                path.moveTo(transform.worldToScreenX(x), transform.worldToScreenY(y));
            } else {
                path.lineTo(transform.worldToScreenX(x), transform.worldToScreenY(y));
            }
        }
        path.closePath();
        return path;
    }

    private void drawWallLights(Graphics2D graphics, DungeonRect view, ViewTransform transform) {
        for (DungeonPlacedArtifact placement : loadedArea.getPlacementsIntersecting(view)) {
            for (DungeonItem item : placement.getWorldItems()) {
                if (WALL_LIGHT_ID.equals(item.id())) {
                    drawWallLight(graphics, item, transform);
                }
            }
        }
    }

    private void drawWallLight(Graphics2D graphics, DungeonItem item, ViewTransform transform) {
        Rectangle2D.Double box = wallLightBox(item);
        int x1 = transform.worldToScreenX(box.getMinX());
        int y1 = transform.worldToScreenY(box.getMinY());
        int x2 = transform.worldToScreenX(box.getMaxX());
        int y2 = transform.worldToScreenY(box.getMaxY());
        graphics.setColor(new Color(255, 222, 78));
        graphics.fillRect(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.max(2, Math.abs(x2 - x1)),
                Math.max(2, Math.abs(y2 - y1))
        );

        DungeonPoint offset = directionOffset(item.direction());
        graphics.setColor(new Color(255, 245, 150));
        graphics.drawLine(
                transform.worldToScreenX(itemCellCenterX(item)),
                transform.worldToScreenY(itemCellCenterY(item)),
                transform.worldToScreenX(itemCellCenterX(item) + offset.x() * 0.45),
                transform.worldToScreenY(itemCellCenterY(item) + offset.y() * 0.45)
        );
    }

    private Rectangle2D.Double wallLightBox(DungeonItem item) {
        double cellX = item.position().x();
        double cellY = item.position().y();
        double minX = cellX + (1.0 - WALL_LIGHT_SIZE_BLOCKS) / 2.0;
        double minY = cellY + (1.0 - WALL_LIGHT_SIZE_BLOCKS) / 2.0;

        switch (item.direction()) {
            case NORTH -> minY = cellY + 1.0 - WALL_LIGHT_SIZE_BLOCKS;
            case EAST -> minX = cellX;
            case SOUTH -> minY = cellY;
            case WEST -> minX = cellX + 1.0 - WALL_LIGHT_SIZE_BLOCKS;
        }

        return new Rectangle2D.Double(minX, minY, WALL_LIGHT_SIZE_BLOCKS, WALL_LIGHT_SIZE_BLOCKS);
    }

    private double itemCellCenterX(DungeonItem item) {
        return item.position().x() + 0.5;
    }

    private double itemCellCenterY(DungeonItem item) {
        return item.position().y() + 0.5;
    }

    private DungeonPoint directionOffset(DungeonDirection direction) {
        return switch (direction) {
            case NORTH -> new DungeonPoint(0, -1);
            case EAST -> new DungeonPoint(1, 0);
            case SOUTH -> new DungeonPoint(0, 1);
            case WEST -> new DungeonPoint(-1, 0);
        };
    }

    private double lightStrength(double distance) {
        if (distance > WALL_LIGHT_SEEN_DISTANCE_BLOCKS) {
            return 0.0;
        }
        if (distance <= WALL_LIGHT_FULL_DISTANCE_BLOCKS) {
            return 1.0;
        }
        return (WALL_LIGHT_SEEN_DISTANCE_BLOCKS - distance) /
                (WALL_LIGHT_SEEN_DISTANCE_BLOCKS - WALL_LIGHT_FULL_DISTANCE_BLOCKS);
    }

    private double distance(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return Math.sqrt(dx * dx + dy * dy);
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

    private double cross(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
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

    private record RayHit(double angle, double x, double y) {}

    private record LightVisibility(DungeonItem item, Shape shape, double strength) {}
}
