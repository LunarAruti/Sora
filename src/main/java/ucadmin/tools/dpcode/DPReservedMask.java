package ucadmin.tools.dpcode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reservation mask and traversal metadata for one logical DP grid.
 *
 * <p>This class computes all structural reservations for a chosen logical
 * size, including corner markers, timing patterns, control box, bootstrap
 * cells, header cells, floater cells, and payload-eligible cells.</p>
 */
final class DPReservedMask {

    enum CellRole {
        PAYLOAD,
        TOP_LEFT_MARKER,
        BOTTOM_RIGHT_MARKER,
        TOP_TIMING_WHITE,
        TOP_TIMING_BLACK,
        RIGHT_TIMING_WHITE,
        RIGHT_TIMING_BLACK,
        CONTROL_BLACK,
        BOOTSTRAP,
        HEADER,
        FLOATER_ARM_BLACK,
        FLOATER_CENTER_WHITE
    }

    record Point(int x, int y) {}

    private final int logicalSize;
    private final CellRole[][] roles;
    private final List<Point> bootstrapCells;
    private final List<Point> headerCells;
    private final List<Point> payloadCells;
    private final List<Point> floaterCenters;

    private DPReservedMask(int logicalSize,
                           CellRole[][] roles,
                           List<Point> bootstrapCells,
                           List<Point> headerCells,
                           List<Point> payloadCells,
                           List<Point> floaterCenters) {
        this.logicalSize = logicalSize;
        this.roles = roles;
        this.bootstrapCells = bootstrapCells;
        this.headerCells = headerCells;
        this.payloadCells = payloadCells;
        this.floaterCenters = floaterCenters;
    }

    static DPReservedMask create(int logicalSize) {
        validateLogicalSize(logicalSize);

        CellRole[][] roles = new CellRole[logicalSize][logicalSize];
        for (int y = 0; y < logicalSize; y++) {
            for (int x = 0; x < logicalSize; x++) {
                roles[y][x] = CellRole.PAYLOAD;
            }
        }

        markTopLeftMarker(roles);
        markBottomRightMarker(roles, logicalSize);
        markTimingPatterns(roles, logicalSize);
        markControlBox(roles, logicalSize);
        markHeaderBlock(roles, logicalSize);

        List<Point> floaterCenters = placeFloaters(roles, logicalSize);
        List<Point> bootstrapCells = buildBootstrapTraversal(logicalSize);
        List<Point> headerCells = buildHeaderTraversal(logicalSize);
        List<Point> payloadCells = buildPayloadTraversal(roles);

        return new DPReservedMask(
                logicalSize,
                roles,
                Collections.unmodifiableList(bootstrapCells),
                Collections.unmodifiableList(headerCells),
                Collections.unmodifiableList(payloadCells),
                Collections.unmodifiableList(floaterCenters)
        );
    }

    int getLogicalSize() {
        return logicalSize;
    }

    CellRole getRole(int x, int y) {
        return roles[y][x];
    }

    List<Point> getBootstrapCells() {
        return bootstrapCells;
    }

    List<Point> getHeaderCells() {
        return headerCells;
    }

    List<Point> getPayloadCells() {
        return payloadCells;
    }

    List<Point> getFloaterCenters() {
        return floaterCenters;
    }

    private static void validateLogicalSize(int logicalSize) {
        if (logicalSize < 10 || ((logicalSize - 10) % 4) != 0) {
            throw new IllegalArgumentException("Illegal DP logical size: " + logicalSize);
        }
    }

    private static void markTopLeftMarker(CellRole[][] roles) {
        for (int x = 0; x <= 4; x++) {
            roles[0][x] = CellRole.TOP_LEFT_MARKER;
        }
        for (int y = 0; y <= 4; y++) {
            roles[y][0] = CellRole.TOP_LEFT_MARKER;
        }
    }

    private static void markBottomRightMarker(CellRole[][] roles, int logicalSize) {
        for (int x = logicalSize - 5; x < logicalSize; x++) {
            roles[logicalSize - 1][x] = CellRole.BOTTOM_RIGHT_MARKER;
        }
        for (int y = logicalSize - 5; y < logicalSize; y++) {
            roles[y][logicalSize - 1] = CellRole.BOTTOM_RIGHT_MARKER;
        }
    }

    private static void markTimingPatterns(CellRole[][] roles, int logicalSize) {
        for (int x = 5; x < logicalSize; x++) {
            roles[0][x] = ((x - 5) % 2 == 0) ? CellRole.TOP_TIMING_WHITE : CellRole.TOP_TIMING_BLACK;
        }
        for (int y = 0; y <= logicalSize - 6; y++) {
            roles[y][logicalSize - 1] = (y % 2 == 0) ? CellRole.RIGHT_TIMING_WHITE : CellRole.RIGHT_TIMING_BLACK;
        }
    }

