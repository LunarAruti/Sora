package sora.tools.qrgenerator;

import java.util.Arrays;

/**
 * QR specification tables and pure lookup helpers.
 *
 * <p>This class centralizes the constants and deterministic calculations that
 * are defined by the QR standard: matrix sizing, data capacities, block
 * layouts, alignment-pattern positions, mode indicators, format bits, version
 * bits, and encoding metadata.</p>
 *
 * <p>The rest of the QR package treats this class as the canonical source of
 * truth for spec values so those tables do not get duplicated across the
 * encoder, codeword builder, and matrix writer.</p>
 */
public final class QrSpec {

    /**
     * Highest QR version represented by the embedded specification tables.
     */
    public static final int MAX_SUPPORTED_VERSION = 40;
    private static final String ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    /**
     * Total data-codeword capacities by version and EC level.
     *
     * <p>Columns are ordered L, M, Q, H.</p>
     */
    private static final int[][] DATA_CODEWORDS = {
            {19, 16, 13, 9},
            {34, 28, 22, 16},
            {55, 44, 34, 26},
            {80, 64, 48, 36},
            {108, 86, 62, 46},
            {136, 108, 76, 60},
            {156, 124, 88, 66},
            {194, 154, 110, 86},
            {232, 182, 132, 100},
            {274, 216, 154, 122},
            {324, 254, 180, 140},
            {370, 290, 206, 158},
            {428, 334, 244, 180},
            {461, 365, 261, 197},
            {523, 415, 295, 223},
            {589, 453, 325, 253},
            {647, 507, 367, 283},
            {721, 563, 397, 313},
            {795, 627, 445, 341},
            {861, 669, 485, 385},
            {932, 714, 512, 406},
            {1006, 782, 568, 442},
            {1094, 860, 614, 464},
            {1174, 914, 664, 514},
            {1276, 1000, 718, 538},
            {1370, 1062, 754, 596},
            {1468, 1128, 808, 628},
            {1531, 1193, 871, 661},
            {1631, 1267, 911, 701},
            {1735, 1373, 985, 745},
            {1843, 1455, 1033, 793},
            {1955, 1541, 1115, 845},
            {2071, 1631, 1171, 901},
            {2191, 1725, 1231, 961},
            {2306, 1812, 1286, 986},
            {2434, 1914, 1354, 1054},
            {2566, 1992, 1426, 1096},
            {2702, 2102, 1502, 1142},
            {2812, 2216, 1582, 1222},
            {2956, 2334, 1666, 1276}
    };

