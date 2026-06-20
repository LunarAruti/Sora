package sora.tools.pixelgenerator;

import java.nio.file.Path;
import java.util.ArrayDeque;

/**
 * Mutable grid-authoring helper layered on top of the pixel-generator render
 * contract.
 *
 * <p>This class exists to make manual pixel-art creation easier without
 * increasing the public complexity of {@link PixelArt}. The builder owns a
 * mutable token grid plus the same render metadata required by
 * {@link PixelGenerator}. When the caller is ready to render, the builder can
 * produce a {@link PixelArt} view or be passed directly into the renderer.</p>
 *
 * <p>This is the main authoring API when you want to draw the image in code.
 * Typical flow: create the builder, call methods such as {@link #setCell(int, int, String)},
 * {@link #drawLine(int, int, int, int, String)}, or
 * {@link #fillRect(int, int, int, int, String)}, then call {@link #render()}.</p>
 *
 * <p>Builder state breaks into two parts:</p>
 * <ul>
 *     <li>grid state: the mutable {@code String[][]} token canvas that the
 *     caller edits directly through helper methods</li>
 *     <li>render state: preferred output width, preferred output height,
 *     output path, border size, and border color, which are the same settings
 *     eventually needed by {@link PixelArt}</li>
 * </ul>
 *
 * <p>The grid dimensions are fixed after construction, but the render metadata
 * can be changed later through setters. That makes the builder more forgiving
 * when a caller wants to change output size, output file, or border settings
 * without rebuilding the art grid from scratch.</p>
 */
public final class PixelGridBuilder {

    private final String[][] grid;
    private final int gridWidth;
    private final int gridHeight;
    private int preferredWidth;
    private int preferredHeight;
    private String outputPath;
    private int borderSize;
    private String borderColor;

    /**
     * Creates a new mutable pixel-grid builder.
     *
     * <p>Constructor arguments intentionally mirror the full render contract so
     * callers do not need to manually assemble a separate {@link PixelArt}
     * object later. All new cells start as the transparent token {@code "."}.
     * A border is optional and follows the same semantics as {@link PixelArt}:
     * it is measured in pixels, applied outside the content area, and its color
     * is resolved through the shared fixed palette.</p>
     *
     * @param gridWidth grid width in cells; must be greater than zero
     * @param gridHeight grid height in cells; must be greater than zero
     * @param preferredWidth preferred render width in pixels; must be greater than zero
     * @param preferredHeight preferred render height in pixels; must be greater than zero
     * @param outputPath exact final PNG output path; must not be null or blank
     * @param borderSize optional border thickness in pixels; must be zero or greater
     * @param borderColor border token when borderSize is greater than zero
     * @throws IllegalArgumentException if any constructor argument is invalid
     */
    public PixelGridBuilder(int gridWidth,
                            int gridHeight,
                            int preferredWidth,
                            int preferredHeight,
                            String outputPath,
                            int borderSize,
                            String borderColor) {
        if (gridWidth <= 0) {
            throw new IllegalArgumentException("PixelGridBuilder: gridWidth must be > 0.");
        }
        if (gridHeight <= 0) {
            throw new IllegalArgumentException("PixelGridBuilder: gridHeight must be > 0.");
        }

        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.grid = createTransparentGrid(gridWidth, gridHeight);
        setPreferredSize(preferredWidth, preferredHeight);
        setOutputPath(outputPath);
        setBorder(borderSize, borderColor);
    }

    /**
     * Returns the builder grid width in cells.
     *
     * @return grid width in cells
     */
    public int getGridWidth() {
        return gridWidth;
    }

    /**
     * Returns the builder grid height in cells.
     *
     * @return grid height in cells
     */
    public int getGridHeight() {
        return gridHeight;
    }

    /**
     * Returns the preferred render width that will be used when this builder
     * is converted into {@link PixelArt}.
     *
     * @return preferred render width in pixels
     */
    public int getPreferredWidth() {
        return preferredWidth;
    }

    /**
     * Updates the preferred rendered content width in pixels.
     *
     * <p>This value is part of the builder's render metadata and does not
     * change the number of editable grid columns.</p>
     *
     * @param preferredWidth preferred content width in pixels; must be greater than zero
     * @throws IllegalArgumentException if preferredWidth is not positive
     */
    public void setPreferredWidth(int preferredWidth) {
        validatePreferredWidth(preferredWidth);
        this.preferredWidth = preferredWidth;
    }