    private static void markControlBox(CellRole[][] roles, int logicalSize) {
        int top = logicalSize - 3;
        for (int y = top; y < logicalSize; y++) {
            for (int x = 0; x <= 2; x++) {
                roles[y][x] = CellRole.CONTROL_BLACK;
            }
        }

        roles[logicalSize - 2][0] = CellRole.BOOTSTRAP;
        roles[logicalSize - 2][1] = CellRole.BOOTSTRAP;
        roles[logicalSize - 1][1] = CellRole.BOOTSTRAP;
        roles[logicalSize - 1][0] = CellRole.BOOTSTRAP;
    }

    private static void markHeaderBlock(CellRole[][] roles, int logicalSize) {
        for (int y = 1; y <= 4; y++) {
            for (int x = logicalSize - 5; x <= logicalSize - 2; x++) {
                roles[y][x] = CellRole.HEADER;
            }
        }
    }

    private static List<Point> placeFloaters(CellRole[][] roles, int logicalSize) {
        int floaterSide = computeFloaterSide(logicalSize);
        if (floaterSide == 0) {
            return List.of();
        }

        boolean[][] legalCenters = buildLegalCenterMatrix(roles);
        FloaterRectangle usableRectangle = findUsableFloaterRectangle(legalCenters, floaterSide);
        int[] xCenters = subdivisionCenterCoordinates(usableRectangle.minX(), usableRectangle.maxX(), floaterSide);
        int[] yCenters = subdivisionCenterCoordinates(usableRectangle.minY(), usableRectangle.maxY(), floaterSide);
        List<Point> centers = new ArrayList<>(floaterSide * floaterSide);

        for (int yCenter : yCenters) {
            for (int xCenter : xCenters) {
                if (!legalCenters[yCenter][xCenter]) {
                    throw new IllegalStateException(
                            "Unable to place floater at center (" + xCenter + "," + yCenter + ") for N=" + logicalSize
                    );
                }
                markFloater(roles, xCenter, yCenter);
                centers.add(new Point(xCenter, yCenter));
            }
        }
        return centers;
    }

    private static int computeFloaterSide(int logicalSize) {
        if (logicalSize < 30) {
            return 0;
        }
        return 1 + ((logicalSize - 30) / 16);
    }

    /**
     * Builds the legal floater-center map after all hard-reserved structures
     * have been marked.
     */
    private static boolean[][] buildLegalCenterMatrix(CellRole[][] roles) {
        int logicalSize = roles.length;
        boolean[][] legalCenters = new boolean[logicalSize][logicalSize];
        for (int y = 0; y < logicalSize; y++) {
            for (int x = 0; x < logicalSize; x++) {
                legalCenters[y][x] = canPlaceFloater(roles, x, y);
            }
        }
        return legalCenters;
    }

    /**
     * Finds the largest all-legal rectangle of floater centers. This becomes
     * the official usable interior for the floater lattice.
     *
     * <p>The chosen rectangle is deterministic:
     * prefer larger area first, then prefer the rectangle whose center is
     * closest to the grid center, then prefer the wider rectangle, then the
     * taller rectangle.</p>
     */
    private static FloaterRectangle findUsableFloaterRectangle(boolean[][] legalCenters, int floaterSide) {
        int logicalSize = legalCenters.length;
        int[] heights = new int[logicalSize];
        FloaterRectangle best = null;
        double gridCenter = (logicalSize - 1) / 2.0;

        for (int y = 0; y < logicalSize; y++) {
            for (int x = 0; x < logicalSize; x++) {
                heights[x] = legalCenters[y][x] ? (heights[x] + 1) : 0;
            }

            java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
            for (int x = 0; x <= logicalSize; x++) {
                int currentHeight = (x == logicalSize) ? 0 : heights[x];
                while (!stack.isEmpty() && currentHeight < heights[stack.peek()]) {
                    int height = heights[stack.pop()];
                    if (height == 0) {
                        continue;
                    }

                    int left = stack.isEmpty() ? 0 : (stack.peek() + 1);
                    int right = x - 1;
                    int width = right - left + 1;
                    if (width < floaterSide || height < floaterSide) {
                        continue;
                    }

                    FloaterRectangle candidate = new FloaterRectangle(left, y - height + 1, right, y);
                    if (best == null || isBetterRectangle(candidate, best, gridCenter)) {
                        best = candidate;
                    }
                }
                stack.push(x);
            }
        }

        if (best == null) {
            throw new IllegalStateException(
                    "Unable to derive a legal floater interior rectangle for floaterSide=" + floaterSide +
                            " and logicalSize=" + logicalSize
            );
        }
        return best;
    }