    /**
     * QR error-correction block layouts by version and EC level.
     *
     * <p>Columns are ordered L, M, Q, H.</p>
     */
    private static final BlockSpec[][] BLOCK_SPECS = {
            {
                    spec(7, 1, 19), spec(10, 1, 16), spec(13, 1, 13), spec(17, 1, 9)
            },
            {
                    spec(10, 1, 34), spec(16, 1, 28), spec(22, 1, 22), spec(28, 1, 16)
            },
            {
                    spec(15, 1, 55), spec(26, 1, 44), spec(18, 2, 17), spec(22, 2, 13)
            },
            {
                    spec(20, 1, 80), spec(18, 2, 32), spec(26, 2, 24), spec(16, 4, 9)
            },
            {
                    spec(26, 1, 108), spec(24, 2, 43), spec(18, 2, 15, 2, 16), spec(22, 2, 11, 2, 12)
            },
            {
                    spec(18, 2, 68), spec(16, 4, 27), spec(24, 4, 19), spec(28, 4, 15)
            },
            {
                    spec(20, 2, 78), spec(18, 4, 31), spec(18, 2, 14, 4, 15), spec(26, 4, 13, 1, 14)
            },
            {
                    spec(24, 2, 97), spec(22, 2, 38, 2, 39), spec(22, 4, 18, 2, 19), spec(26, 4, 14, 2, 15)
            },
            {
                    spec(30, 2, 116), spec(22, 3, 36, 2, 37), spec(20, 4, 16, 4, 17), spec(24, 4, 12, 4, 13)
            },
            {
                    spec(18, 2, 68, 2, 69), spec(26, 4, 43, 1, 44), spec(24, 6, 19, 2, 20), spec(28, 6, 15, 2, 16)
            },
            {
                    spec(20, 4, 81), spec(30, 1, 50, 4, 51), spec(28, 4, 22, 4, 23), spec(24, 3, 12, 8, 13)
            },
            {
                    spec(24, 2, 92, 2, 93), spec(22, 6, 36, 2, 37), spec(26, 4, 20, 6, 21), spec(28, 7, 14, 4, 15)
            },
            {
                    spec(26, 4, 107), spec(22, 8, 37, 1, 38), spec(24, 8, 20, 4, 21), spec(22, 12, 11, 4, 12)
            },
            {
                    spec(30, 3, 115, 1, 116), spec(24, 4, 40, 5, 41), spec(20, 11, 16, 5, 17), spec(24, 11, 12, 5, 13)
            },
            {
                    spec(22, 5, 87, 1, 88), spec(24, 5, 41, 5, 42), spec(30, 5, 24, 7, 25), spec(24, 11, 12, 7, 13)
            },
            {
                    spec(24, 5, 98, 1, 99), spec(28, 7, 45, 3, 46), spec(24, 15, 19, 2, 20), spec(30, 3, 15, 13, 16)
            },
            {
                    spec(28, 1, 107, 5, 108), spec(28, 10, 46, 1, 47), spec(28, 1, 22, 15, 23), spec(28, 2, 14, 17, 15)
            },
            {
                    spec(30, 5, 120, 1, 121), spec(26, 9, 43, 4, 44), spec(28, 17, 22, 1, 23), spec(28, 2, 14, 19, 15)
            },
            {
                    spec(28, 3, 113, 4, 114), spec(26, 3, 44, 11, 45), spec(26, 17, 21, 4, 22), spec(26, 9, 13, 16, 14)
            },
            {
                    spec(28, 3, 107, 5, 108), spec(26, 3, 41, 13, 42), spec(30, 15, 24, 5, 25), spec(28, 15, 15, 10, 16)
            },
            {
                    spec(28, 4, 116, 4, 117), spec(26, 17, 42), spec(28, 17, 22, 6, 23), spec(30, 19, 16, 6, 17)
            },
            {
                    spec(28, 2, 111, 7, 112), spec(28, 17, 46), spec(30, 7, 24, 16, 25), spec(24, 34, 13)
            },
            {
                    spec(30, 4, 121, 5, 122), spec(28, 4, 47, 14, 48), spec(30, 11, 24, 14, 25), spec(30, 16, 15, 14, 16)
            },
            {
                    spec(30, 6, 117, 4, 118), spec(28, 6, 45, 14, 46), spec(30, 11, 24, 16, 25), spec(30, 30, 16, 2, 17)
            },
            {
                    spec(26, 8, 106, 4, 107), spec(28, 8, 47, 13, 48), spec(30, 7, 24, 22, 25), spec(30, 22, 15, 13, 16)
            },
            {
                    spec(28, 10, 114, 2, 115), spec(28, 19, 46, 4, 47), spec(28, 28, 22, 6, 23), spec(30, 33, 16, 4, 17)
            },
            {
                    spec(30, 8, 122, 4, 123), spec(28, 22, 45, 3, 46), spec(30, 8, 23, 26, 24), spec(30, 12, 15, 28, 16)
            },
            {
                    spec(30, 3, 117, 10, 118), spec(28, 3, 45, 23, 46), spec(30, 4, 24, 31, 25), spec(30, 11, 15, 31, 16)
            },
            {
                    spec(30, 7, 116, 7, 117), spec(28, 21, 45, 7, 46), spec(30, 1, 23, 37, 24), spec(30, 19, 15, 26, 16)
            },
            {
                    spec(30, 5, 115, 10, 116), spec(28, 19, 47, 10, 48), spec(30, 15, 24, 25, 25), spec(30, 23, 15, 25, 16)
            },
            {
                    spec(30, 13, 115, 3, 116), spec(28, 2, 46, 29, 47), spec(30, 42, 24, 1, 25), spec(30, 23, 15, 28, 16)
            },
            {
                    spec(30, 17, 115), spec(28, 10, 46, 23, 47), spec(30, 10, 24, 35, 25), spec(30, 19, 15, 35, 16)
            },
            {
                    spec(30, 17, 115, 1, 116), spec(28, 14, 46, 21, 47), spec(30, 29, 24, 19, 25), spec(30, 11, 15, 46, 16)
            },
            {
                    spec(30, 13, 115, 6, 116), spec(28, 14, 46, 23, 47), spec(30, 44, 24, 7, 25), spec(30, 59, 16, 1, 17)
            },
            {
                    spec(30, 12, 121, 7, 122), spec(28, 12, 47, 26, 48), spec(30, 39, 24, 14, 25), spec(30, 22, 15, 41, 16)
            },
            {
                    spec(30, 6, 121, 14, 122), spec(28, 6, 47, 34, 48), spec(30, 46, 24, 10, 25), spec(30, 2, 15, 64, 16)
            },
            {
                    spec(30, 17, 122, 4, 123), spec(28, 29, 46, 14, 47), spec(30, 49, 24, 10, 25), spec(30, 24, 15, 46, 16)
            },
            {
                    spec(30, 4, 122, 18, 123), spec(28, 13, 46, 32, 47), spec(30, 48, 24, 14, 25), spec(30, 42, 15, 32, 16)
            },
            {
                    spec(30, 20, 117, 4, 118), spec(28, 40, 47, 7, 48), spec(30, 43, 24, 22, 25), spec(30, 10, 15, 67, 16)
            },
            {
                    spec(30, 19, 118, 6, 119), spec(28, 18, 47, 31, 48), spec(30, 34, 24, 34, 25), spec(30, 20, 15, 61, 16)
            }
    };