    /**
     * Returns the preferred render height that will be used when this builder
     * is converted into {@link PixelArt}.
     *
     * @return preferred render height in pixels
     */
    public int getPreferredHeight() {
        return preferredHeight;
    }

    /**
     * Updates the preferred rendered content height in pixels.
     *
     * <p>This value is part of the builder's render metadata and does not
     * change the number of editable grid rows.</p>
     *
     * @param preferredHeight preferred content height in pixels; must be greater than zero
     * @throws IllegalArgumentException if preferredHeight is not positive
     */
    public void setPreferredHeight(int preferredHeight) {
        validatePreferredHeight(preferredHeight);
        this.preferredHeight = preferredHeight;
    }

    /**
     * Returns the exact output path that will be used when this builder is
     * converted into {@link PixelArt}.
     *
     * @return requested output path
     */
    public String getOutputPath() {
        return outputPath;
    }

    /**
     * Updates the exact final PNG output path used when the builder is
     * rendered or converted into {@link PixelArt}.
     *
     * @param outputPath exact target PNG path; must not be null or blank
     * @throws IllegalArgumentException if outputPath is null or blank
     */
    public void setOutputPath(String outputPath) {
        validateOutputPath(outputPath);
        this.outputPath = outputPath;
    }

    /**
     * Returns the configured border size in pixels.
     *
     * @return border thickness in pixels
     */
    public int getBorderSize() {
        return borderSize;
    }

    /**
     * Updates the render-time border thickness in pixels.
     *
     * @param borderSize border thickness in pixels; must be zero or greater
     * @throws IllegalArgumentException if the requested border state is invalid
     */
    public void setBorderSize(int borderSize) {
        validateBorderState(borderSize, borderColor);
        this.borderSize = borderSize;
    }

    /**
     * Returns the configured border color token, or null when no border is
     * requested.
     *
     * @return border color token or null
     */
    public String getBorderColor() {
        return borderColor;
    }

    /**
     * Updates the render-time border token.
     *
     * <p>If a positive border size is already configured, the supplied token
     * must be non-blank and resolvable by the shared fixed palette.</p>
     *
     * @param borderColor border token, or null when no border is needed
     * @throws IllegalArgumentException if the requested border state is invalid
     */
    public void setBorderColor(String borderColor) {
        validateBorderState(borderSize, borderColor);
        if (borderColor != null && !borderColor.isBlank()) {
            PixelTokenSupport.resolveNamedColor(borderColor, "builder borderColor");
        }
        this.borderColor = borderColor;
    }

    /**
     * Updates both preferred rendered dimensions together.
     *
     * @param preferredWidth preferred content width in pixels; must be greater than zero
     * @param preferredHeight preferred content height in pixels; must be greater than zero
     * @throws IllegalArgumentException if either dimension is invalid
     */
    public void setPreferredSize(int preferredWidth, int preferredHeight) {
        validatePreferredWidth(preferredWidth);
        validatePreferredHeight(preferredHeight);
        this.preferredWidth = preferredWidth;
        this.preferredHeight = preferredHeight;
    }

    /**
     * Updates both border fields together.
     *
     * <p>This is the recommended way to change border configuration because it
     * validates the requested size/color pair as one unit before applying it.</p>
     *
     * @param borderSize border thickness in pixels; must be zero or greater
     * @param borderColor border token when borderSize is greater than zero
     * @throws IllegalArgumentException if the requested border state is invalid
     */
    public void setBorder(int borderSize, String borderColor) {
        validateBorderState(borderSize, borderColor);
        if (borderColor != null && !borderColor.isBlank()) {
            PixelTokenSupport.resolveNamedColor(borderColor, "builder borderColor");
        }
        this.borderSize = borderSize;
        this.borderColor = borderColor;
    }

    /**
     * Returns the current token stored at one grid cell.
     *
     * @param x zero-based grid x coordinate
     * @param y zero-based grid y coordinate
     * @return stored token in normalized form
     * @throws IllegalArgumentException if the coordinate is outside the grid
     */
    public String getCell(int x, int y) {
        validateCoordinate(x, y);
        return grid[y][x];
    }

