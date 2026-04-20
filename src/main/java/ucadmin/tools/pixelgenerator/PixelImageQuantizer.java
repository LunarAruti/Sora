package ucadmin.tools.pixelgenerator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Image-to-token-grid quantizer for the pixel generator.
 *
 * <p>This utility reads a source image from disk, compresses the entire image
 * into a lower-detail token grid, and returns a {@code String[][]} that can be
 * passed directly into {@link PixelArt}. Each output cell represents one region
 * of the full source image rather than a crop of the top-left pixels.</p>
 *
 * <p>The public API is intentionally minimal: one method that accepts an image
 * path plus a target grid width and height. All palette matching uses the same
 * fixed palette as the renderer.</p>
 */
public final class PixelImageQuantizer {

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
     * Quantizes an image from disk into a token grid compatible with the
     * pixel generator.
     *
     * <p>The source image is treated as a whole. It is subdivided into the
     * requested output grid dimensions, and each destination cell receives the
     * average color of its corresponding source region. Transparent source
     * pixels are ignored during averaging. If a region has no visible pixels
     * after transparency is ignored, the output token for that cell is
     * {@code "."}.</p>
     *
     * <p>The returned grid is already filled with palette tokens, so it can be
     * handed directly into {@link PixelArt} or any other pixel-generator flow.</p>
     *
     * @param inputPath filesystem path to the source image
     * @param outputWidth target token-grid width in cells; must be greater than zero
     * @param outputHeight target token-grid height in cells; must be greater than zero
     * @return quantized token grid using the shared pixel-generator palette
     * @throws IllegalArgumentException if the path or dimensions are invalid, the image
     *                                  cannot be read, or the file is not a supported image
     */
    public static String[][] quantize(String inputPath, int outputWidth, int outputHeight) {
        Path path = resolveInputPath(inputPath);
        validateOutputSize(outputWidth, outputHeight);

        BufferedImage image = readImage(path);
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();

        RegionAccumulator[][] accumulators = createAccumulators(outputWidth, outputHeight);
        accumulateSourcePixels(image, sourceWidth, sourceHeight, outputWidth, outputHeight, accumulators);
        return buildOutputGrid(image, sourceWidth, sourceHeight, outputWidth, outputHeight, accumulators);
    }

    /**
     * Resolves and validates the source image path.
     */
    private static Path resolveInputPath(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("PixelImageQuantizer: inputPath cannot be null or blank.");
        }

        final Path path;
        try {
            path = Path.of(inputPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(
                    "PixelImageQuantizer: invalid image path '" + inputPath + "'.", e
            );
        }

        if (!Files.exists(path)) {
            throw new IllegalArgumentException("PixelImageQuantizer: image path does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("PixelImageQuantizer: image path is not a file: " + path);
        }

        return path;
    }

    /**
     * Validates the requested output grid dimensions.
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
     * Finds the nearest palette token using plain squared RGB distance.
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