    /**
     * Alignment-pattern center coordinates by version.
     *
     * <p>Version 1 has no alignment patterns, so its entry is empty.</p>
     */
    private static final int[][] ALIGNMENT_CENTERS = {
            {},
            {6, 18},
            {6, 22},
            {6, 26},
            {6, 30},
            {6, 34},
            {6, 22, 38},
            {6, 24, 42},
            {6, 26, 46},
            {6, 28, 50},
            {6, 30, 54},
            {6, 32, 58},
            {6, 34, 62},
            {6, 26, 46, 66},
            {6, 26, 48, 70},
            {6, 26, 50, 74},
            {6, 30, 54, 78},
            {6, 30, 56, 82},
            {6, 30, 58, 86},
            {6, 34, 62, 90},
            {6, 28, 50, 72, 94},
            {6, 26, 50, 74, 98},
            {6, 30, 54, 78, 102},
            {6, 28, 54, 80, 106},
            {6, 32, 58, 84, 110},
            {6, 30, 58, 86, 114},
            {6, 34, 62, 90, 118},
            {6, 26, 50, 74, 98, 122},
            {6, 30, 54, 78, 102, 126},
            {6, 26, 52, 78, 104, 130},
            {6, 30, 56, 82, 108, 134},
            {6, 34, 60, 86, 112, 138},
            {6, 30, 58, 86, 114, 142},
            {6, 34, 62, 90, 118, 146},
            {6, 30, 54, 78, 102, 126, 150},
            {6, 24, 50, 76, 102, 128, 154},
            {6, 28, 54, 80, 106, 132, 158},
            {6, 32, 58, 84, 110, 136, 162},
            {6, 26, 54, 82, 110, 138, 166},
            {6, 30, 58, 86, 114, 142, 170}
    };

    /**
     * Remainder-bit counts by version.
     */
    private static final int[] REMAINDER_BITS = {
            0, 7, 7, 7, 7, 7,
            0, 0, 0, 0, 0, 0, 0,
            3, 3, 3, 3, 3, 3, 3,
            4, 4, 4, 4, 4, 4, 4,
            3, 3, 3, 3, 3, 3, 3,
            0, 0, 0, 0, 0, 0
    };

    private QrSpec() {}

