package sora.tools.pixelgenerator;

/**
 * Reusable render request object for the pixel generator.
 *
 * <p>This object packages the source grid, preferred output dimensions,
 * and exact output file path into a single reusable unit. The renderer
 * reads from this object but must never mutate it.</p>
 *
 * <p>Use this class when you already have the final {@code String[][]} token
 * grid, for example because you loaded it from a text-grid file through
 * {@link PixelImageQuantizer#loadTextGrid(String)} or because another tool
 * already built the array for you. If you want drawing commands first, use
 * {@link PixelGridBuilder} instead.</p>
 *
 * <p>Public request fields, exposed through the constructor/getters:</p>
 * <ul>
 *     <li>{@code grid}: a rectangular {@code String[][]} token grid where each
 *     cell is a palette key such as {@code "b"}, {@code "r"}, {@code "lb"},
 *     or {@code "."}</li>
 *     <li>{@code preferredWidth}: preferred content width in pixels before the
 *     renderer rounds to a size evenly divisible by the column count</li>
 *     <li>{@code preferredHeight}: preferred content height in pixels before
 *     the renderer rounds to a size evenly divisible by the row count</li>
 *     <li>{@code outputPath}: exact final PNG file path</li>
 *     <li>{@code borderSize}: optional border thickness in pixels around the
 *     rendered content</li>
 *     <li>{@code borderColor}: palette token used for the border color when a
 *     positive border size is requested</li>
 * </ul>
 *
 * <p>The backing grid reference is stored as-is rather than deep-copied.
 * This keeps memory use low and allows the caller to intentionally reuse
 * the same object with externally edited grid contents between renders.
 * Because of that design, this object is not deeply immutable, and the
 * renderer still revalidates the grid each time a render is requested.</p>
 *
 * <p>The request may also define a fixed pixel border around the rendered
 * content. This is especially useful for outputs like QR codes where a quiet
 * zone is part of the final image but should not require manually enlarging
 * the source token grid.</p>
 */
public final class PixelArt {

    private final String[][] grid;
    private int preferredWidth;
    private int preferredHeight;
    private String outputPath;
    private int borderSize;
    private String borderColor;

    /**
     * Creates a new pixel-art render request.
     *
     * <p>Validation here is intentionally light. The constructor rejects
     * obviously invalid top-level inputs, while the renderer performs the
     * full structural validation on each render invocation. This allows
     * callers to reuse the same object while editing the grid contents
     * between renders without forcing unnecessary deep copies.</p>
     *
     * <p>Token expectations:</p>
     * <ul>
     *     <li>Tokens are case-insensitive after normalization</li>
     *     <li>Leading and trailing whitespace is ignored</li>
     *     <li>{@code "."}, null, and blank strings render as transparent</li>
     *     <li>Any other token must exist in the fixed palette or rendering will throw</li>
     * </ul>
     *
     * @param grid source token grid; must not be null and must contain at least one row
     * @param preferredWidth preferred output width in pixels; must be greater than zero
     * @param preferredHeight preferred output height in pixels; must be greater than zero
     * @param outputPath exact target file path for the PNG output
     * @throws IllegalArgumentException if any top-level argument is invalid
     */
    public PixelArt(String[][] grid, int preferredWidth, int preferredHeight, String outputPath) {
        this(grid, preferredWidth, preferredHeight, outputPath, 0, null);
    }

    /**
     * Creates a new pixel-art render request with a fixed pixel border.
     *
     * <p>The preferred width and height describe the content render area. If a
     * positive border size is supplied, that border is added outside the
     * rendered content in the final output image.</p>
     *
     * <p>This is especially useful for outputs like QR codes where the source
     * art should stay tightly sized to the actual module grid while the final
     * image still needs a quiet zone or frame around it.</p>
     *
     * @param grid source token grid; must not be null and must contain at least one row
     * @param preferredWidth preferred content width in pixels; must be greater than zero
     * @param preferredHeight preferred content height in pixels; must be greater than zero
     * @param outputPath exact target file path for the PNG output
     * @param borderSize border thickness in pixels on each side; must be zero or greater
     * @param borderColor token for the border color; required when borderSize is greater than zero
     * @throws IllegalArgumentException if any top-level argument is invalid
     */
    public PixelArt(String[][] grid,
                    int preferredWidth,
                    int preferredHeight,
                    String outputPath,
                    int borderSize,
                    String borderColor) {
        if (grid == null) {
            throw new IllegalArgumentException("PixelArt: grid cannot be null.");
        }
        if (grid.length == 0) {
            throw new IllegalArgumentException("PixelArt: grid must contain at least one row.");
        }

        this.grid = grid;
        setPreferredWidth(preferredWidth);
        setPreferredHeight(preferredHeight);
        setOutputPath(outputPath);
        setBorder(borderSize, borderColor);
    }

    /**
     * Returns the backing token grid by reference.
     *
     * <p>The renderer must treat this grid as read-only. Callers may reuse
     * the same {@code PixelArt} instance and edit the grid contents between
     * renders if they intentionally want different output from the same
     * request object.</p>
     *
     * @return source token grid reference
     */
    public String[][] getGrid() {
        return grid;
    }

    /**
     * Returns the preferred output width in pixels before divisible-size
     * resolution is applied by the renderer.
     *
     * @return preferred width in pixels
     */
    public int getPreferredWidth() {
        return preferredWidth;
    }

