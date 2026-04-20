package ucadmin.tools.pixelgenerator;

import ucadmin.util.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/**
 * PNG renderer for token-based pixel art.
 *
 * <p>The public API is intentionally small:
 * a caller builds a {@link PixelArt} request object and passes it to
 * {@link #render(PixelArt)}. The renderer validates the request, resolves
 * final divisible output dimensions, renders fully in memory, and performs
 * one final buffered PNG write to the requested output file.</p>
 *
 * <p>Design goals for this first pass:</p>
 * <ul>
 *     <li>String-token grid input ({@code String[][]})</li>
 *     <li>Case-insensitive palette lookup through normalization</li>
 *     <li>Fully transparent cells for {@code "."}, null, and blank strings</li>
 *     <li>No temp files and no staged disk writes</li>
 *     <li>No mutation of the supplied {@link PixelArt} object or its grid</li>
 * </ul>
 */
public final class PixelGenerator {

    private PixelGenerator() {}

    /**
     * Renders a pixel-art request to the exact PNG output path stored on the
     * supplied {@link PixelArt} object.
     *
     * <p>Render behavior:</p>
     * <ul>
     *     <li>Validates the request and grid structure</li>
     *     <li>Normalizes tokens by trimming and lowercasing</li>
     *     <li>Treats null, blank, and {@code "."} tokens as transparent</li>
     *     <li>Rounds preferred width/height to the nearest size evenly divisible
     *     by the grid dimensions, rounding upward on exact ties</li>
     *     <li>Optionally adds a fixed pixel border outside the rendered content</li>
     *     <li>Creates one in-memory ARGB image</li>
     *     <li>Performs one final buffered PNG write to the resolved path</li>
     * </ul>
     *
     * @param art pixel-art request object
     * @return normalized absolute path of the written PNG file
     * @throws PixelGeneratorException if validation fails or the PNG write cannot complete
     */
    public static Path render(PixelArt art) {
        long t0 = System.nanoTime();
        Logger.log(Logger.TAG.SYSTEM, "PixelGenerator.render(begin)");

        if (art == null) {
            throw new PixelGeneratorException("PixelGenerator.render: art cannot be null.");
        }

        String[][] grid = art.getGrid();
        GridSpec spec = validateGrid(grid);
        Path outputPath = resolveOutputPath(art.getOutputPath());
        BorderSpec border = resolveBorder(art);

        int contentWidth = resolveNearestDivisibleSize(art.getPreferredWidth(), spec.columns);
        int contentHeight = resolveNearestDivisibleSize(art.getPreferredHeight(), spec.rows);
        int cellWidth = contentWidth / spec.columns;
        int cellHeight = contentHeight / spec.rows;
        int finalWidth = contentWidth + (border.sizePx * 2);
        int finalHeight = contentHeight + (border.sizePx * 2);

        Logger.log(Logger.TAG.DEBUG,
                "PixelGenerator.render: rows=" + spec.rows +
                        " cols=" + spec.columns +
                        " preferredWidth=" + art.getPreferredWidth() +
                        " preferredHeight=" + art.getPreferredHeight() +
                        " contentWidth=" + contentWidth +
                        " contentHeight=" + contentHeight +
                        " finalWidth=" + finalWidth +
                        " finalHeight=" + finalHeight +
                        " borderSize=" + border.sizePx +
                        " cellWidth=" + cellWidth +
                        " cellHeight=" + cellHeight);

        BufferedImage image = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_ARGB);
        paintBorder(image, finalWidth, finalHeight, border);
        paintGrid(image, grid, spec.rows, spec.columns, cellWidth, cellHeight, border.sizePx);
        writePng(image, outputPath);

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        Logger.log(Logger.TAG.INFO,
                "PixelGenerator.render: wrote PNG to " + outputPath + " (elapsedMs=" + elapsedMs + ")");
        Logger.log(Logger.TAG.SYSTEM, "PixelGenerator.render(end)");
        return outputPath;
    }

    /**
     * Validates the structural rules for the token grid.
     *
     * <p>The grid must be a proper rectangular 2D array with at least one
     * row and one column. Token-level validation is deferred until paint
     * time so we do not allocate unnecessary intermediate structures.</p>
     *
     * @param grid source token grid
     * @return resolved grid dimensions
     * @throws PixelGeneratorException if the grid shape is invalid
     */
    private static GridSpec validateGrid(String[][] grid) {
        if (grid == null) {
            throw new PixelGeneratorException("PixelGenerator.validateGrid: grid cannot be null.");
        }
        if (grid.length == 0) {
            throw new PixelGeneratorException("PixelGenerator.validateGrid: grid must contain at least one row.");
        }

        int columns = -1;
        for (int y = 0; y < grid.length; y++) {
            String[] row = grid[y];
            if (row == null) {
                throw new PixelGeneratorException("PixelGenerator.validateGrid: row " + y + " is null.");
            }
            if (row.length == 0) {
                throw new PixelGeneratorException("PixelGenerator.validateGrid: row " + y + " has zero columns.");
            }
            if (columns == -1) {
                columns = row.length;
            } else if (row.length != columns) {
                throw new PixelGeneratorException(
                        "PixelGenerator.validateGrid: row " + y + " length " + row.length +
                                " does not match expected column count " + columns + "."
                );
            }
        }

        if (columns <= 0) {
            throw new PixelGeneratorException("PixelGenerator.validateGrid: grid must contain at least one column.");
        }

        return new GridSpec(grid.length, columns);
    }

    /**
     * Resolves, normalizes, and validates the final output path.
     *
     * <p>Directory creation is intentionally not performed here. The caller
     * must provide an existing parent directory and an exact PNG file name.
     * This keeps the behavior explicit and avoids hidden filesystem side
     * effects beyond the single final file write.</p>
     *
     * @param rawOutputPath caller-supplied output path
     * @return normalized absolute output path
     * @throws PixelGeneratorException if the path is malformed or unusable
     */
    private static Path resolveOutputPath(String rawOutputPath) {
        if (rawOutputPath == null || rawOutputPath.isBlank()) {
            throw new PixelGeneratorException("PixelGenerator.resolveOutputPath: output path cannot be null or blank.");
        }

        final Path path;
        try {
            path = Path.of(rawOutputPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveOutputPath: invalid output path '" + rawOutputPath + "'.", e
            );
        }

        Path parent = path.getParent();
        if (parent == null) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveOutputPath: output path must include an existing parent directory."
            );
        }
        if (!Files.exists(parent)) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveOutputPath: parent directory does not exist: " + parent
            );
        }
        if (!Files.isDirectory(parent)) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveOutputPath: parent path is not a directory: " + parent
            );
        }

        Path fileName = path.getFileName();
        if (fileName == null || fileName.toString().isBlank()) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveOutputPath: output path must include a PNG file name."
            );
        }
        if (!fileName.toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveOutputPath: output file must end in .png: " + fileName
            );
        }
        if (Files.exists(path) && Files.isDirectory(path)) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveOutputPath: output path points to a directory, not a file: " + path
            );
        }

        return path;
    }

    /**
     * Resolves border settings from the request object.
     *
     * <p>The border is defined in pixels and is added outside the rendered
     * content area. A zero-sized border is treated as disabled and does not
     * require a border color token.</p>
     *
     * @param art pixel-art request
     * @return immutable resolved border settings
     * @throws PixelGeneratorException if the border configuration is invalid
     */
    private static BorderSpec resolveBorder(PixelArt art) {
        int borderSize = art.getBorderSize();
        if (borderSize < 0) {
            throw new PixelGeneratorException("PixelGenerator.resolveBorder: borderSize must be >= 0.");
        }
        if (borderSize == 0) {
            return new BorderSpec(0, 0x00000000, false);
        }

        String borderColor = art.getBorderColor();
        if (borderColor == null || borderColor.isBlank()) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveBorder: borderColor is required when borderSize > 0."
            );
        }

        int argb = PixelTokenSupport.resolveNamedColor(borderColor, "borderColor");
        return new BorderSpec(borderSize, argb, true);
    }

    /**
     * Rounds a preferred size to the nearest positive size evenly divisible
     * by the supplied divisor.
     *
     * <p>If the preferred size falls exactly halfway between a lower and
     * upper divisible candidate, the method rounds upward. The result is
     * never allowed to drop below one full divisor because a zero-sized
     * image dimension is invalid.</p>
     *
     * @param preferred preferred size in pixels
     * @param divisor grid row or column count
     * @return nearest positive divisible size
     * @throws PixelGeneratorException if preferred or divisor is invalid
     */
    private static int resolveNearestDivisibleSize(int preferred, int divisor) {
        if (preferred <= 0) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveNearestDivisibleSize: preferred size must be > 0."
            );
        }
        if (divisor <= 0) {
            throw new PixelGeneratorException(
                    "PixelGenerator.resolveNearestDivisibleSize: divisor must be > 0."
            );
        }

        int lower = (preferred / divisor) * divisor;
        int upper = lower + divisor;

        if (lower <= 0) {
            return upper;
        }
        if (preferred == lower) {
            return lower;
        }

        int lowerDelta = preferred - lower;
        int upperDelta = upper - preferred;
        return (upperDelta <= lowerDelta) ? upper : lower;
    }

    /**
     * Paints the token grid into the in-memory image.
     *
     * <p>Each source cell expands into a rectangular block of pixels whose
     * width and height are shared across the entire render. Token lookup is
     * performed during painting so validation and rendering happen in one
     * pass without extra normalized-grid allocations.</p>
     *
     * @param image target image buffer
     * @param grid source token grid
     * @param rows total row count
     * @param columns total column count
     * @param cellWidth width of each rendered cell in pixels
     * @param cellHeight height of each rendered cell in pixels
     * @throws PixelGeneratorException if a token is unknown
     */
    private static void paintGrid(BufferedImage image,
                                  String[][] grid,
                                  int rows,
                                  int columns,
                                  int cellWidth,
                                  int cellHeight,
                                  int borderOffsetPx) {
        for (int y = 0; y < rows; y++) {
            String[] row = grid[y];
            for (int x = 0; x < columns; x++) {
                int argb = PixelTokenSupport.resolveColor(row[x], x, y);
                int startX = borderOffsetPx + (x * cellWidth);
                int startY = borderOffsetPx + (y * cellHeight);

                for (int py = 0; py < cellHeight; py++) {
                    for (int px = 0; px < cellWidth; px++) {
                        image.setRGB(startX + px, startY + py, argb);
                    }
                }
            }
        }
    }

    /**
     * Paints the fixed pixel border into the output image before content is drawn.
     *
     * <p>If no border is enabled, this method does nothing. When a border is
     * enabled, the entire image is first filled with the border color and the
     * content grid is then drawn inset from each edge by the configured
     * border size.</p>
     *
     * @param image target image buffer
     * @param width total output width in pixels
     * @param height total output height in pixels
     * @param border resolved border settings
     */
    private static void paintBorder(BufferedImage image, int width, int height, BorderSpec border) {
        if (!border.enabled) {
            return;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, border.argb);
            }
        }
    }

    /**
     * Writes the rendered image directly to the final PNG file using one
     * buffered output stream.
     *
     * <p>No temporary file or staged write is used. The image is encoded and
     * written once to the resolved final output path.</p>
     *
     * @param image rendered ARGB image
     * @param outputPath final PNG output path
     * @throws PixelGeneratorException if the write fails or PNG encoding is unavailable
     */
    private static void writePng(BufferedImage image, Path outputPath) {
        try (OutputStream fileOut = Files.newOutputStream(
                outputPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
             BufferedOutputStream bufferedOut = new BufferedOutputStream(fileOut)) {

            boolean ok = ImageIO.write(image, "png", bufferedOut);
            bufferedOut.flush();
            if (!ok) {
                throw new PixelGeneratorException(
                        "PixelGenerator.writePng: no PNG image writer is available for output path " + outputPath + "."
                );
            }
        } catch (IOException e) {
            throw new PixelGeneratorException(
                    "PixelGenerator.writePng: failed to write PNG to " + outputPath + ".", e
            );
        }
    }

    /**
     * Minimal immutable grid-dimension snapshot used during rendering.
     */
    private static final class GridSpec {
        private final int rows;
        private final int columns;

        /**
         * Creates a dimension snapshot for the validated grid.
         *
         * @param rows total row count
         * @param columns total column count
         */
        private GridSpec(int rows, int columns) {
            this.rows = rows;
            this.columns = columns;
        }
    }

    /**
     * Minimal immutable border snapshot used during rendering.
     */
    private static final class BorderSpec {
        private final int sizePx;
        private final int argb;
        private final boolean enabled;

        /**
         * Creates resolved border settings for one render operation.
         *
         * @param sizePx border thickness in pixels
         * @param argb packed ARGB border color
         * @param enabled true when the border should be painted
         */
        private BorderSpec(int sizePx, int argb, boolean enabled) {
            this.sizePx = sizePx;
            this.argb = argb;
            this.enabled = enabled;
        }
    }
}
