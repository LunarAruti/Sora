package sora.tools.dpcode;

import sora.tools.Colors;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Image-to-logical-grid sampler for exported or digitally resized DP PNGs.
 *
 * <p>This helper does not rely on the image still having its original quiet
 * zone or exact pixel divisibility. Instead it derives a coarse square symbol
 * region from projected image activity, refines that region from the top-left
 * and bottom-right corner markers, infers floating-point cell geometry for
 * legal logical sizes, scores those candidates using the fixed structural
 * lattice (corner markers, timing patterns, control box, and floater
 * crosses), and then samples the logical grid from the strongest candidates
 * first.</p>
 */
final class DPImageSampler {

    private static final boolean SAMPLER_DEBUG_LOGGING = true;
    private static final int MIN_LOGICAL_SIZE = 10;
    private static final int ACTIVE_THRESHOLD = 248;
    private static final double MIN_SAMPLE_CELL_SIZE_PX = 2.0;
    private static final double EDGE_ACTIVITY_FRACTION = 0.02;
    private static final double MARKER_SEARCH_RADIUS_CELLS = 2.0;

    private DPImageSampler() {}

    static List<SampleCandidate> sampleCandidates(Path imagePath) {
        if (imagePath == null) {
            throw new IllegalArgumentException("imagePath cannot be null");
        }

        BufferedImage image = readImage(imagePath);
        logSampler("sampleCandidates start imagePath=" + imagePath
                + ", imageWidth=" + image.getWidth()
                + ", imageHeight=" + image.getHeight());
        validateImageGeometry(image);
        Bounds coarseBounds = detectSymbolBounds(image);
        logSampler("sampleCandidates coarseBounds=" + coarseBounds);

        List<SampleCandidate> candidates = new ArrayList<>();
        List<Integer> logicalSizeCandidates = buildLogicalSizeCandidates(image, coarseBounds);
        logSampler("sampleCandidates logicalSizeCandidates=" + logicalSizeCandidates);
        for (int logicalSize : logicalSizeCandidates) {
            Bounds bounds = refineBoundsFromCornerMarkers(image, coarseBounds, logicalSize);
            double cellWidthPx = bounds.widthPx() / (double) logicalSize;
            double cellHeightPx = bounds.heightPx() / (double) logicalSize;
            if (cellWidthPx < MIN_SAMPLE_CELL_SIZE_PX || cellHeightPx < MIN_SAMPLE_CELL_SIZE_PX) {
                logSampler("candidate skip logicalSize=" + logicalSize
                        + ", bounds=" + bounds
                        + ", cellWidthPx=" + formatDouble(cellWidthPx)
                        + ", cellHeightPx=" + formatDouble(cellHeightPx)
                        + " because sampled cells are too small");
                continue;
            }

            double structureScore = scoreStructure(image, bounds, logicalSize);
            logSampler("candidate logicalSize=" + logicalSize
                    + ", bounds=" + bounds
                    + ", cellWidthPx=" + formatDouble(cellWidthPx)
                    + ", cellHeightPx=" + formatDouble(cellHeightPx)
                    + ", structureScore=" + formatDouble(structureScore));
            candidates.add(new SampleCandidate(
                    imagePath,
                    sampleLogicalGrid(image, bounds, logicalSize),
                    logicalSize,
                    cellWidthPx,
                    cellHeightPx,
                    structureScore,
                    bounds,
                    image.getWidth(),
                    image.getHeight()
            ));
        }

        candidates.sort(Comparator
                .comparingDouble(SampleCandidate::structureScore).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (SampleCandidate candidate) -> Math.min(candidate.cellWidthPx(), candidate.cellHeightPx())
                ).reversed())
                .thenComparingInt(SampleCandidate::logicalSize));
        logSampler("sampleCandidates sortedCandidates=" + summarizeCandidates(candidates));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Image does not yield any legal DP geometry candidates within bounds " + coarseBounds +
                            " for dimensions " + image.getWidth() + "x" + image.getHeight()
            );
        }
        return candidates;
    }

    /**
     * Derives plausible legal logical sizes from the visible timing/marker
     * rhythm on the top and right structural edges.
     */
    private static List<Integer> buildLogicalSizeCandidates(BufferedImage image, Bounds bounds) {
        Set<Integer> candidates = new LinkedHashSet<>();
        List<Integer> topRuns = collectHorizontalRuns(image, bounds.minY(), bounds.minX(), bounds.maxX());
        List<Integer> rightRuns = collectVerticalRuns(image, bounds.maxX(), bounds.minY(), bounds.maxY());
        double estimatedCellWidth = estimateCellSizeFromRuns(topRuns);
        double estimatedCellHeight = estimateCellSizeFromRuns(rightRuns);
        int topRunLogicalSize = estimateLogicalSizeFromRunCount(topRuns.size());
        int rightRunLogicalSize = estimateLogicalSizeFromRunCount(rightRuns.size());
        logSampler("buildLogicalSizeCandidates bounds=" + bounds
                + ", topRuns=" + formatIntList(topRuns, 24)
                + ", rightRuns=" + formatIntList(rightRuns, 24)
                + ", topRunLogicalSize=" + topRunLogicalSize
                + ", rightRunLogicalSize=" + rightRunLogicalSize
                + ", estimatedCellWidth=" + formatDouble(estimatedCellWidth)
                + ", estimatedCellHeight=" + formatDouble(estimatedCellHeight));

        addRunCountCandidates(candidates, topRuns.size(), "top");
        addRunCountCandidates(candidates, rightRuns.size(), "right");
        addEstimatedCandidates(candidates, bounds.widthPx(), estimatedCellWidth);
        addEstimatedCandidates(candidates, bounds.heightPx(), estimatedCellHeight);
        addEstimatedCandidates(
                candidates,
                Math.min(bounds.widthPx(), bounds.heightPx()),
                averagePositive(estimatedCellWidth, estimatedCellHeight)
        );

        if (candidates.size() < 5) {
            int maxLogicalSize = Math.min(bounds.widthPx(), bounds.heightPx());
            for (int logicalSize = MIN_LOGICAL_SIZE; logicalSize <= maxLogicalSize; logicalSize += 4) {
                candidates.add(logicalSize);
            }
            logSampler("buildLogicalSizeCandidates fallback added full legal range up to " + maxLogicalSize);
        }

        List<Integer> result = new ArrayList<>(candidates);
        logSampler("buildLogicalSizeCandidates result=" + result);
        return result;
    }

    private static void addRunCountCandidates(Set<Integer> candidates, int blackRunCount, String source) {
        int estimatedLogicalSize = estimateLogicalSizeFromRunCount(blackRunCount);
        if (!isLegalLogicalSize(estimatedLogicalSize)) {
            logSampler("addRunCountCandidates skip source=" + source
                    + ", blackRunCount=" + blackRunCount
                    + ", estimatedLogicalSize=" + estimatedLogicalSize);
            return;
        }

        logSampler("addRunCountCandidates source=" + source
                + ", blackRunCount=" + blackRunCount
                + ", estimatedLogicalSize=" + estimatedLogicalSize);
        addLogicalSizeNeighborhood(candidates, estimatedLogicalSize);
    }

    private static void addEstimatedCandidates(Set<Integer> candidates, int spanPx, double estimatedCellSizePx) {
        if (estimatedCellSizePx < MIN_SAMPLE_CELL_SIZE_PX) {
            logSampler("addEstimatedCandidates skip spanPx=" + spanPx
                    + ", estimatedCellSizePx=" + formatDouble(estimatedCellSizePx));
            return;
        }

        int estimatedLogicalSize = (int) Math.round(spanPx / estimatedCellSizePx);
        int center = snapToLegalLogicalSize(estimatedLogicalSize);
        logSampler("addEstimatedCandidates spanPx=" + spanPx
                + ", estimatedCellSizePx=" + formatDouble(estimatedCellSizePx)
                + ", estimatedLogicalSize=" + estimatedLogicalSize
                + ", snappedCenter=" + center);
        addLogicalSizeNeighborhood(candidates, center);
    }

    private static void addLogicalSizeNeighborhood(Set<Integer> candidates, int center) {
        for (int delta = -8; delta <= 8; delta += 4) {
            int logicalSize = snapToLegalLogicalSize(center + delta);
            if (isLegalLogicalSize(logicalSize)) {
                candidates.add(logicalSize);
            }
        }
    }

    private static int estimateLogicalSizeFromRunCount(int blackRunCount) {
        if (blackRunCount <= 0) {
            return -1;
        }
        return (blackRunCount * 2) + 4;
    }

    private static double estimateCellSizeFromTopEdge(BufferedImage image, Bounds bounds) {
        List<Integer> activeRuns = collectHorizontalRuns(image, bounds.minY(), bounds.minX(), bounds.maxX());
        return estimateCellSizeFromRuns(activeRuns);
    }

    private static double estimateCellSizeFromRightEdge(BufferedImage image, Bounds bounds) {
        List<Integer> activeRuns = collectVerticalRuns(image, bounds.maxX(), bounds.minY(), bounds.maxY());
        return estimateCellSizeFromRuns(activeRuns);
    }

    private static List<Integer> collectHorizontalRuns(BufferedImage image, int y, int startX, int endX) {
        List<Integer> runs = new ArrayList<>();
        boolean inRun = false;
        int runStart = startX;

        for (int x = startX; x <= endX; x++) {
            int argb = image.getRGB(x, y);
            boolean active = isActivePixel(argb);
            if (active && !inRun) {
                inRun = true;
                runStart = x;
            } else if (!active && inRun) {
                runs.add(x - runStart);
                inRun = false;
            }
        }

        if (inRun) {
            runs.add((endX - runStart) + 1);
        }
        return runs;
    }

    private static List<Integer> collectVerticalRuns(BufferedImage image, int x, int startY, int endY) {
        List<Integer> runs = new ArrayList<>();
        boolean inRun = false;
        int runStart = startY;

        for (int y = startY; y <= endY; y++) {
            int argb = image.getRGB(x, y);
            boolean active = isActivePixel(argb);
            if (active && !inRun) {
                inRun = true;
                runStart = y;
            } else if (!active && inRun) {
                runs.add(y - runStart);
                inRun = false;
            }
        }

        if (inRun) {
            runs.add((endY - runStart) + 1);
        }
        return runs;
    }

    private static double estimateCellSizeFromRuns(List<Integer> activeRuns) {
        List<Double> estimates = new ArrayList<>();
        for (int i = 0; i < activeRuns.size(); i++) {
            int run = activeRuns.get(i);
            if (run <= 0) {
                continue;
            }
            if (i == 0) {
                estimates.add(run / 5.0);
            } else {
                estimates.add((double) run);
            }
        }
        if (estimates.isEmpty()) {
            return -1.0;
        }
        estimates.sort(Double::compare);
        return estimates.get(estimates.size() / 2);
    }

    private static double averagePositive(double first, double second) {
        if (first > 0.0 && second > 0.0) {
            return (first + second) / 2.0;
        }
        if (first > 0.0) {
            return first;
        }
        return second;
    }

    private static int snapToLegalLogicalSize(int logicalSize) {
        if (logicalSize <= MIN_LOGICAL_SIZE) {
            return MIN_LOGICAL_SIZE;
        }
        int remainder = (logicalSize - MIN_LOGICAL_SIZE) % 4;
        if (remainder == 0) {
            return logicalSize;
        }
        return logicalSize + (4 - remainder);
    }

    private static boolean isLegalLogicalSize(int logicalSize) {
        return logicalSize >= MIN_LOGICAL_SIZE && ((logicalSize - MIN_LOGICAL_SIZE) % 4) == 0;
    }

    private static BufferedImage readImage(Path imagePath) {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) {
                throw new IllegalArgumentException("ImageIO could not decode image: " + imagePath);
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read image: " + imagePath, exception);
        }
    }

    private static void validateImageGeometry(BufferedImage image) {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        if (image.getWidth() < MIN_LOGICAL_SIZE || image.getHeight() < MIN_LOGICAL_SIZE) {
            throw new IllegalArgumentException(
                    "Image is too small to contain a legal DP symbol: " + image.getWidth() + "x" + image.getHeight()
            );
        }
    }

    private static Bounds detectSymbolBounds(BufferedImage image) {
        int[] rowCounts = new int[image.getHeight()];
        int[] colCounts = new int[image.getWidth()];
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!isActivePixel(image.getRGB(x, y))) {
                    continue;
                }
                rowCounts[y]++;
                colCounts[x]++;
                if (x < minX) {
                    minX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("Image does not contain any detectable DP symbol content");
        }

        int rowThreshold = Math.max(3, (int) Math.ceil(image.getWidth() * EDGE_ACTIVITY_FRACTION));
        int colThreshold = Math.max(3, (int) Math.ceil(image.getHeight() * EDGE_ACTIVITY_FRACTION));
        Bounds projectedBounds = boundsFromProjectedActivity(rowCounts, colCounts, rowThreshold, colThreshold);
        Bounds rawBounds = (projectedBounds != null) ? projectedBounds : new Bounds(minX, minY, maxX, maxY);
        Bounds normalizedBounds = normalizeToSquareBounds(rawBounds, image.getWidth(), image.getHeight());
        logSampler("detectSymbolBounds rawActiveBounds=" + new Bounds(minX, minY, maxX, maxY)
                + ", rowThreshold=" + rowThreshold
                + ", colThreshold=" + colThreshold
                + ", projectedBounds=" + projectedBounds
                + ", normalizedBounds=" + normalizedBounds
                + ", topRowCounts=" + formatIntArray(rowCounts, 16)
                + ", leftColCounts=" + formatIntArray(colCounts, 16));
        return normalizedBounds;
    }

    private static Bounds boundsFromProjectedActivity(int[] rowCounts,
                                                      int[] colCounts,
                                                      int rowThreshold,
                                                      int colThreshold) {
        int minY = firstIndexAtLeast(rowCounts, rowThreshold);
        int maxY = lastIndexAtLeast(rowCounts, rowThreshold);
        int minX = firstIndexAtLeast(colCounts, colThreshold);
        int maxX = lastIndexAtLeast(colCounts, colThreshold);
        if (minX < 0 || minY < 0 || maxX < minX || maxY < minY) {
            return null;
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static int firstIndexAtLeast(int[] values, int threshold) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= threshold) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexAtLeast(int[] values, int threshold) {
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i] >= threshold) {
                return i;
            }
        }
        return -1;
    }

    private static Bounds normalizeToSquareBounds(Bounds bounds, int imageWidth, int imageHeight) {
        int side = Math.min(Math.max(bounds.widthPx(), bounds.heightPx()), Math.min(imageWidth, imageHeight));
        double centerX = (bounds.minX() + bounds.maxX()) / 2.0;
        double centerY = (bounds.minY() + bounds.maxY()) / 2.0;

        int minX = (int) Math.round(centerX - ((side - 1) / 2.0));
        int minY = (int) Math.round(centerY - ((side - 1) / 2.0));
        int maxX = minX + side - 1;
        int maxY = minY + side - 1;

        if (minX < 0) {
            maxX -= minX;
            minX = 0;
        }
        if (minY < 0) {
            maxY -= minY;
            minY = 0;
        }
        if (maxX >= imageWidth) {
            int shift = maxX - imageWidth + 1;
            minX -= shift;
            maxX -= shift;
        }
        if (maxY >= imageHeight) {
            int shift = maxY - imageHeight + 1;
            minY -= shift;
            maxY -= shift;
        }

        minX = clamp(minX, 0, imageWidth - 1);
        minY = clamp(minY, 0, imageHeight - 1);
        maxX = clamp(maxX, minX, imageWidth - 1);
        maxY = clamp(maxY, minY, imageHeight - 1);
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static Bounds refineBoundsFromCornerMarkers(BufferedImage image, Bounds coarseBounds, int logicalSize) {
        double coarseCellWidth = coarseBounds.widthPx() / (double) logicalSize;
        double coarseCellHeight = coarseBounds.heightPx() / (double) logicalSize;
        if (coarseCellWidth < MIN_SAMPLE_CELL_SIZE_PX || coarseCellHeight < MIN_SAMPLE_CELL_SIZE_PX) {
            logSampler("refineBoundsFromCornerMarkers logicalSize=" + logicalSize
                    + " returning coarseBounds because coarse cells are too small"
                    + ", coarseBounds=" + coarseBounds
                    + ", coarseCellWidth=" + formatDouble(coarseCellWidth)
                    + ", coarseCellHeight=" + formatDouble(coarseCellHeight));
            return coarseBounds;
        }

        CornerAnchor topLeft = findBestTopLeftCorner(image, coarseBounds, coarseCellWidth, coarseCellHeight);
        CornerAnchor bottomRight = findBestBottomRightCorner(image, coarseBounds, coarseCellWidth, coarseCellHeight);
        if (topLeft == null || bottomRight == null) {
            logSampler("refineBoundsFromCornerMarkers logicalSize=" + logicalSize
                    + " returning coarseBounds because a corner anchor was not found"
                    + ", coarseBounds=" + coarseBounds
                    + ", topLeft=" + topLeft
                    + ", bottomRight=" + bottomRight);
            return coarseBounds;
        }
        if (bottomRight.x() <= topLeft.x() || bottomRight.y() <= topLeft.y()) {
            logSampler("refineBoundsFromCornerMarkers logicalSize=" + logicalSize
                    + " returning coarseBounds because anchors are inverted"
                    + ", coarseBounds=" + coarseBounds
                    + ", topLeft=" + topLeft
                    + ", bottomRight=" + bottomRight);
            return coarseBounds;
        }
        Bounds refinedBounds = new Bounds(topLeft.x(), topLeft.y(), bottomRight.x(), bottomRight.y());
        logSampler("refineBoundsFromCornerMarkers logicalSize=" + logicalSize
                + ", coarseBounds=" + coarseBounds
                + ", coarseCellWidth=" + formatDouble(coarseCellWidth)
                + ", coarseCellHeight=" + formatDouble(coarseCellHeight)
                + ", topLeft=" + topLeft
                + ", bottomRight=" + bottomRight
                + ", refinedBounds=" + refinedBounds);
        return refinedBounds;
    }

    private static CornerAnchor findBestTopLeftCorner(BufferedImage image,
                                                      Bounds coarseBounds,
                                                      double cellWidth,
                                                      double cellHeight) {
        int searchRadiusX = Math.max(2, (int) Math.ceil(cellWidth * MARKER_SEARCH_RADIUS_CELLS));
        int searchRadiusY = Math.max(2, (int) Math.ceil(cellHeight * MARKER_SEARCH_RADIUS_CELLS));
        int minX = clamp(coarseBounds.minX() - searchRadiusX, 0, image.getWidth() - 1);
        int maxX = clamp(coarseBounds.minX() + searchRadiusX, 0, image.getWidth() - 1);
        int minY = clamp(coarseBounds.minY() - searchRadiusY, 0, image.getHeight() - 1);
        int maxY = clamp(coarseBounds.minY() + searchRadiusY, 0, image.getHeight() - 1);

        CornerAnchor best = null;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double score = scoreTopLeftMarker(image, x, y, cellWidth, cellHeight);
                if (best == null || score > best.score()) {
                    best = new CornerAnchor(x, y, score);
                }
            }
        }
        return best;
    }

    private static CornerAnchor findBestBottomRightCorner(BufferedImage image,
                                                          Bounds coarseBounds,
                                                          double cellWidth,
                                                          double cellHeight) {
        int searchRadiusX = Math.max(2, (int) Math.ceil(cellWidth * MARKER_SEARCH_RADIUS_CELLS));
        int searchRadiusY = Math.max(2, (int) Math.ceil(cellHeight * MARKER_SEARCH_RADIUS_CELLS));
        int minX = clamp(coarseBounds.maxX() - searchRadiusX, 0, image.getWidth() - 1);
        int maxX = clamp(coarseBounds.maxX() + searchRadiusX, 0, image.getWidth() - 1);
        int minY = clamp(coarseBounds.maxY() - searchRadiusY, 0, image.getHeight() - 1);
        int maxY = clamp(coarseBounds.maxY() + searchRadiusY, 0, image.getHeight() - 1);

        CornerAnchor best = null;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double score = scoreBottomRightMarker(image, x, y, cellWidth, cellHeight);
                if (best == null || score > best.score()) {
                    best = new CornerAnchor(x, y, score);
                }
            }
        }
        return best;
    }

    private static double scoreTopLeftMarker(BufferedImage image,
                                             int cornerX,
                                             int cornerY,
                                             double cellWidth,
                                             double cellHeight) {
        double score = 0.0;
        int checks = 0;
        for (int offset = 0; offset < 5; offset++) {
            score += blackScore(sampleMarkerCell(image, cornerX + (offset * cellWidth), cornerY, cellWidth, cellHeight));
            checks++;
        }
        for (int offset = 1; offset < 5; offset++) {
            score += blackScore(sampleMarkerCell(image, cornerX, cornerY + (offset * cellHeight), cellWidth, cellHeight));
            checks++;
        }
        return score / checks;
    }

    private static double scoreBottomRightMarker(BufferedImage image,
                                                 int cornerX,
                                                 int cornerY,
                                                 double cellWidth,
                                                 double cellHeight) {
        double score = 0.0;
        int checks = 0;
        for (int offset = 0; offset < 5; offset++) {
            double cellStartX = (cornerX + 1.0) - ((offset + 1.0) * cellWidth);
            double cellStartY = (cornerY + 1.0) - cellHeight;
            score += blackScore(sampleMarkerCell(image, cellStartX, cellStartY, cellWidth, cellHeight));
            checks++;
        }
        for (int offset = 1; offset < 5; offset++) {
            double cellStartX = (cornerX + 1.0) - cellWidth;
            double cellStartY = (cornerY + 1.0) - ((offset + 1.0) * cellHeight);
            score += blackScore(sampleMarkerCell(image, cellStartX, cellStartY, cellWidth, cellHeight));
            checks++;
        }
        return score / checks;
    }

    private static Colors.Color sampleMarkerCell(BufferedImage image,
                                                 double cellStartX,
                                                 double cellStartY,
                                                 double cellWidth,
                                                 double cellHeight) {
        double marginX = Math.max(0.0, cellWidth * 0.18);
        double marginY = Math.max(0.0, cellHeight * 0.18);
        int startX = clamp((int) Math.floor(cellStartX + marginX), 0, image.getWidth() - 1);
        int endX = clamp((int) Math.ceil((cellStartX + cellWidth) - marginX) - 1, 0, image.getWidth() - 1);
        int startY = clamp((int) Math.floor(cellStartY + marginY), 0, image.getHeight() - 1);
        int endY = clamp((int) Math.ceil((cellStartY + cellHeight) - marginY) - 1, 0, image.getHeight() - 1);
        if (endX < startX) {
            int centerX = clamp((int) Math.round(cellStartX + (cellWidth / 2.0)), 0, image.getWidth() - 1);
            startX = centerX;
            endX = centerX;
        }
        if (endY < startY) {
            int centerY = clamp((int) Math.round(cellStartY + (cellHeight / 2.0)), 0, image.getHeight() - 1);
            startY = centerY;
            endY = centerY;
        }

        long redSum = 0;
        long greenSum = 0;
        long blueSum = 0;
        int count = 0;
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int argb = image.getRGB(x, y);
                redSum += (argb >>> 16) & 0xFF;
                greenSum += (argb >>> 8) & 0xFF;
                blueSum += argb & 0xFF;
                count++;
            }
        }

        return new Colors.Color(
                (int) Math.round(redSum / (double) count),
                (int) Math.round(greenSum / (double) count),
                (int) Math.round(blueSum / (double) count)
        );
    }

    private static boolean isActivePixel(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) {
            return false;
        }

        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return red < ACTIVE_THRESHOLD || green < ACTIVE_THRESHOLD || blue < ACTIVE_THRESHOLD;
    }

    private static double scoreStructure(BufferedImage image, Bounds bounds, int logicalSize) {
        DPReservedMask mask = DPReservedMask.create(logicalSize);
        double score = 0.0;
        int checks = 0;

        for (int y = 0; y < logicalSize; y++) {
            for (int x = 0; x < logicalSize; x++) {
                Colors.Color color = sampleCellAverage(image, bounds, logicalSize, x, y);
                switch (mask.getRole(x, y)) {
                    case TOP_LEFT_MARKER, BOTTOM_RIGHT_MARKER, CONTROL_BLACK, FLOATER_ARM_BLACK,
                            TOP_TIMING_BLACK, RIGHT_TIMING_BLACK -> {
                        score += blackScore(color);
                        checks++;
                    }
                    case TOP_TIMING_WHITE, RIGHT_TIMING_WHITE, FLOATER_CENTER_WHITE -> {
                        score += whiteScore(color);
                        checks++;
                    }
                    case BOOTSTRAP, HEADER, PAYLOAD -> {
                        // These cells are payload- or metadata-bearing, so they
                        // are not useful for geometry scoring here.
                    }
                }
            }
        }

        if (checks == 0) {
            return 0.0;
        }
        return score / checks;
    }

    private static double blackScore(Colors.Color color) {
        return 1.0 - (luminance(color) / 255.0);
    }

    private static double whiteScore(Colors.Color color) {
        return luminance(color) / 255.0;
    }

    private static double luminance(Colors.Color color) {
        return (0.2126 * color.getR()) + (0.7152 * color.getG()) + (0.0722 * color.getB());
    }

    private static Colors.Color[][] sampleLogicalGrid(BufferedImage image, Bounds bounds, int logicalSize) {
        Colors.Color[][] logicalGrid = new Colors.Color[logicalSize][logicalSize];

        for (int y = 0; y < logicalSize; y++) {
            for (int x = 0; x < logicalSize; x++) {
                logicalGrid[y][x] = sampleCellAverage(image, bounds, logicalSize, x, y);
            }
        }
        return logicalGrid;
    }

    private static Colors.Color sampleCellAverage(BufferedImage image,
                                                  Bounds bounds,
                                                  int logicalSize,
                                                  int cellX,
                                                  int cellY) {
        double cellWidth = bounds.widthPx() / (double) logicalSize;
        double cellHeight = bounds.heightPx() / (double) logicalSize;

        int centerX = clamp((int) Math.round(bounds.minX() + ((cellX + 0.5) * cellWidth) - 0.5), bounds.minX(), bounds.maxX());
        int centerY = clamp((int) Math.round(bounds.minY() + ((cellY + 0.5) * cellHeight) - 0.5), bounds.minY(), bounds.maxY());
        int windowRadiusX = Math.max(0, (int) Math.floor(cellWidth * 0.18));
        int windowRadiusY = Math.max(0, (int) Math.floor(cellHeight * 0.18));

        int startX = clamp(centerX - windowRadiusX, bounds.minX(), bounds.maxX());
        int endX = clamp(centerX + windowRadiusX, bounds.minX(), bounds.maxX());
        int startY = clamp(centerY - windowRadiusY, bounds.minY(), bounds.maxY());
        int endY = clamp(centerY + windowRadiusY, bounds.minY(), bounds.maxY());

        long redSum = 0;
        long greenSum = 0;
        long blueSum = 0;
        int count = 0;
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int argb = image.getRGB(x, y);
                redSum += (argb >>> 16) & 0xFF;
                greenSum += (argb >>> 8) & 0xFF;
                blueSum += argb & 0xFF;
                count++;
            }
        }

        if (count == 0) {
            int argb = image.getRGB(centerX, centerY);
            return new Colors.Color((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF);
        }

        return new Colors.Color(
                (int) Math.round(redSum / (double) count),
                (int) Math.round(greenSum / (double) count),
                (int) Math.round(blueSum / (double) count)
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record SampleCandidate(Path imagePath,
                           Colors.Color[][] logicalGrid,
                           int logicalSize,
                           double cellWidthPx,
                           double cellHeightPx,
                           double structureScore,
                           Bounds bounds,
                           int imageWidth,
                           int imageHeight) {}

    private record CornerAnchor(int x, int y, double score) {}

    private static void logSampler(String message) {
        if (SAMPLER_DEBUG_LOGGING) {
            System.out.println("[DPImageSampler] " + message);
        }
    }

    private static String formatDouble(double value) {
        return String.format("%.4f", value);
    }

    private static String formatIntList(List<Integer> values, int maxValues) {
        if (values == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("len=").append(values.size()).append(" [");
        int limit = Math.min(values.size(), Math.max(0, maxValues));
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values.get(i));
        }
        if (values.size() > limit) {
            if (limit > 0) {
                builder.append(", ");
            }
            builder.append("...");
        }
        builder.append(']');
        return builder.toString();
    }

    private static String formatIntArray(int[] values, int maxValues) {
        if (values == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("len=").append(values.length).append(" [");
        int limit = Math.min(values.length, Math.max(0, maxValues));
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values[i]);
        }
        if (values.length > limit) {
            if (limit > 0) {
                builder.append(", ");
            }
            builder.append("...");
        }
        builder.append(']');
        return builder.toString();
    }

    private static String summarizeCandidates(List<SampleCandidate> candidates) {
        if (candidates == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("count=").append(candidates.size()).append(" [");
        int limit = Math.min(candidates.size(), 12);
        for (int i = 0; i < limit; i++) {
            SampleCandidate candidate = candidates.get(i);
            if (i > 0) {
                builder.append("; ");
            }
            builder.append("N=").append(candidate.logicalSize())
                    .append(" score=").append(formatDouble(candidate.structureScore()))
                    .append(" bounds=").append(candidate.bounds());
        }
        if (candidates.size() > limit) {
            if (limit > 0) {
                builder.append("; ");
            }
            builder.append("...");
        }
        builder.append(']');
        return builder.toString();
    }

    record Bounds(int minX, int minY, int maxX, int maxY) {
        int widthPx() {
            return (maxX - minX) + 1;
        }

        int heightPx() {
            return (maxY - minY) + 1;
        }
    }
}
