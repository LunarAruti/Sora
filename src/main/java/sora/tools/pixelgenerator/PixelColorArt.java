package sora.tools.pixelgenerator;

import sora.tools.Colors;

/**
 * Reusable render request object for direct RGB pixel art.
 *
 * <p>This is the parallel request type to {@link PixelArt}. Instead of a
 * token grid backed by the fixed palette, this request stores exact
 * {@link Colors.Color} values for each cell and renders those values directly
 * into the PNG.</p>
 *
 * <p>Use this only when the shared token palette is not enough and you need
 * exact per-cell RGB control. If palette tokens are acceptable, the normal
 * token-grid flow through {@link PixelArt} or {@link PixelGridBuilder} is the
 * simpler path.</p>
 *
 * <p>Null grid cells are treated as fully transparent so callers can still
 * leave holes in the image when needed. Non-null cells are written as opaque
 * {@code 0xFFRRGGBB} pixels using the exact RGB values from the supplied
 * {@link Colors.Color} objects.</p>
 */
public final class PixelColorArt {

    private final Colors.Color[][] grid;
    private int preferredWidth;
    private int preferredHeight;
    private String outputPath;
    private int borderSize;
    private Colors.Color borderColor;

    /**
     * Creates a direct-color pixel-art render request without a border.
     *
     * @param grid source color grid; must not be null and must contain at least one row
     * @param preferredWidth preferred content width in pixels; must be greater than zero
     * @param preferredHeight preferred content height in pixels; must be greater than zero
     * @param outputPath exact target file path for the PNG output
     * @throws IllegalArgumentException if any top-level argument is invalid
     */
    public PixelColorArt(Colors.Color[][] grid,
                         int preferredWidth,
                         int preferredHeight,
                         String outputPath) {
        this(grid, preferredWidth, preferredHeight, outputPath, 0, null);
    }

    /**
     * Creates a direct-color pixel-art render request with an optional border.
     *
     * @param grid source color grid; must not be null and must contain at least one row
     * @param preferredWidth preferred content width in pixels; must be greater than zero
     * @param preferredHeight preferred content height in pixels; must be greater than zero
     * @param outputPath exact target file path for the PNG output
     * @param borderSize border thickness in pixels on each side; must be zero or greater
     * @param borderColor exact border color; required when borderSize is greater than zero
     * @throws IllegalArgumentException if any top-level argument is invalid
     */
    public PixelColorArt(Colors.Color[][] grid,
                         int preferredWidth,
                         int preferredHeight,
                         String outputPath,
                         int borderSize,
                         Colors.Color borderColor) {
        if (grid == null) {
            throw new IllegalArgumentException("PixelColorArt: grid cannot be null.");
        }
        if (grid.length == 0) {
            throw new IllegalArgumentException("PixelColorArt: grid must contain at least one row.");
        }

        this.grid = grid;
        setPreferredWidth(preferredWidth);
        setPreferredHeight(preferredHeight);
        setOutputPath(outputPath);
        setBorder(borderSize, borderColor);
    }

    /**
     * Returns the backing direct-color grid by reference.
     *
     * @return source color grid reference
     */
    public Colors.Color[][] getGrid() {
        return grid;
    }

    /**
     * Returns the preferred content width in pixels.
     *
     * @return preferred content width in pixels
     */
    public int getPreferredWidth() {
        return preferredWidth;
    }

    /**
     * Updates the preferred content width in pixels.
     *
     * @param preferredWidth preferred content width in pixels; must be greater than zero
     * @throws IllegalArgumentException if preferredWidth is not positive
     */
    public void setPreferredWidth(int preferredWidth) {
        validatePreferredWidth(preferredWidth);
        this.preferredWidth = preferredWidth;
    }

    /**
     * Returns the preferred content height in pixels.
     *
     * @return preferred content height in pixels
     */
    public int getPreferredHeight() {
        return preferredHeight;
    }

    /**
     * Updates the preferred content height in pixels.
     *
     * @param preferredHeight preferred content height in pixels; must be greater than zero
     * @throws IllegalArgumentException if preferredHeight is not positive
     */
    public void setPreferredHeight(int preferredHeight) {
        validatePreferredHeight(preferredHeight);
        this.preferredHeight = preferredHeight;
    }

    /**
     * Returns the requested output PNG path.
     *
     * @return requested output path
     */
    public String getOutputPath() {
        return outputPath;
    }

    /**
     * Updates the exact target PNG output path.
     *
     * @param outputPath exact target file path; must not be null or blank
     * @throws IllegalArgumentException if outputPath is null or blank
     */
    public void setOutputPath(String outputPath) {
        validateOutputPath(outputPath);
        this.outputPath = outputPath;
    }

    /**
     * Returns the configured border thickness in pixels.
     *
     * @return border thickness in pixels
     */
    public int getBorderSize() {
        return borderSize;
    }

    /**
     * Updates the border thickness in pixels.
     *
     * @param borderSize border thickness in pixels; must be zero or greater
     * @throws IllegalArgumentException if borderSize is invalid for the current border color state
     */
    public void setBorderSize(int borderSize) {
        validateBorderState(borderSize, borderColor);
        this.borderSize = borderSize;
    }

    /**
     * Returns the configured border color, or null when no border is needed.
     *
     * @return configured border color or null
     */
    public Colors.Color getBorderColor() {
        return borderColor;
    }

    /**
     * Updates the border color.
     *
     * @param borderColor exact border color, or null when no border is needed
     * @throws IllegalArgumentException if borderColor is invalid for the current border size
     */
    public void setBorderColor(Colors.Color borderColor) {
        validateBorderState(borderSize, borderColor);
        this.borderColor = borderColor;
    }

    /**
     * Updates both preferred content dimensions together.
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
     * @param borderSize border thickness in pixels; must be zero or greater
     * @param borderColor exact border color when borderSize is greater than zero
     * @throws IllegalArgumentException if the requested border configuration is invalid
     */
    public void setBorder(int borderSize, Colors.Color borderColor) {
        validateBorderState(borderSize, borderColor);
        this.borderSize = borderSize;
        this.borderColor = borderColor;
    }

    private static void validatePreferredWidth(int preferredWidth) {
        if (preferredWidth <= 0) {
            throw new IllegalArgumentException("PixelColorArt: preferredWidth must be > 0.");
        }
    }

    private static void validatePreferredHeight(int preferredHeight) {
        if (preferredHeight <= 0) {
            throw new IllegalArgumentException("PixelColorArt: preferredHeight must be > 0.");
        }
    }

    private static void validateOutputPath(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("PixelColorArt: outputPath cannot be null or blank.");
        }
    }

    private static void validateBorderState(int borderSize, Colors.Color borderColor) {
        if (borderSize < 0) {
            throw new IllegalArgumentException("PixelColorArt: borderSize must be >= 0.");
        }
        if (borderSize > 0 && borderColor == null) {
            throw new IllegalArgumentException("PixelColorArt: borderColor is required when borderSize > 0.");
        }
    }
}
