package sora.tools.qrgenerator;

/**
 * QR symbol matrix builder and mask scorer.
 *
 * <p>This class takes the final payload bits produced by
 * {@link QrCodewords}, lays them into a QR matrix, applies each of the eight
 * mask patterns, scores every masked candidate using the standard QR penalty
 * rules, and returns the best-performing matrix.</p>
 *
 * <p>The public surface here is intentionally narrow because most callers
 * should use {@link QrEncoder}. This class mainly exists as the matrix layer
 * of the QR pipeline and as a useful debug seam for inspecting final module
 * placement.</p>
 */
public final class QrMatrix {
    private static final int MASK_COUNT = 8;
    private static final int MAX_RENDERABLE_VERSION = 40;
    private static final int PENALTY_N1 = 3;
    private static final int PENALTY_N2 = 3;
    private static final int PENALTY_N3 = 40;
    private static final int PENALTY_N4 = 10;

    private QrMatrix() {}

    /**
     * Returns the highest QR version this matrix builder currently supports.
     *
     * <p>This is kept separate from the specification maximum so the encoder
     * can guard against a future situation where the spec table exists but the
     * renderer does not yet fully support some version-related matrix logic.</p>
     */
    static int maxRenderableVersion() {
        return MAX_RENDERABLE_VERSION;
    }

    /**
     * Builds the final masked QR module matrix.
     *
     * @param result payload/codeword result from {@link QrCodewords}
     * @param ecLevel error-correction level used to write format information
     * @return final masked QR module matrix where true means dark
     */
    public static boolean[][] buildMatrix(QrCodewords.Result result,
                                          QrEncoder.ErrorCorrectionLevel ecLevel) {
        return buildMatrixDetails(result, ecLevel).modules();
    }

    /**
     * Builds the final matrix and also returns mask-selection diagnostics.
     *
     * <p>This is primarily used by the debug QR flow so callers can inspect
     * the chosen mask and the penalty scores of all eight candidates.</p>
     */
    static MatrixBuild buildMatrixDetails(QrCodewords.Result result,
                                          QrEncoder.ErrorCorrectionLevel ecLevel) {
        MatrixState base = createEmpty(result.version());
        drawFunctionPatterns(base, result.version());
        validateMatrixCapacity(base, result.finalBits().length);
        placeDataBits(base, result.finalBits());

        MatrixState best = null;
        int bestPenalty = Integer.MAX_VALUE;
        int bestMask = -1;
        int[] penalties = new int[MASK_COUNT];

        // Each mask candidate starts from the same unmasked data placement so
        // scoring stays comparable and deterministic.
        for (int mask = 0; mask < MASK_COUNT; mask++) {
            MatrixState candidate = copyOf(base);
            applyMask(candidate, mask);
            writeFormatInfo(candidate, ecLevel, mask);
            writeVersionInfo(candidate, result.version());

            int penalty = scoreMask(candidate);
            penalties[mask] = penalty;
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
                best = candidate;
            }
        }

        if (best == null) {
            throw new IllegalStateException("No QR mask candidate was produced.");
        }

