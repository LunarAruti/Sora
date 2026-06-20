package sora.tools.pixelgenerator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Image-to-grid and text-grid utility for the token-based pixel generator.
 *
 * <p>Use this class when you want to do one of three things:</p>
 * <ul>
 *     <li>quantize a normal image into the shared token palette</li>
 *     <li>write an existing {@code String[][]} token grid to a reusable text file</li>
 *     <li>load one of those text-grid files back into memory for rendering</li>
 * </ul>
 *
 * <p>The text-grid format is intentionally simple: the file contains a Java-style
 * {@code String[][] picture = { ... };} initializer where each row is one grid row
 * and each quoted string is one token cell. That keeps the files readable,
 * copyable, and easy to turn back into {@link PixelArt} later.</p>
 */
public final class PixelImageQuantizer {

    /**
     * Pattern used to read quoted token values from one text-grid row.
     */
    private static final Pattern QUOTED_TOKEN_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    /**
     * Canonical transparent output token.
     */
    private static final String TRANSPARENT_OUTPUT_TOKEN = PixelTokenSupport.TRANSPARENT_TOKEN;

    /**
     * Canonical non-transparent palette entries used for quantization.
     *
     * <p>Each distinct ARGB color is represented once using the first token
     * registered for that color in the shared pixel-generator palette. This
     * keeps quantized output deterministic while still using the full palette
     * for nearest-color matching.</p>
     */
    private static final PaletteEntry[] QUANTIZABLE_PALETTE = buildQuantizablePalette();

    private PixelImageQuantizer() {}

    /**
     * Quantizes an image from disk into a token grid compatible with the pixel
     * generator.
     *
     * <p>Use this when you want a {@code String[][]} directly in memory without
     * writing any text file. The source image is treated as a whole, divided into
     * the requested destination grid size, and each output cell receives the
     * nearest token in the shared palette.</p>
     *
     * @param inputPath filesystem path to the source image
     * @param outputWidth target token-grid width in cells; must be greater than zero
     * @param outputHeight target token-grid height in cells; must be greater than zero
     * @return quantized token grid using the shared pixel-generator palette
     * @throws IllegalArgumentException if the path or dimensions are invalid, the image
     *                                  cannot be read, or the file is not a supported image
     */
    public static String[][] quantize(String inputPath, int outputWidth, int outputHeight) {
        Path path = resolveReadablePath(inputPath, "inputPath");
        validateOutputSize(outputWidth, outputHeight);

        BufferedImage image = readImage(path);
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();

        RegionAccumulator[][] accumulators = createAccumulators(outputWidth, outputHeight);
        accumulateSourcePixels(image, sourceWidth, sourceHeight, outputWidth, outputHeight, accumulators);
        return buildOutputGrid(image, sourceWidth, sourceHeight, outputWidth, outputHeight, accumulators);
    }

    /**
     * Quantizes an image and immediately writes the resulting token grid to a
     * plain text file.
     *
     * <p>Use this when you want a reusable source-art file instead of a PNG or
     * generated Java class. The destination path ignores any supplied extension
     * and always resolves to {@code .txt}.</p>
     *
     * @param inputPath filesystem path to the source image
     * @param outputWidth target token-grid width in cells; must be greater than zero
     * @param outputHeight target token-grid height in cells; must be greater than zero
     * @param outputPath filesystem path whose basename should be used for the generated text file
     * @return normalized absolute path of the written text file
     * @throws IllegalArgumentException if any path or dimension is invalid or the write fails
     */
    public static Path quantizeToTextGrid(String inputPath,
                                          int outputWidth,
                                          int outputHeight,
                                          String outputPath) {
        return writeTextGrid(quantize(inputPath, outputWidth, outputHeight), outputPath);
    }

    /**
     * Writes an already-existing token grid to the package text-grid format.
     *
     * <p>Use this when you already have a {@code String[][]}, whether it came
     * from manual code, quantization, or another loader, and you want to save
     * it as a reusable text asset. The destination path ignores any supplied
     * extension and always resolves to {@code .txt}.</p>
     *
     * @param grid rectangular token grid to save
     * @param outputPath filesystem path whose basename should be used for the generated text file
     * @return normalized absolute path of the written text file
     * @throws IllegalArgumentException if the grid shape is invalid or the write fails
     */
    public static Path writeTextGrid(String[][] grid, String outputPath) {
        String[][] normalizedGrid = normalizeAndValidateGrid(grid);
        Path textFilePath = resolveGeneratedTextOutputPath(outputPath);
        writeTextArtifact(textFilePath, buildTextGridSource(normalizedGrid));
        return textFilePath;
    }