    /**
     * Returns true when the candidate rectangle is the preferred floater
     * interior under the deterministic tie-break rules.
     */
    private static boolean isBetterRectangle(FloaterRectangle candidate,
                                             FloaterRectangle best,
                                             double gridCenter) {
        if (candidate.area() != best.area()) {
            return candidate.area() > best.area();
        }

        double candidateDistance = candidate.centerDistanceSquared(gridCenter);
        double bestDistance = best.centerDistanceSquared(gridCenter);
        if (Double.compare(candidateDistance, bestDistance) != 0) {
            return candidateDistance < bestDistance;
        }

        if (candidate.width() != best.width()) {
            return candidate.width() > best.width();
        }
        if (candidate.height() != best.height()) {
            return candidate.height() > best.height();
        }

        if (candidate.minX() != best.minX()) {
            return candidate.minX() < best.minX();
        }
        return candidate.minY() < best.minY();
    }

    /**
     * Returns the center coordinate of each equal subdivision across an
     * inclusive coordinate range.
     *
     * <p>This is intentionally different from placing points on the range
     * endpoints. For example, a 2x2 floater lattice should sit near the quarter
     * points of the usable interior rather than being pushed to the interior
     * corners.</p>
     */
    private static int[] subdivisionCenterCoordinates(int min, int max, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Floater subdivision count must be positive");
        }
        if (max < min) {
            throw new IllegalArgumentException("Invalid floater subdivision range: " + min + ".." + max);
        }

        int[] values = new int[count];
        int width = (max - min) + 1;
        if (count == 1) {
            values[0] = (min + max) / 2;
            return values;
        }

        for (int i = 0; i < count; i++) {
            double center = min + ((((2.0 * i) + 1.0) * width) / (2.0 * count)) - 0.5;
            int coordinate = (int) Math.round(center);
            values[i] = Math.max(min, Math.min(max, coordinate));
        }
        return values;
    }

    private static boolean canPlaceFloater(CellRole[][] roles, int centerX, int centerY) {
        int[][] offsets = {
                {0, -1}, {-1, 0}, {0, 0}, {1, 0}, {0, 1}
        };
        for (int[] offset : offsets) {
            int x = centerX + offset[0];
            int y = centerY + offset[1];
            if (x < 0 || y < 0 || y >= roles.length || x >= roles.length) {
                return false;
            }
            if (roles[y][x] != CellRole.PAYLOAD) {
                return false;
            }
        }
        return true;
    }

    private static void markFloater(CellRole[][] roles, int centerX, int centerY) {
        roles[centerY - 1][centerX] = CellRole.FLOATER_ARM_BLACK;
        roles[centerY][centerX - 1] = CellRole.FLOATER_ARM_BLACK;
        roles[centerY][centerX] = CellRole.FLOATER_CENTER_WHITE;
        roles[centerY][centerX + 1] = CellRole.FLOATER_ARM_BLACK;
        roles[centerY + 1][centerX] = CellRole.FLOATER_ARM_BLACK;
    }

    private static List<Point> buildBootstrapTraversal(int logicalSize) {
        return List.of(
                new Point(0, logicalSize - 2),
                new Point(1, logicalSize - 2),
                new Point(1, logicalSize - 1),
                new Point(0, logicalSize - 1)
        );
    }

    private static List<Point> buildHeaderTraversal(int logicalSize) {
        List<Point> points = new ArrayList<>(16);
        int startX = logicalSize - 5;
        for (int row = 0; row < 4; row++) {
            int y = 1 + row;
            if ((row % 2) == 0) {
                for (int x = startX; x < startX + 4; x++) {
                    points.add(new Point(x, y));
                }
            } else {
                for (int x = startX + 3; x >= startX; x--) {
                    points.add(new Point(x, y));
                }
            }
        }
        return points;
    }

    private static List<Point> buildPayloadTraversal(CellRole[][] roles) {
        int logicalSize = roles.length;
        List<Point> points = new ArrayList<>();
        for (int y = 0; y < logicalSize; y++) {
            if ((y % 2) == 0) {
                for (int x = 0; x < logicalSize; x++) {
                    if (roles[y][x] == CellRole.PAYLOAD) {
                        points.add(new Point(x, y));
                    }
                }
            } else {
                for (int x = logicalSize - 1; x >= 0; x--) {
                    if (roles[y][x] == CellRole.PAYLOAD) {
                        points.add(new Point(x, y));
                    }
                }
            }
        }
        return points;
    }

    /**
     * Immutable rectangle of legal floater centers.
     */
    private record FloaterRectangle(int minX, int minY, int maxX, int maxY) {
        int width() {
            return (maxX - minX) + 1;
        }

        int height() {
            return (maxY - minY) + 1;
        }

        int area() {
            return width() * height();
        }

        double centerDistanceSquared(double gridCenter) {
            double centerX = (minX + maxX) / 2.0;
            double centerY = (minY + maxY) / 2.0;
            double dx = centerX - gridCenter;
            double dy = centerY - gridCenter;
            return (dx * dx) + (dy * dy);
        }
    }
}