    /**
     * Updates the preferred content width in pixels.
     *
     * <p>This value describes the intended rendered content width before the
     * renderer adjusts it to the nearest size evenly divisible by the grid
     * column count.</p>
     *
     * @param preferredWidth preferred content width in pixels; must be greater than zero
     * @throws IllegalArgumentException if preferredWidth is not positive
     */
    public void setPreferredWidth(int preferredWidth) {
        validatePreferredWidth(preferredWidth);
        this.preferredWidth = preferredWidth;
    }

    /**
     * Returns the preferred output height in pixels before divisible-size
     * resolution is applied by the renderer.
     *
     * @return preferred height in pixels
     */
    public int getPreferredHeight() {
        return preferredHeight;
    }

    /**
     * Updates the preferred content height in pixels.
     *
     * <p>This value describes the intended rendered content height before the
     * renderer adjusts it to the nearest size evenly divisible by the grid row
     * count.</p>
     *
     * @param preferredHeight preferred content height in pixels; must be greater than zero
     * @throws IllegalArgumentException if preferredHeight is not positive
     */
    public void setPreferredHeight(int preferredHeight) {
        validatePreferredHeight(preferredHeight);
        this.preferredHeight = preferredHeight;
    }

    /**
     * Returns the exact target file path requested by the caller.
     *
     * <p>The renderer resolves this path to a normalized absolute path and
     * writes the PNG directly to that final location.</p>
     *
     * @return requested output file path
     */
    public String getOutputPath() {
        return outputPath;
    }

    /**
     * Updates the exact target PNG output path.
     *
     * <p>The path is stored exactly as supplied here. Final path resolution and
     * filesystem validation still happen at render time.</p>
     *
     * @param outputPath exact target file path; must not be null or blank
     * @throws IllegalArgumentException if outputPath is null or blank
     */
    public void setOutputPath(String outputPath) {
        validateOutputPath(outputPath);
        this.outputPath = outputPath;
    }

    /**
     * Returns the fixed border thickness in pixels applied to each edge of
     * the final output image.
     *
     * <p>A value of zero means no border is added.</p>
     *
     * @return border thickness in pixels
     */
    public int getBorderSize() {
        return borderSize;
    }

    /**
     * Updates the render-time border thickness in pixels.
     *
     * <p>If the new size is greater than zero, a non-blank border color token
     * must already be configured on this object.</p>
     *
     * @param borderSize border thickness in pixels; must be zero or greater
     * @throws IllegalArgumentException if borderSize is negative or if a
     *                                  positive size is requested without a
     *                                  configured border color token
     */
    public void setBorderSize(int borderSize) {
        validateBorderState(borderSize, borderColor);
        this.borderSize = borderSize;
    }

    /**
     * Returns the requested border color token.
     *
     * <p>This token is normalized and resolved by the renderer using the same
     * fixed palette as all grid cells. The value may be null when the border
     * size is zero.</p>
     *
     * @return raw border color token, or null when no border color is needed
     */
    public String getBorderColor() {
        return borderColor;
    }

    /**
     * Updates the render-time border color token.
     *
     * <p>The token is not resolved here; it is only validated for required
     * presence when a positive border size is configured. Actual palette
     * resolution still occurs at render time.</p>
     *
     * @param borderColor border token, or null when no border is needed
     * @throws IllegalArgumentException if a positive border size is already set
     *                                  and borderColor is null or blank
     */
    public void setBorderColor(String borderColor) {
        validateBorderState(borderSize, borderColor);
        this.borderColor = borderColor;
    }

    /**
     * Updates both border fields together.
     *
     * <p>This is the safest way to change border configuration because the
     * size/color pair is validated as one unit before either field is updated.</p>
     *
     * @param borderSize border thickness in pixels; must be zero or greater
     * @param borderColor border token when borderSize is greater than zero
     * @throws IllegalArgumentException if the requested border configuration is invalid
     */
    public void setBorder(int borderSize, String borderColor) {
        validateBorderState(borderSize, borderColor);
        this.borderSize = borderSize;
        this.borderColor = borderColor;
    }

    /**
     * Updates both preferred render dimensions together.
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
     * Validates preferred width.
     */
    private static void validatePreferredWidth(int preferredWidth) {
        if (preferredWidth <= 0) {
            throw new IllegalArgumentException("PixelArt: preferredWidth must be > 0.");
        }
    }

    /**
     * Validates preferred height.
     */
    private static void validatePreferredHeight(int preferredHeight) {
        if (preferredHeight <= 0) {
            throw new IllegalArgumentException("PixelArt: preferredHeight must be > 0.");
        }
    }

    /**
     * Validates output path presence.
     */
    private static void validateOutputPath(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("PixelArt: outputPath cannot be null or blank.");
        }
    }

    /**
     * Validates border size/color consistency.
     */
    private static void validateBorderState(int borderSize, String borderColor) {
        if (borderSize < 0) {
            throw new IllegalArgumentException("PixelArt: borderSize must be >= 0.");
        }
        if (borderSize > 0 && (borderColor == null || borderColor.isBlank())) {
            throw new IllegalArgumentException("PixelArt: borderColor is required when borderSize > 0.");
        }
    }
}