        return new MatrixBuild(copyModules(best.modules), bestMask, penalties, base.size, result.finalBits().length);
    }

    private static MatrixState createEmpty(int version) {
        int size = QrSpec.getMatrixSize(version);
        return new MatrixState(size);
    }

    private static void drawFunctionPatterns(MatrixState state, int version) {
        drawFinderWithSeparator(state, 0, 0);
        drawFinderWithSeparator(state, 0, state.size - 7);
        drawFinderWithSeparator(state, state.size - 7, 0);
        drawAlignmentPatterns(state, version);
        drawTimingPatterns(state);
        drawDarkModule(state, version);
        reserveFormatAreas(state);
        reserveVersionAreas(state, version);
    }

    /**
     * Draws a finder pattern with its surrounding white separator region.
     */
    private static void drawFinderWithSeparator(MatrixState state, int topRow, int leftCol) {
        for (int rowOffset = -1; rowOffset <= 7; rowOffset++) {
            for (int colOffset = -1; colOffset <= 7; colOffset++) {
                int row = topRow + rowOffset;
                int col = leftCol + colOffset;
                if (!state.inBounds(row, col)) {
                    continue;
                }

                boolean inCore = rowOffset >= 0 && rowOffset <= 6 && colOffset >= 0 && colOffset <= 6;
                boolean value = inCore && (
                        rowOffset == 0 || rowOffset == 6 ||
                                colOffset == 0 || colOffset == 6 ||
                                (rowOffset >= 2 && rowOffset <= 4 && colOffset >= 2 && colOffset <= 4)
                );
                state.setFunctionModule(row, col, value);
            }
        }
    }

    /**
     * Draws the horizontal and vertical timing patterns that help scanners
     * determine module pitch.
     */
    private static void drawTimingPatterns(MatrixState state) {
        for (int i = 0; i < state.size; i++) {
            if (!state.reserved[6][i]) {
                state.setFunctionModule(6, i, i % 2 == 0);
            }
            if (!state.reserved[i][6]) {
                state.setFunctionModule(i, 6, i % 2 == 0);
            }
        }
    }

    /**
     * Draws all alignment patterns required for the selected QR version.
     */
    private static void drawAlignmentPatterns(MatrixState state, int version) {
        int[] centers = QrSpec.getAlignmentCenters(version);
        for (int rowCenter : centers) {
            for (int colCenter : centers) {
                if (state.reserved[rowCenter][colCenter]) {
                    continue;
                }
                drawAlignmentPattern(state, rowCenter, colCenter);
            }
        }
    }

    /**
     * Draws one 5x5 alignment pattern centered at the supplied coordinates.
     */
    private static void drawAlignmentPattern(MatrixState state, int rowCenter, int colCenter) {
        for (int rowOffset = -2; rowOffset <= 2; rowOffset++) {
            for (int colOffset = -2; colOffset <= 2; colOffset++) {
                int row = rowCenter + rowOffset;
                int col = colCenter + colOffset;
                boolean value = Math.max(Math.abs(rowOffset), Math.abs(colOffset)) != 1;
                state.setFunctionModule(row, col, value);
            }
        }
    }

    /**
     * Draws the fixed dark module required by the QR specification.
     */
    private static void drawDarkModule(MatrixState state, int version) {
        int row = (4 * version) + 9;
        state.setFunctionModule(row, 8, true);
    }

    /**
     * Reserves matrix cells that will later hold QR format information bits.
     */
    private static void reserveFormatAreas(MatrixState state) {
        for (int col = 0; col <= 8; col++) {
            if (col != 6) {
                state.reserved[8][col] = true;
            }
        }
        for (int row = 0; row <= 8; row++) {
            if (row != 6) {
                state.reserved[row][8] = true;
            }
        }

        for (int col = state.size - 8; col < state.size; col++) {
            state.reserved[8][col] = true;
        }
        for (int row = state.size - 8; row < state.size; row++) {
            state.reserved[row][8] = true;
        }
    }

    /**
     * Reserves the two version-information areas used on version 7 and above.
     */
    private static void reserveVersionAreas(MatrixState state, int version) {
        if (version < 7) {
            return;
        }

        for (int row = 0; row < 6; row++) {
            for (int col = state.size - 11; col <= state.size - 9; col++) {
                state.reserved[row][col] = true;
            }
        }

        for (int row = state.size - 11; row <= state.size - 9; row++) {
            for (int col = 0; col < 6; col++) {
                state.reserved[row][col] = true;
            }
        }
    }

    /**
     * Places the final QR data bits into all non-function matrix cells using
     * the standard two-column zig-zag traversal.
     */
    private static void placeDataBits(MatrixState state, boolean[] bits) {
        int bitIndex = 0;
        boolean upwards = true;

        for (int rightCol = state.size - 1; rightCol >= 1; rightCol -= 2) {
            if (rightCol == 6) {
                rightCol--;
            }

            int startRow = upwards ? state.size - 1 : 0;
            int endRow = upwards ? -1 : state.size;
            int step = upwards ? -1 : 1;

            for (int row = startRow; row != endRow; row += step) {
                for (int col = rightCol; col >= rightCol - 1; col--) {
                    if (state.reserved[row][col]) {
                        continue;
                    }

                    if (bitIndex >= bits.length) {
                        throw new IllegalStateException("Matrix has more writable data cells than final QR bits.");
                    }
                    boolean bit = bits[bitIndex++];
                    state.modules[row][col] = bit;
                    state.dataCells[row][col] = true;
                }
            }

            upwards = !upwards;
        }

        if (bitIndex != bits.length) {
            throw new IllegalStateException("Not all QR data bits were placed into the matrix.");
        }
    }

    /**
     * Verifies that the number of writable matrix cells exactly matches the
     * number of final QR bits supplied by the payload layer.
     */
    private static void validateMatrixCapacity(MatrixState state, int finalBitCount) {
        int writableCells = 0;
        for (int row = 0; row < state.size; row++) {
            for (int col = 0; col < state.size; col++) {
                if (!state.reserved[row][col]) {
                    writableCells++;
                }
            }
        }

        if (writableCells != finalBitCount) {
            throw new IllegalStateException("Matrix writable cell count does not match final QR bit count.");
        }
    }

    /**
     * Applies one QR mask pattern to data cells only.
     */
    private static void applyMask(MatrixState state, int mask) {
        for (int row = 0; row < state.size; row++) {
            for (int col = 0; col < state.size; col++) {
                if (!state.dataCells[row][col]) {
                    continue;
                }
                if (maskBit(mask, row, col)) {
                    state.modules[row][col] = !state.modules[row][col];
                }
            }
        }
    }

    /**
     * Evaluates the QR mask predicate for one mask id and one matrix cell.
     */
    private static boolean maskBit(int mask, int row, int col) {
        return switch (mask) {
            case 0 -> ((row + col) & 1) == 0;
            case 1 -> (row & 1) == 0;
            case 2 -> col % 3 == 0;
            case 3 -> (row + col) % 3 == 0;
            case 4 -> ((row / 2) + (col / 3)) % 2 == 0;
            case 5 -> ((row * col) % 2) + ((row * col) % 3) == 0;
            case 6 -> ((((row * col) % 2) + ((row * col) % 3)) & 1) == 0;
            case 7 -> ((((row + col) % 2) + ((row * col) % 3)) & 1) == 0;
            default -> throw new IllegalArgumentException("Unsupported mask: " + mask);
        };
    }

    /**
     * Writes the 15 QR format-information bits for the selected
     * error-correction level and mask id.
     */
    private static void writeFormatInfo(MatrixState state,
                                        QrEncoder.ErrorCorrectionLevel ecLevel,
                                        int mask) {
        int formatBits = QrSpec.getFormatBits(ecLevel, mask);

        for (int i = 0; i <= 5; i++) {
            state.setFunctionModule(i, 8, bitAt(formatBits, i));
        }
        state.setFunctionModule(7, 8, bitAt(formatBits, 6));
        state.setFunctionModule(8, 8, bitAt(formatBits, 7));
        state.setFunctionModule(8, 7, bitAt(formatBits, 8));
        for (int i = 9; i < 15; i++) {
            state.setFunctionModule(8, 14 - i, bitAt(formatBits, i));
        }

        for (int i = 0; i < 8; i++) {
            state.setFunctionModule(8, state.size - 1 - i, bitAt(formatBits, i));
        }
        for (int i = 8; i < 15; i++) {
            state.setFunctionModule(state.size - 15 + i, 8, bitAt(formatBits, i));
        }
        state.setFunctionModule(state.size - 8, 8, true);
    }

    /**
     * Writes the 18 QR version-information bits for versions 7 and above.
     */
    private static void writeVersionInfo(MatrixState state, int version) {
        if (version < 7) {
            return;
        }

        int versionBits = QrSpec.getVersionBits(version);
        for (int i = 0; i < 18; i++) {
            boolean bit = bitAt(versionBits, i);
            int minor = i % 3;
            int major = i / 3;

            state.setFunctionModule(state.size - 11 + minor, major, bit);
            state.setFunctionModule(major, state.size - 11 + minor, bit);
        }
    }

    private static boolean bitAt(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    /**
     * Scores a complete masked QR matrix using all four standard QR penalty
     * categories.
     */
    private static int scoreMask(MatrixState state) {
        return scoreRuns(state.modules)
                + scoreBlocks(state.modules)
                + scoreFinderLikePatterns(state.modules)
                + scoreDarkBalance(state.modules);
    }

    /**
     * Scores long same-color runs across rows and columns (QR penalty rule N1).
     */
    private static int scoreRuns(boolean[][] modules) {
        int penalty = 0;
        int size = modules.length;

        for (int row = 0; row < size; row++) {
            penalty += scoreLineRuns(modules[row]);
        }

        for (int col = 0; col < size; col++) {
            boolean[] line = new boolean[size];
            for (int row = 0; row < size; row++) {
                line[row] = modules[row][col];
            }
            penalty += scoreLineRuns(line);
        }

        return penalty;
    }

    /**
     * Scores one row or column for the N1 long-run penalty rule.
     */
    private static int scoreLineRuns(boolean[] line) {
        int penalty = 0;
        boolean current = line[0];
        int runLength = 1;

        for (int i = 1; i < line.length; i++) {
            if (line[i] == current) {
                runLength++;
                continue;
            }

            if (runLength >= 5) {
                penalty += PENALTY_N1 + (runLength - 5);
            }
            current = line[i];
            runLength = 1;
        }

        if (runLength >= 5) {
            penalty += PENALTY_N1 + (runLength - 5);
        }

        return penalty;
    }

    /**
     * Scores 2x2 same-color blocks (QR penalty rule N2).
     */
    private static int scoreBlocks(boolean[][] modules) {
        int penalty = 0;
        int size = modules.length;

        for (int row = 0; row < size - 1; row++) {
            for (int col = 0; col < size - 1; col++) {
                boolean color = modules[row][col];
                if (modules[row][col + 1] == color
                        && modules[row + 1][col] == color
                        && modules[row + 1][col + 1] == color) {
                    penalty += PENALTY_N2;
                }
            }
        }

        return penalty;
    }

    /**
     * Scores finder-like run patterns that can confuse scanners (QR penalty
     * rule N3).
     */
    private static int scoreFinderLikePatterns(boolean[][] modules) {
        int penalty = 0;
        int size = modules.length;

        for (int row = 0; row < size; row++) {
            penalty += scoreFinderLikeLine(modules[row], size);
        }

        for (int col = 0; col < size; col++) {
            boolean[] line = new boolean[size];
            for (int row = 0; row < size; row++) {
                line[row] = modules[row][col];
            }
            penalty += scoreFinderLikeLine(line, size);
        }

        return penalty;
    }

    /**
     * Scores one row or column for finder-like patterns using QR's run-history
     * technique.
     */
    private static int scoreFinderLikeLine(boolean[] line, int size) {
        int penalty = 0;
        boolean currentColor = false;
        int currentRunLength = 0;
        int[] runHistory = new int[7];

        for (boolean cell : line) {
            if (cell == currentColor) {
                currentRunLength++;
                continue;
            }

            addFinderPenaltyHistory(currentRunLength, runHistory, size);
            if (!currentColor) {
                penalty += countFinderPenaltyPatterns(runHistory) * PENALTY_N3;
            }
            currentColor = cell;
            currentRunLength = 1;
        }

        penalty += terminateFinderPenaltyCount(currentColor, currentRunLength, runHistory, size) * PENALTY_N3;
        return penalty;
    }

    /**
     * Checks whether the current run-history window matches one of the two
     * finder-like N3 patterns.
     */
    private static int countFinderPenaltyPatterns(int[] runHistory) {
        int n = runHistory[1];
        boolean core = n > 0
                && runHistory[2] == n
                && runHistory[3] == n * 3
                && runHistory[4] == n
                && runHistory[5] == n;

        int count = 0;
        if (core && runHistory[0] >= n * 4 && runHistory[6] >= n) {
            count++;
        }
        if (core && runHistory[6] >= n * 4 && runHistory[0] >= n) {
            count++;
        }
        return count;
    }

    /**
     * Finalizes N3 scoring for the last run in a row or column.
     */
    private static int terminateFinderPenaltyCount(boolean currentColor,
                                                   int currentRunLength,
                                                   int[] runHistory,
                                                   int size) {
        if (currentColor) {
            addFinderPenaltyHistory(currentRunLength, runHistory, size);
            currentRunLength = 0;
        }

        currentRunLength += size;
        addFinderPenaltyHistory(currentRunLength, runHistory, size);
        return countFinderPenaltyPatterns(runHistory);
    }

    /**
     * Shifts a completed run into the finder-pattern history window.
     */
    private static void addFinderPenaltyHistory(int currentRunLength,
                                                int[] runHistory,
                                                int size) {
        if (runHistory[0] == 0) {
            currentRunLength += size;
        }

        System.arraycopy(runHistory, 0, runHistory, 1, runHistory.length - 1);
        runHistory[0] = currentRunLength;
    }

    /**
     * Scores dark/light balance against the QR target of roughly 50% dark
     * modules (QR penalty rule N4).
     */
    private static int scoreDarkBalance(boolean[][] modules) {
        int darkCount = 0;
        int total = modules.length * modules.length;

        for (boolean[] row : modules) {
            for (boolean cell : row) {
                if (cell) {
                    darkCount++;
                }
            }
        }

        int deviationSteps = Math.abs((darkCount * 20) - (total * 10)) / total;
        return deviationSteps * PENALTY_N4;
    }

    private static MatrixState copyOf(MatrixState source) {
        MatrixState copy = new MatrixState(source.size);
        for (int row = 0; row < source.size; row++) {
            System.arraycopy(source.modules[row], 0, copy.modules[row], 0, source.size);
            System.arraycopy(source.reserved[row], 0, copy.reserved[row], 0, source.size);
            System.arraycopy(source.dataCells[row], 0, copy.dataCells[row], 0, source.size);
        }
        return copy;
    }

    private static boolean[][] copyModules(boolean[][] modules) {
        boolean[][] copy = new boolean[modules.length][];
        for (int row = 0; row < modules.length; row++) {
            copy[row] = modules[row].clone();
        }
        return copy;
    }

    record MatrixBuild(boolean[][] modules,
                       int chosenMask,
                       int[] maskPenalties,
                       int size,
                       int finalBitCount) {
        MatrixBuild {
            modules = copyModules(modules);
            maskPenalties = maskPenalties.clone();
        }
    }

    /**
     * Mutable working matrix used while function patterns, data bits, and mask
     * candidates are being assembled.
     */
    private static final class MatrixState {
        private final boolean[][] modules;
        private final boolean[][] reserved;
        private final boolean[][] dataCells;
        private final int size;

        private MatrixState(int size) {
            this.size = size;
            this.modules = new boolean[size][size];
            this.reserved = new boolean[size][size];
            this.dataCells = new boolean[size][size];
        }

        private boolean inBounds(int row, int col) {
            return row >= 0 && row < size && col >= 0 && col < size;
        }

        private void setFunctionModule(int row, int col, boolean value) {
            modules[row][col] = value;
            reserved[row][col] = true;
        }
    }
}