    /**
     * Loads a previously written text-grid file back into a token grid.
     *
     * <p>Use this when you want to render or edit a saved grid later. The file
     * is expected to contain quoted token rows in the same
     * {@code String[][] picture = { ... };} format produced by
     * {@link #quantizeToTextGrid(String, int, int, String)} and
     * {@link #writeTextGrid(String[][], String)}.</p>
     *
     * @param inputPath filesystem path to the text-grid file
     * @return loaded rectangular token grid
     * @throws IllegalArgumentException if the file path is invalid, the file
     *                                  cannot be read, or the contents are malformed
     */
    public static String[][] loadTextGrid(String inputPath) {
        Path path = resolveReadablePath(inputPath, "inputPath");
        List<String> lines = readTextLines(path);
        return parseTextGrid(lines, path);
    }

    /**
     * Resolves and validates one readable filesystem path.
     */
    private static Path resolveReadablePath(String rawPathText, String argumentName) {
        if (rawPathText == null || rawPathText.isBlank()) {
            throw new IllegalArgumentException("PixelImageQuantizer: " + argumentName + " cannot be null or blank.");
        }

        final Path path;
        try {
            path = Path.of(rawPathText).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: invalid path for " + argumentName + ": '" + rawPathText + "'.", e
            );
        }

        if (!Files.exists(path)) {
            throw new IllegalArgumentException("PixelImageQuantizer: path does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("PixelImageQuantizer: path is not a file: " + path);
        }

        return path;
    }

    /**
     * Resolves, normalizes, and converts the destination path into the final
     * generated text-grid file path.
     */
    private static Path resolveGeneratedTextOutputPath(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("PixelImageQuantizer: outputPath cannot be null or blank.");
        }

        final Path rawPath;
        try {
            rawPath = Path.of(outputPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: invalid output path '" + outputPath + "'.", e
            );
        }

