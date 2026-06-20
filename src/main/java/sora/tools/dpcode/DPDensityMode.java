package sora.tools.dpcode;

import sora.tools.Colors;

/**
 * Supported DP Code density modes.
 *
 * <p>Each mode defines the number of legal quantized values available per RGB
 * channel. Since the levels are powers of two, each channel contributes a
 * fixed number of bits and each cell contributes three times that many bits.</p>
 */
public enum DPDensityMode {
    D2(2),
    D4(4),
    D8(8),
    D16(16),
    D32(32),
    D64(64),
    D128(128),
    D256(256);

    private final int levelsPerChannel;
    private final int bitsPerChannel;
    private final int bitsPerCell;

    DPDensityMode(int levelsPerChannel) {
        this.levelsPerChannel = levelsPerChannel;
        this.bitsPerChannel = Integer.numberOfTrailingZeros(levelsPerChannel);
        this.bitsPerCell = this.bitsPerChannel * 3;
    }

    /**
     * Returns the number of legal quantized values for each color channel.
     *
     * @return levels available per channel
     */
    public int getLevelsPerChannel() {
        return levelsPerChannel;
    }

    /**
     * Returns how many bits one channel contributes in this density mode.
     *
     * @return bits contributed by each channel
     */
    public int getBitsPerChannel() {
        return bitsPerChannel;
    }

    /**
     * Returns how many bits one full RGB cell contributes in this density mode.
     *
     * @return bits contributed by each cell
     */
    public int getBitsPerCell() {
        return bitsPerCell;
    }

    /**
     * Returns the legal channel value at the supplied quantized index.
     *
     * <p>Values are evenly spaced across the full 0..255 range, inclusive.</p>
     *
     * @param levelIndex zero-based quantized level index
     * @return legal channel value in the range 0..255
     * @throws IllegalArgumentException if the index is outside the legal range
     */
    public int getChannelValue(int levelIndex) {
        if (levelIndex < 0 || levelIndex >= levelsPerChannel) {
            throw new IllegalArgumentException("levelIndex out of range: " + levelIndex);
        }
        if (levelsPerChannel == 256) {
            return levelIndex;
        }
        return (int) Math.round((levelIndex * 255.0) / (levelsPerChannel - 1));
    }

    /**
     * Returns all legal channel values for this density mode.
     *
     * @return legal channel values in ascending order
     */
    public int[] getLegalChannelValues() {
        int[] values = new int[levelsPerChannel];
        for (int i = 0; i < levelsPerChannel; i++) {
            values[i] = getChannelValue(i);
        }
        return values;
    }

    /**
     * Converts one packed cell value into an exact RGB color for this mode.
     *
     * <p>The packed value is interpreted as consecutive channel groups in
     * {@code R, G, B} order. The highest bits belong to red, the middle bits
     * belong to green, and the lowest bits belong to blue.</p>
     *
     * @param cellBits packed cell value in the range {@code 0..2^bitsPerCell-1}
     * @return exact color for the packed cell value
     * @throws IllegalArgumentException if the packed value is outside the legal range
     */
    public Colors.Color colorFromCellBits(int cellBits) {
        int max = (1 << bitsPerCell) - 1;
        if (cellBits < 0 || cellBits > max) {
            throw new IllegalArgumentException("cellBits out of range: " + cellBits);
        }

        int channelMask = (1 << bitsPerChannel) - 1;
        int blueIndex = cellBits & channelMask;
        int greenIndex = (cellBits >>> bitsPerChannel) & channelMask;
        int redIndex = (cellBits >>> (bitsPerChannel * 2)) & channelMask;

        return new Colors.Color(
                getChannelValue(redIndex),
                getChannelValue(greenIndex),
                getChannelValue(blueIndex)
        );
    }

    /**
     * Converts one exact or sampled RGB color into a packed cell value for
     * this density mode.
     *
     * <p>Each channel is rounded to the nearest legal quantized value for the
     * current mode and then converted back into its quantized index. The final
     * packed cell value uses {@code R, G, B} channel group order.</p>
     *
     * @param color source color to decode
     * @return packed cell value for this density mode
     * @throws IllegalArgumentException if color is null
     */
    public int cellBitsFromColor(Colors.Color color) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null");
        }

        int redIndex = getChannelIndex(color.getR());
        int greenIndex = getChannelIndex(color.getG());
        int blueIndex = getChannelIndex(color.getB());

        return (redIndex << (bitsPerChannel * 2))
                | (greenIndex << bitsPerChannel)
                | blueIndex;
    }

    /**
     * Rounds a sampled channel value to the nearest legal quantized value for
     * this density mode.
     *
     * @param sampledValue sampled channel value
     * @return nearest legal quantized channel value
     */
    public int roundChannelValue(int sampledValue) {
        if (sampledValue <= 0) {
            return 0;
        }
        if (sampledValue >= 255) {
            return 255;
        }
        if (levelsPerChannel == 256) {
            return sampledValue;
        }

        int bestValue = 0;
        int bestDelta = Integer.MAX_VALUE;
        for (int value : getLegalChannelValues()) {
            int delta = Math.abs(sampledValue - value);
            if (delta < bestDelta) {
                bestDelta = delta;
                bestValue = value;
            }
        }
        return bestValue;
    }

    private int getChannelIndex(int sampledValue) {
        int roundedValue = roundChannelValue(sampledValue);
        if (levelsPerChannel == 256) {
            return roundedValue;
        }

        int[] legalValues = getLegalChannelValues();
        for (int i = 0; i < legalValues.length; i++) {
            if (legalValues[i] == roundedValue) {
                return i;
            }
        }
        throw new IllegalStateException("Rounded channel value does not map to a legal index: " + roundedValue);
    }
}