    /**
     * Returns the QR matrix dimension for a version.
     *
     * @param version QR version in range 1..40
     * @return matrix width/height in modules
     */
    public static int getMatrixSize(int version) {
        validateVersion(version);
        return 21 + ((version - 1) * 4);
    }

    /**
     * Returns the number of character-count bits for the supplied version/mode
     * combination.
     */
    public static int getCharacterCountBits(int version, QrEncoder.QrMode mode) {
        validateVersion(version);

        if (version <= 9) {
            return switch (mode) {
                case NUMERIC -> 10;
                case ALPHANUMERIC -> 9;
                case BYTE -> 8;
            };
        }

        if (version <= 26) {
            return switch (mode) {
                case NUMERIC -> 12;
                case ALPHANUMERIC -> 11;
                case BYTE -> 16;
            };
        }

        return switch (mode) {
            case NUMERIC -> 14;
            case ALPHANUMERIC -> 13;
            case BYTE -> 16;
        };
    }

    /**
     * Returns the data-codeword capacity for one version/error-correction
     * combination.
     */
    public static int getDataCodewordCapacity(int version, QrEncoder.ErrorCorrectionLevel ecLevel) {
        validateVersion(version);
        return DATA_CODEWORDS[version - 1][ecIndex(ecLevel)];
    }

    /**
     * Returns the total number of raw data modules available in the QR matrix
     * after function patterns and reserved areas are accounted for.
     */
    public static int getRawDataModuleCount(int version) {
        validateVersion(version);

        int result = ((16 * version) + 128) * version + 64;
        if (version >= 2) {
            int numAlign = (version / 7) + 2;
            result -= ((25 * numAlign) - 10) * numAlign - 55;
            if (version >= 7) {
                result -= 36;
            }
        }
        return result;
    }

    /**
     * Returns the total number of raw codewords available in the symbol.
     */
    public static int getRawCodewordCount(int version) {
        return getRawDataModuleCount(version) / 8;
    }

    /**
     * Returns the QR block layout for one version/error-correction
     * combination.
     */
    public static BlockSpec getBlockSpec(int version, QrEncoder.ErrorCorrectionLevel ecLevel) {
        validateVersion(version);
        return BLOCK_SPECS[version - 1][ecIndex(ecLevel)];
    }

    /**
     * Returns the alignment-pattern centers for the supplied version.
     *
     * <p>A defensive copy is returned so callers cannot mutate the embedded
     * spec table.</p>
     */
    public static int[] getAlignmentCenters(int version) {
        validateVersion(version);
        return Arrays.copyOf(ALIGNMENT_CENTERS[version - 1], ALIGNMENT_CENTERS[version - 1].length);
    }

    /**
     * Returns the remainder-bit count for the supplied version.
     */
    public static int getRemainderBitCount(int version) {
        validateVersion(version);
        return REMAINDER_BITS[version - 1];
    }

    /**
     * Computes the 15-bit format information field for the supplied
     * error-correction level and mask id.
     */
    public static int getFormatBits(QrEncoder.ErrorCorrectionLevel ecLevel, int mask) {
        if (mask < 0 || mask > 7) {
            throw new IllegalArgumentException("Mask must be in range 0..7.");
        }

        int data = (getFormatEcBits(ecLevel) << 3) | mask;
        int bits = data << 10;
        for (int bit = 14; bit >= 10; bit--) {
            if (((bits >>> bit) & 1) != 0) {
                bits ^= 0x537 << (bit - 10);
            }
        }
        return ((data << 10) | bits) ^ 0x5412;
    }

    /**
     * Computes the 18-bit version information field for versions 7 and above.
     */
    public static int getVersionBits(int version) {
        validateVersion(version);
        if (version < 7) {
            throw new IllegalArgumentException("Version information is only defined for version 7 and above.");
        }

        int bits = version << 12;
        for (int bit = 17; bit >= 12; bit--) {
            if (((bits >>> bit) & 1) != 0) {
                bits ^= 0x1F25 << (bit - 12);
            }
        }
        return (version << 12) | bits;
    }