    /**
     * Stores a token at one grid cell.
     *
     * <p>Tokens are normalized immediately so the builder grid always stays in
     * the same canonical form expected by the renderer. Unknown non-transparent
     * tokens throw immediately instead of being deferred until render time.</p>
     *
     * @param x zero-based grid x coordinate
     * @param y zero-based grid y coordinate
     * @param token source token to store
     * @throws IllegalArgumentException if the coordinate is outside the grid
     * @throws PixelGeneratorException if the token is not part of the fixed palette
     */
    public void setCell(int x, int y, String token) {
        validateCoordinate(x, y);
        grid[y][x] = normalizeAndValidateToken(token);
    }

    /**
     * Clears one cell back to the transparent token.
     *
     * @param x zero-based grid x coordinate
     * @param y zero-based grid y coordinate
     * @throws IllegalArgumentException if the coordinate is outside the grid
     */
    public void clearCell(int x, int y) {
        validateCoordinate(x, y);
        grid[y][x] = PixelTokenSupport.TRANSPARENT_TOKEN;
    }

    /**
     * Clears the entire builder grid back to transparency.
     */
    public void clear() {
        fillBackground(PixelTokenSupport.TRANSPARENT_TOKEN);
    }

    /**
     * Fills the entire builder grid with one token.
     *
     * @param token token to apply to every cell
     * @throws PixelGeneratorException if the token is not part of the fixed palette
     */
    public void fillBackground(String token) {
        String normalizedToken = normalizeAndValidateToken(token);
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                grid[y][x] = normalizedToken;
            }
        }
    }

    /**
     * Flood-fills a connected region starting from one cell, similar to the
     * paint-bucket tool in a normal art program.
     *
     * <p>The fill only replaces cells that match the normalized token already
     * present at the starting coordinate. The search uses 4-directional
     * adjacency: left, right, up, and down.</p>
     *
     * @param startX zero-based starting x coordinate
     * @param startY zero-based starting y coordinate
     * @param token replacement token
     * @throws IllegalArgumentException if the coordinate is outside the grid
     * @throws PixelGeneratorException if the token is not part of the fixed palette
     */
    public void fill(int startX, int startY, String token) {
        validateCoordinate(startX, startY);

        String replacement = normalizeAndValidateToken(token);
        String target = grid[startY][startX];
        if (target.equals(replacement)) {
            return;
        }

        ArrayDeque<GridPoint> queue = new ArrayDeque<>();
        queue.addLast(new GridPoint(startX, startY));

        while (!queue.isEmpty()) {
            GridPoint point = queue.removeFirst();
            if (!isInBounds(point.x, point.y)) {
                continue;
            }
            if (!grid[point.y][point.x].equals(target)) {
                continue;
            }

            grid[point.y][point.x] = replacement;
            queue.addLast(new GridPoint(point.x - 1, point.y));
            queue.addLast(new GridPoint(point.x + 1, point.y));
            queue.addLast(new GridPoint(point.x, point.y - 1));
            queue.addLast(new GridPoint(point.x, point.y + 1));
        }
    }

    /**
     * Replaces every cell whose normalized token matches {@code fromToken}
     * with {@code toToken}.
     *
     * @param fromToken token to replace
     * @param toToken replacement token
     * @throws PixelGeneratorException if either token is not part of the fixed palette
     */
    public void replace(String fromToken, String toToken) {
        String from = normalizeAndValidateToken(fromToken);
        String to = normalizeAndValidateToken(toToken);
        if (from.equals(to)) {
            return;
        }

        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                if (grid[y][x].equals(from)) {
                    grid[y][x] = to;
                }
            }
        }
    }

    /**
     * Draws the closest straight pixel line possible between two grid points.
     *
     * <p>This uses an integer Bresenham-style traversal so the line can be
     * drawn in any direction without restricting the caller to purely vertical
     * or horizontal movement.</p>
     *
     * @param x1 start x coordinate
     * @param y1 start y coordinate
     * @param x2 end x coordinate
     * @param y2 end y coordinate
     * @param token line token
     * @throws IllegalArgumentException if either endpoint is outside the grid
     * @throws PixelGeneratorException if the token is not part of the fixed palette
     */
    public void drawLine(int x1, int y1, int x2, int y2, String token) {
        validateCoordinate(x1, y1);
        validateCoordinate(x2, y2);

        String normalizedToken = normalizeAndValidateToken(token);

        int currentX = x1;
        int currentY = y1;
        int deltaX = Math.abs(x2 - x1);
        int deltaY = Math.abs(y2 - y1);
        int stepX = Integer.compare(x2, x1);
        int stepY = Integer.compare(y2, y1);
        int error = deltaX - deltaY;

        while (true) {
            grid[currentY][currentX] = normalizedToken;
            if (currentX == x2 && currentY == y2) {
                return;
            }

            int doubledError = error * 2;
            if (doubledError > -deltaY) {
                error -= deltaY;
                currentX += stepX;
            }
            if (doubledError < deltaX) {
                error += deltaX;
                currentY += stepY;
            }
        }
    }

    /**
     * Draws a rectangular outline between two corner coordinates.
     *
     * <p>The two corners may be provided in any order. The builder normalizes
     * them internally into inclusive minimum/maximum bounds before drawing.</p>
     *
     * @param x1 first corner x coordinate
     * @param y1 first corner y coordinate
     * @param x2 second corner x coordinate
     * @param y2 second corner y coordinate
     * @param token outline token
     * @throws IllegalArgumentException if either corner is outside the grid
     * @throws PixelGeneratorException if the token is not part of the fixed palette
     */
    public void drawRect(int x1, int y1, int x2, int y2, String token) {
        validateCoordinate(x1, y1);
        validateCoordinate(x2, y2);

        String normalizedToken = normalizeAndValidateToken(token);
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        for (int drawX = minX; drawX <= maxX; drawX++) {
            grid[minY][drawX] = normalizedToken;
            grid[maxY][drawX] = normalizedToken;
        }
        for (int drawY = minY; drawY <= maxY; drawY++) {
            grid[drawY][minX] = normalizedToken;
            grid[drawY][maxX] = normalizedToken;
        }
    }

    /**
     * Fills a rectangular region between two corner coordinates.
     *
     * <p>The two corners may be provided in any order. The builder normalizes
     * them internally into inclusive minimum/maximum bounds before filling.</p>
     *
     * @param x1 first corner x coordinate
     * @param y1 first corner y coordinate
     * @param x2 second corner x coordinate
     * @param y2 second corner y coordinate
     * @param token fill token
     * @throws IllegalArgumentException if either corner is outside the grid
     * @throws PixelGeneratorException if the token is not part of the fixed palette
     */
    public void fillRect(int x1, int y1, int x2, int y2, String token) {
        validateCoordinate(x1, y1);
        validateCoordinate(x2, y2);

        String normalizedToken = normalizeAndValidateToken(token);
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        for (int drawY = minY; drawY <= maxY; drawY++) {
            for (int drawX = minX; drawX <= maxX; drawX++) {
                grid[drawY][drawX] = normalizedToken;
            }
        }
    }

    /**
     * Draws a border around the entire builder grid.
     *
     * <p>The border is drawn manually into the token grid rather than using the
     * render-time border support from {@link PixelArt}. This is useful when the
     * caller wants the border to be part of the art itself.</p>
     *
     * @param token border token
     * @param thickness border thickness in cells
     * @throws IllegalArgumentException if thickness is not positive
     * @throws PixelGeneratorException if the token is not part of the fixed palette
     */
    public void drawBorder(String token, int thickness) {
        if (thickness <= 0) {
            throw new IllegalArgumentException("PixelGridBuilder: border thickness must be > 0.");
        }

        String normalizedToken = normalizeAndValidateToken(token);
        int layers = Math.min(thickness, Math.min(gridWidth, gridHeight));

        for (int layer = 0; layer < layers; layer++) {
            int left = layer;
            int top = layer;
            int right = gridWidth - 1 - layer;
            int bottom = gridHeight - 1 - layer;

            if (left > right || top > bottom) {
                return;
            }

            for (int x = left; x <= right; x++) {
                grid[top][x] = normalizedToken;
                grid[bottom][x] = normalizedToken;
            }
            for (int y = top; y <= bottom; y++) {
                grid[y][left] = normalizedToken;
                grid[y][right] = normalizedToken;
            }
        }
    }

    /**
     * Copies a rectangular region of the current builder grid.
     *
     * <p>The returned grid is a standalone snapshot and can be reused later
     * with {@link #pasteRegion(int, int, String[][])}.</p>
     *
     * @param x top-left source x coordinate
     * @param y top-left source y coordinate
     * @param width region width in cells
     * @param height region height in cells
     * @return deep copy of the requested region
     * @throws IllegalArgumentException if the region is invalid or outside the grid
     */
    public String[][] copyRegion(int x, int y, int width, int height) {
        validateRegion(x, y, width, height);

        String[][] copy = new String[height][width];
        for (int row = 0; row < height; row++) {
            System.arraycopy(grid[y + row], x, copy[row], 0, width);
        }
        return copy;
    }

    /**
     * Pastes a previously copied region into the builder grid.
     *
     * <p>The pasted region must fit entirely within the destination grid. Each
     * pasted token is normalized and validated so malformed external grids are
     * rejected immediately.</p>
     *
     * @param destX top-left destination x coordinate
     * @param destY top-left destination y coordinate
     * @param region source region to paste
     * @throws IllegalArgumentException if the destination or source region is invalid
     * @throws PixelGeneratorException if any pasted token is not part of the fixed palette
     */
    public void pasteRegion(int destX, int destY, String[][] region) {
        RegionSpec regionSpec = validateExternalRegion(region);
        validateRegion(destX, destY, regionSpec.width, regionSpec.height);

        for (int y = 0; y < regionSpec.height; y++) {
            for (int x = 0; x < regionSpec.width; x++) {
                grid[destY + y][destX + x] = normalizeAndValidateToken(region[y][x]);
            }
        }
    }

    /**
     * Shifts the entire builder grid by the supplied deltas.
     *
     * <p>Cells shifted outside the canvas are discarded. Newly exposed cells
     * become transparent.</p>
     *
     * @param deltaX horizontal shift in cells; positive moves right
     * @param deltaY vertical shift in cells; positive moves down
     */
    public void shift(int deltaX, int deltaY) {
        if (deltaX == 0 && deltaY == 0) {
            return;
        }

        String[][] shifted = createTransparentGrid(gridWidth, gridHeight);
        for (int sourceY = 0; sourceY < gridHeight; sourceY++) {
            for (int sourceX = 0; sourceX < gridWidth; sourceX++) {
                int destX = sourceX + deltaX;
                int destY = sourceY + deltaY;
                if (isInBounds(destX, destY)) {
                    shifted[destY][destX] = grid[sourceY][sourceX];
                }
            }
        }

        for (int y = 0; y < gridHeight; y++) {
            System.arraycopy(shifted[y], 0, grid[y], 0, gridWidth);
        }
    }

    /**
     * Returns a defensive grid copy suitable for callers that want the raw
     * token grid without exposing the builder's live mutable backing array.
     *
     * @return deep copy of the current token grid
     */
    public String[][] copyGrid() {
        String[][] copy = new String[gridHeight][gridWidth];
        for (int y = 0; y < gridHeight; y++) {
            System.arraycopy(grid[y], 0, copy[y], 0, gridWidth);
        }
        return copy;
    }

    /**
     * Builds a fresh {@link PixelArt} snapshot from the current builder state.
     *
     * <p>A defensive grid copy is used so callers can keep drawing into the
     * builder after retrieving the render request without accidentally changing
     * the already-created {@link PixelArt} instance. Use this when you want to
     * leave the mutable builder API and hand the result to another method that
     * expects a plain {@link PixelArt} request.</p>
     *
     * @return new PixelArt snapshot representing the builder's current state
     */
    public PixelArt toPixelArt() {
        return new PixelArt(
                copyGrid(),
                preferredWidth,
                preferredHeight,
                outputPath,
                borderSize,
                borderColor
        );
    }

    /**
     * Renders the builder's current contents using the normal pixel-generator
     * render path.
     *
     * <p>This is the usual finish step after you have populated the grid with
     * builder draw commands.</p>
     *
     * @return final rendered PNG path
     * @throws PixelGeneratorException if validation or the write fails
     */
    public Path render() {
        return PixelGenerator.render(this);
    }

    /**
     * Creates a new transparent grid in canonical token form.
     */
    private static String[][] createTransparentGrid(int gridWidth, int gridHeight) {
        String[][] out = new String[gridHeight][gridWidth];
        for (int y = 0; y < gridHeight; y++) {
            for (int x = 0; x < gridWidth; x++) {
                out[y][x] = PixelTokenSupport.TRANSPARENT_TOKEN;
            }
        }
        return out;
    }

    /**
     * Validates a single grid coordinate.
     */
    private void validateCoordinate(int x, int y) {
        if (x < 0 || x >= gridWidth || y < 0 || y >= gridHeight) {
            throw new IllegalArgumentException(
                    "PixelGridBuilder: coordinate out of bounds (x=" + x + ", y=" + y +
                            ", width=" + gridWidth + ", height=" + gridHeight + ")."
            );
        }
    }

    /**
     * Validates a rectangular region that must fit entirely within the grid.
     */
    private void validateRegion(int x, int y, int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("PixelGridBuilder: region width must be > 0.");
        }
        if (height <= 0) {
            throw new IllegalArgumentException("PixelGridBuilder: region height must be > 0.");
        }
        validateCoordinate(x, y);
        validateCoordinate(x + width - 1, y + height - 1);
    }

    /**
     * Returns true when a coordinate is inside the grid bounds.
     */
    private boolean isInBounds(int x, int y) {
        return x >= 0 && x < gridWidth && y >= 0 && y < gridHeight;
    }

    /**
     * Validates a pasted external region and returns its dimensions.
     */
    private static RegionSpec validateExternalRegion(String[][] region) {
        if (region == null) {
            throw new IllegalArgumentException("PixelGridBuilder: region cannot be null.");
        }
        if (region.length == 0) {
            throw new IllegalArgumentException("PixelGridBuilder: region must contain at least one row.");
        }

        int width = -1;
        for (int y = 0; y < region.length; y++) {
            String[] row = region[y];
            if (row == null) {
                throw new IllegalArgumentException("PixelGridBuilder: region row " + y + " is null.");
            }
            if (row.length == 0) {
                throw new IllegalArgumentException("PixelGridBuilder: region row " + y + " has zero columns.");
            }
            if (width == -1) {
                width = row.length;
            } else if (row.length != width) {
                throw new IllegalArgumentException(
                        "PixelGridBuilder: region row " + y + " length " + row.length +
                                " does not match expected width " + width + "."
                );
            }
        }

        return new RegionSpec(width, region.length);
    }

    /**
     * Normalizes a token and confirms that the shared pixel-generator palette
     * can resolve it.
     */
    private static String normalizeAndValidateToken(String token) {
        String normalized = PixelTokenSupport.normalize(token);
        PixelTokenSupport.resolveNamedColor(normalized, "builder token");
        return normalized;
    }

    /**
     * Validates preferred width.
     */
    private static void validatePreferredWidth(int preferredWidth) {
        if (preferredWidth <= 0) {
            throw new IllegalArgumentException("PixelGridBuilder: preferredWidth must be > 0.");
        }
    }

    /**
     * Validates preferred height.
     */
    private static void validatePreferredHeight(int preferredHeight) {
        if (preferredHeight <= 0) {
            throw new IllegalArgumentException("PixelGridBuilder: preferredHeight must be > 0.");
        }
    }

    /**
     * Validates output path presence.
     */
    private static void validateOutputPath(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("PixelGridBuilder: outputPath cannot be null or blank.");
        }
    }

    /**
     * Validates border size/color consistency.
     */
    private static void validateBorderState(int borderSize, String borderColor) {
        if (borderSize < 0) {
            throw new IllegalArgumentException("PixelGridBuilder: borderSize must be >= 0.");
        }
        if (borderSize > 0 && (borderColor == null || borderColor.isBlank())) {
            throw new IllegalArgumentException("PixelGridBuilder: borderColor is required when borderSize > 0.");
        }
    }

    /**
     * Small immutable grid coordinate used by helper algorithms such as flood
     * fill.
     */
    private record GridPoint(int x, int y) {}

    /**
     * Small immutable width/height snapshot for copied or pasted regions.
     */
    private record RegionSpec(int width, int height) {}
}