        Path parent = rawPath.getParent();
        if (parent == null) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: outputPath must include an existing parent directory."
            );
        }
        if (!Files.exists(parent)) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: outputPath parent directory does not exist: " + parent
            );
        }
        if (!Files.isDirectory(parent)) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: outputPath parent is not a directory: " + parent
            );
        }
        if (Files.exists(rawPath) && Files.isDirectory(rawPath)) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: outputPath points to a directory, not a file: " + rawPath
            );
        }

        String fileName = rawPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = (dot > 0) ? fileName.substring(0, dot) : fileName;
        if (stem.isBlank()) {
            throw new IllegalArgumentException("PixelImageQuantizer: outputPath must include a usable file basename.");
        }

        return parent.resolve(stem + ".txt").toAbsolutePath().normalize();
    }

    /**
     * Validates the requested quantized output grid dimensions.
     */
    private static void validateOutputSize(int outputWidth, int outputHeight) {
        if (outputWidth <= 0) {
            throw new IllegalArgumentException("PixelImageQuantizer: outputWidth must be > 0.");
        }
        if (outputHeight <= 0) {
            throw new IllegalArgumentException("PixelImageQuantizer: outputHeight must be > 0.");
        }
    }

    /**
     * Reads the source image using standard image I/O.
     */
    private static BufferedImage readImage(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalArgumentException(
                        "PixelImageQuantizer: file is not a supported readable image: " + path
                );
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: failed to read image from " + path + ".", e
            );
        }
    }

    /**
     * Reads the full text-grid file into memory.
     */
    private static List<String> readTextLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: failed to read text grid from " + path + ".", e
            );
        }
    }

    /**
     * Normalizes and validates one token grid before it is written to disk.
     */
    private static String[][] normalizeAndValidateGrid(String[][] grid) {
        if (grid == null) {
            throw new IllegalArgumentException("PixelImageQuantizer: grid cannot be null.");
        }
        if (grid.length == 0) {
            throw new IllegalArgumentException("PixelImageQuantizer: grid must contain at least one row.");
        }

        int expectedColumns = -1;
        String[][] normalized = new String[grid.length][];

        for (int y = 0; y < grid.length; y++) {
            String[] row = grid[y];
            if (row == null) {
                throw new IllegalArgumentException("PixelImageQuantizer: grid row " + y + " is null.");
            }
            if (row.length == 0) {
                throw new IllegalArgumentException("PixelImageQuantizer: grid row " + y + " has zero columns.");
            }
            if (expectedColumns == -1) {
                expectedColumns = row.length;
            } else if (row.length != expectedColumns) {
                throw new IllegalArgumentException(
                        "PixelImageQuantizer: grid row " + y + " length " + row.length +
                                " does not match expected column count " + expectedColumns + "."
                );
            }

            normalized[y] = new String[row.length];
            for (int x = 0; x < row.length; x++) {
                normalized[y][x] = PixelTokenSupport.normalize(row[x]);
            }
        }

        return normalized;
    }

    /**
     * Parses one saved text-grid file back into a rectangular token grid.
     */
    private static String[][] parseTextGrid(List<String> lines, Path path) {
        List<String[]> rows = new ArrayList<>();
        int expectedColumns = -1;

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }

            Matcher matcher = QUOTED_TOKEN_PATTERN.matcher(line);
            List<String> rowTokens = new ArrayList<>();
            while (matcher.find()) {
                rowTokens.add(PixelTokenSupport.normalize(unescapeQuotedToken(matcher.group(1))));
            }

            if (rowTokens.isEmpty()) {
                continue;
            }

            if (expectedColumns == -1) {
                expectedColumns = rowTokens.size();
            } else if (rowTokens.size() != expectedColumns) {
                throw new IllegalArgumentException(
                        "PixelImageQuantizer: malformed text grid at " + path +
                                ". Row has " + rowTokens.size() +
                                " columns, expected " + expectedColumns + "."
                );
            }

            rows.add(rowTokens.toArray(new String[0]));
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: text grid file contains no token rows: " + path
            );
        }

        return rows.toArray(new String[0][]);
    }

    /**
     * Unescapes the limited Java-style string content emitted by this class.
     */
    private static String unescapeQuotedToken(String encoded) {
        StringBuilder out = new StringBuilder(encoded.length());

        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c != '\\' || i + 1 >= encoded.length()) {
                out.append(c);
                continue;
            }

            char escaped = encoded.charAt(++i);
            if (escaped == '\\' || escaped == '"') {
                out.append(escaped);
            } else if (escaped == 'n') {
                out.append('\n');
            } else if (escaped == 'r') {
                out.append('\r');
            } else if (escaped == 't') {
                out.append('\t');
            } else {
                out.append(escaped);
            }
        }

        return out.toString();
    }

    /**
     * Creates one accumulator per destination cell.
     */
    private static RegionAccumulator[][] createAccumulators(int outputWidth, int outputHeight) {
        RegionAccumulator[][] accumulators = new RegionAccumulator[outputHeight][outputWidth];
        for (int y = 0; y < outputHeight; y++) {
            for (int x = 0; x < outputWidth; x++) {
                accumulators[y][x] = new RegionAccumulator();
            }
        }
        return accumulators;
    }

    /**
     * Projects every source pixel into its destination region and accumulates
     * visible RGB values for averaging.
     *
     * <p>Transparent source pixels are skipped entirely so mixed regions use
     * only visible colors when computing their average.</p>
     */
    private static void accumulateSourcePixels(BufferedImage image,
                                               int sourceWidth,
                                               int sourceHeight,
                                               int outputWidth,
                                               int outputHeight,
                                               RegionAccumulator[][] accumulators) {
        for (int sourceY = 0; sourceY < sourceHeight; sourceY++) {
            int bucketY = projectToBucket(sourceY, sourceHeight, outputHeight);
            for (int sourceX = 0; sourceX < sourceWidth; sourceX++) {
                int argb = image.getRGB(sourceX, sourceY);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }

                int bucketX = projectToBucket(sourceX, sourceWidth, outputWidth);
                RegionAccumulator accumulator = accumulators[bucketY][bucketX];
                accumulator.sumRed += (argb >>> 16) & 0xFF;
                accumulator.sumGreen += (argb >>> 8) & 0xFF;
                accumulator.sumBlue += argb & 0xFF;
                accumulator.visiblePixelCount++;
            }
        }
    }

    /**
     * Builds the final token grid from the accumulated regions.
     *
     * <p>If a region has no visible source pixels, the method falls back to a
     * representative source sample for that region. This prevents sparse empty
     * cells when callers request more grid cells than the source image has
     * pixels in one dimension. If the representative sample is also fully
     * transparent, the cell remains transparent.</p>
     */
    private static String[][] buildOutputGrid(BufferedImage image,
                                              int sourceWidth,
                                              int sourceHeight,
                                              int outputWidth,
                                              int outputHeight,
                                              RegionAccumulator[][] accumulators) {
        String[][] grid = new String[outputHeight][outputWidth];

        for (int y = 0; y < outputHeight; y++) {
            for (int x = 0; x < outputWidth; x++) {
                RegionAccumulator accumulator = accumulators[y][x];
                if (accumulator.visiblePixelCount > 0) {
                    int averageRed = (int) (accumulator.sumRed / accumulator.visiblePixelCount);
                    int averageGreen = (int) (accumulator.sumGreen / accumulator.visiblePixelCount);
                    int averageBlue = (int) (accumulator.sumBlue / accumulator.visiblePixelCount);
                    grid[y][x] = findNearestToken(averageRed, averageGreen, averageBlue);
                    continue;
                }

                grid[y][x] = sampleRepresentativeToken(image, sourceWidth, sourceHeight, outputWidth, outputHeight, x, y);
            }
        }

        return grid;
    }

    /**
     * Maps a source coordinate into a destination bucket using the full source
     * dimension so the whole image contributes to the output grid.
     */
    private static int projectToBucket(int sourceIndex, int sourceSize, int bucketCount) {
        int bucket = (int) (((long) sourceIndex * bucketCount) / sourceSize);
        return (bucket >= bucketCount) ? bucketCount - 1 : bucket;
    }

    /**
     * Samples the center of a destination region when the direct region
     * accumulator ended up empty.
     */
    private static String sampleRepresentativeToken(BufferedImage image,
                                                    int sourceWidth,
                                                    int sourceHeight,
                                                    int outputWidth,
                                                    int outputHeight,
                                                    int cellX,
                                                    int cellY) {
        int sampleX = clamp(
                (int) Math.floor((((cellX + 0.5D) * sourceWidth) / outputWidth)),
                0,
                sourceWidth - 1
        );
        int sampleY = clamp(
                (int) Math.floor((((cellY + 0.5D) * sourceHeight) / outputHeight)),
                0,
                sourceHeight - 1
        );

        int argb = image.getRGB(sampleX, sampleY);
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) {
            return TRANSPARENT_OUTPUT_TOKEN;
        }
        return findNearestToken((argb >>> 16) & 0xFF, (argb >>> 8) & 0xFF, argb & 0xFF);
    }

    /**
     * Finds the nearest palette token using squared RGB distance.
     */
    private static String findNearestToken(int red, int green, int blue) {
        PaletteEntry best = null;
        long bestDistance = Long.MAX_VALUE;

        for (PaletteEntry entry : QUANTIZABLE_PALETTE) {
            int entryRed = (entry.argb >>> 16) & 0xFF;
            int entryGreen = (entry.argb >>> 8) & 0xFF;
            int entryBlue = entry.argb & 0xFF;

            long deltaRed = red - entryRed;
            long deltaGreen = green - entryGreen;
            long deltaBlue = blue - entryBlue;
            long distance = (deltaRed * deltaRed) + (deltaGreen * deltaGreen) + (deltaBlue * deltaBlue);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry;
            }
        }

        if (best == null) {
            throw new IllegalStateException("PixelImageQuantizer: quantizable palette is empty.");
        }
        return best.token;
    }

    /**
     * Builds the plain text {@code String[][]} initializer written by the
     * public text-grid export methods.
     */
    private static String buildTextGridSource(String[][] grid) {
        String lineSeparator = System.lineSeparator();
        StringBuilder out = new StringBuilder();
        out.append("String[][] picture = {").append(lineSeparator);

        for (int y = 0; y < grid.length; y++) {
            out.append("    {");
            for (int x = 0; x < grid[y].length; x++) {
                if (x > 0) {
                    out.append(", ");
                }
                out.append(quote(grid[y][x]));
            }
            out.append("}");
            if (y + 1 < grid.length) {
                out.append(",");
            }
            out.append(lineSeparator);
        }

        out.append("};").append(lineSeparator);
        return out.toString();
    }

    /**
     * Writes one generated text artifact to the final output path.
     */
    private static void writeTextArtifact(Path outputPath, String content) {
        try {
            Files.writeString(
                    outputPath,
                    content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: failed to write text grid to " + outputPath + ".", e
            );
        }
    }

    /**
     * Escapes one token as a Java-style quoted string literal.
     */
    private static String quote(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    /**
     * Builds the distinct non-transparent palette entries used during nearest-
     * color matching.
     */
    private static PaletteEntry[] buildQuantizablePalette() {
        Map<Integer, String> canonicalByColor = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : PixelTokenSupport.DEFAULT_PALETTE.entrySet()) {
            int argb = entry.getValue();
            if (((argb >>> 24) & 0xFF) == 0) {
                continue;
            }
            canonicalByColor.putIfAbsent(argb, entry.getKey());
        }

        PaletteEntry[] entries = new PaletteEntry[canonicalByColor.size()];
        int index = 0;
        for (Map.Entry<Integer, String> entry : canonicalByColor.entrySet()) {
            entries[index++] = new PaletteEntry(entry.getValue(), entry.getKey());
        }
        return entries;
    }

    /**
     * Small int clamp used for representative-region sampling.
     */
    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * Mutable RGB accumulator for one destination region.
     */
    private static final class RegionAccumulator {
        private long sumRed;
        private long sumGreen;
        private long sumBlue;
        private long visiblePixelCount;
    }

    /**
     * Immutable palette entry used for nearest-color matching.
     */
    private record PaletteEntry(String token, int argb) {}
}