    /**
     * Returns the 4-bit mode indicator for the supplied QR mode.
     */
    public static int getModeIndicator(QrEncoder.QrMode mode) {
        return switch (mode) {
            case NUMERIC -> 0x1;
            case ALPHANUMERIC -> 0x2;
            case BYTE -> 0x4;
        };
    }

    /**
     * Returns the base-45 alphanumeric value for one character, or -1 if the
     * character is not supported by QR alphanumeric mode.
     */
    public static int getAlphanumericValue(char ch) {
        return ALPHANUMERIC_CHARSET.indexOf(ch);
    }

    /**
     * Returns the ECI assignment value for one supported text encoding.
     */
    public static int getEciAssignment(QrEncoder.TextEncoding encoding) {
        return switch (encoding) {
            case ISO_8859_1 -> 3;
            case UTF_8 -> 26;
        };
    }

    private static int getFormatEcBits(QrEncoder.ErrorCorrectionLevel ecLevel) {
        return switch (ecLevel) {
            case L -> 1;
            case M -> 0;
            case Q -> 3;
            case H -> 2;
        };
    }

    private static int ecIndex(QrEncoder.ErrorCorrectionLevel ecLevel) {
        return switch (ecLevel) {
            case L -> 0;
            case M -> 1;
            case Q -> 2;
            case H -> 3;
        };
    }

    private static BlockSpec spec(int ecCodewordsPerBlock,
                                  int group1BlockCount,
                                  int group1DataCodewordsPerBlock) {
        return new BlockSpec(
                ecCodewordsPerBlock,
                new BlockGroup(group1BlockCount, group1DataCodewordsPerBlock),
                null
        );
    }

    private static BlockSpec spec(int ecCodewordsPerBlock,
                                  int group1BlockCount,
                                  int group1DataCodewordsPerBlock,
                                  int group2BlockCount,
                                  int group2DataCodewordsPerBlock) {
        return new BlockSpec(
                ecCodewordsPerBlock,
                new BlockGroup(group1BlockCount, group1DataCodewordsPerBlock),
                new BlockGroup(group2BlockCount, group2DataCodewordsPerBlock)
        );
    }

    private static void validateVersion(int version) {
        if (version < 1 || version > MAX_SUPPORTED_VERSION) {
            throw new IllegalArgumentException("Unsupported QR version: " + version);
        }
    }

    /**
     * One block group from the QR specification, consisting of a repeated
     * number of blocks that all have the same data-codeword length.
     */
    public record BlockGroup(int blockCount, int dataCodewordsPerBlock) {}

    /**
     * Full QR block layout for one version/error-correction combination.
     *
     * @param ecCodewordsPerBlock error-correction codewords generated for each block
     * @param group1 first repeated block group, always present
     * @param group2 optional second repeated block group with a different data length
     */
    public record BlockSpec(int ecCodewordsPerBlock, BlockGroup group1, BlockGroup group2) {
        /**
         * Returns the total number of blocks across both groups.
         */
        public int totalBlockCount() {
            int total = group1.blockCount();
            if (group2 != null) {
                total += group2.blockCount();
            }
            return total;
        }

        /**
         * Returns the total number of data codewords contributed by both groups.
         */
        public int totalDataCodewords() {
            int total = group1.blockCount() * group1.dataCodewordsPerBlock();
            if (group2 != null) {
                total += group2.blockCount() * group2.dataCodewordsPerBlock();
            }
            return total;
        }

        /**
         * Expands the block layout into the per-block data-codeword lengths
         * used when splitting padded data into blocks.
         */
        public int[] dataBlockLengths() {
            int[] lengths = new int[totalBlockCount()];
            int index = 0;

            for (int i = 0; i < group1.blockCount(); i++) {
                lengths[index++] = group1.dataCodewordsPerBlock();
            }

            if (group2 != null) {
                for (int i = 0; i < group2.blockCount(); i++) {
                    lengths[index++] = group2.dataCodewordsPerBlock();
                }
            }

            return lengths;
        }

        @Override
        public String toString() {
            return "BlockSpec{" +
                    "ecCodewordsPerBlock=" + ecCodewordsPerBlock +
                    ", lengths=" + Arrays.toString(dataBlockLengths()) +
                    '}';
        }
    }
}
