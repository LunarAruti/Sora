package ucadmin.tools.dpcode;

import ucadmin.tools.Colors;

import java.nio.file.Path;

/**
 * First-pass DP Code encoder/decoder.
 *
 * <p>This encoder increment builds the logical DP grid, writes the fixed
 * structural markers, compiles bootstrap and header metadata into real
 * colors, places raw payload bytes into payload-eligible cells, and returns
 * a logical {@code Colors.Color[][]} that can be handed to the pixel
 * generator. The same class also provides the ideal-path decode pass for
 * pristine in-memory logical grids.</p>
 *
 * <p>The current ECC stage uses deterministic Reed-Solomon chunking. Payload
 * bytes are split into balanced blocks, each block gets its own parity bytes,
 * payload cells are written into forward traversal slots, and ECC cells are
 * written into deterministic interleaved slots that are scattered across the
 * full payload traversal while still being written in reverse slot order.</p>
 */
public final class DPEncoder {

    private static final boolean ISSUE_DIAGNOSTICS = true;
    private static final boolean HEADER_COLOR_DEBUG_LOGGING = true;
    private static final int MASK_COUNT = 8;
    private static final double MASK_EARLY_EXIT_FACTOR = 0.33;
    private static final Colors.Color BLACK = new Colors.Color(Colors.Preset.BLACK);
    private static final Colors.Color WHITE = new Colors.Color(Colors.Preset.WHITE);
    private static final Colors.Color PAYLOAD_PADDING = new Colors.Color(238, 238, 238);
    private static final int[] GF_EXP = new int[512];
    private static final int[] GF_LOG = new int[256];

    static {
        int value = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = value;
            GF_LOG[value] = i;
            value <<= 1;
            if ((value & 0x100) != 0) {
                value ^= 0x11D;
            }
        }
        for (int i = 255; i < GF_EXP.length; i++) {
            GF_EXP[i] = GF_EXP[i - 255];
        }
    }

    /**
     * First-pass ECC strength profiles for the DP encoder.
     *
     * <p>The current implementation sizes total Reed-Solomon parity
     * deterministically from this profile and then distributes that parity
     * across balanced payload blocks.</p>
     */
    public enum EccProfile {
        LOW(0, 1, 4),
        MEDIUM(1, 1, 2),
        HIGH(2, 1, 1),
        VERY_HIGH(3, 2, 1);

        private final int headerValue;
        private final int numerator;
        private final int denominator;

        EccProfile(int headerValue, int numerator, int denominator) {
            this.headerValue = headerValue;
            this.numerator = numerator;
            this.denominator = denominator;
        }

        /**
         * Returns the 2-bit header value used for this profile.
         *
         * @return 2-bit header value
         */
        public int getHeaderValue() {
            return headerValue;
        }

        int targetParityBytes(int payloadLength) {
            return (payloadLength * numerator + denominator - 1) / denominator;
        }

        static EccProfile fromHeaderValue(int headerValue) {
            for (EccProfile profile : values()) {
                if (profile.headerValue == headerValue) {
                    return profile;
                }
            }
            throw new IllegalArgumentException("Unknown ECC profile header value: " + headerValue);
        }
    }

    private DPEncoder() {}

    /**
     * Compiles a raw-payload DP symbol for the supplied payload metadata.
     *
     * <p>The payload bytes are packed into a continuous bitstream and written
     * into payload-eligible cells using the frozen payload traversal order and
     * {@code R, G, B} channel group order. The final partial cell, if any, is
     * padded with zero bits.</p>
     *
     * @param payloadBytes payload bytes whose metadata should be reflected in the header
     * @param logicalSize legal DP logical grid size
     * @param densityMode payload density mode to record in the header
     * @param bootstrapProfileId 3-bit bootstrap/profile identifier
     * @return DP compile result including the logical color grid and payload capacity metadata
     * @throws IllegalArgumentException if any argument is invalid
     */
    public static Result compileStructure(byte[] payloadBytes,
                                          int logicalSize,
                                          DPDensityMode densityMode,
                                          int bootstrapProfileId) {
        return compileStructure(payloadBytes, logicalSize, densityMode, bootstrapProfileId, EccProfile.LOW);
    }

    /**
     * Compiles a raw-payload DP symbol with the requested ECC profile.
     *
     * <p>Payload bytes are placed into forward traversal slots. Reed-Solomon
     * parity bytes are generated for the payload and then placed into evenly
     * scattered ECC slots across the full payload traversal. Those ECC slots
     * are written in reverse slot order so the payload and its protection are
     * both distributed and directionally opposed. Floater nodes are encoded as
     * four black arms around one white center cell.</p>
     *
     * @param payloadBytes payload bytes whose metadata should be reflected in the header
     * @param logicalSize legal DP logical grid size
     * @param densityMode payload density mode to record in the header
     * @param bootstrapProfileId 3-bit bootstrap/profile identifier
     * @param eccProfile ECC strength profile used to size and generate parity bytes
     * @return DP compile result including the logical color grid, payload stats, and ECC stats
     * @throws IllegalArgumentException if any argument is invalid
     */
    public static Result compileStructure(byte[] payloadBytes,
                                          int logicalSize,
                                          DPDensityMode densityMode,
                                          int bootstrapProfileId,
                                          EccProfile eccProfile) {
        if (payloadBytes == null) {
            throw new IllegalArgumentException("payloadBytes cannot be null");
        }
        if (densityMode == null) {
            throw new IllegalArgumentException("densityMode cannot be null");
        }
        if (eccProfile == null) {
            throw new IllegalArgumentException("eccProfile cannot be null");
        }

        DPReservedMask mask = DPReservedMask.create(logicalSize);
        int[] bootstrapBits = DPHeaderCodec.buildBootstrapBits(bootstrapProfileId);
        int payloadCrc = DPHeaderCodec.computePayloadCrc(payloadBytes);
        int payloadBitCount = payloadBytes.length * 8;
        int payloadCapacityBits = mask.getPayloadCells().size() * densityMode.getBitsPerCell();
        int payloadCellCount = mask.getPayloadCells().size();
        EccLayoutPlan eccPlan = planEccLayout(payloadBytes.length, payloadCellCount, densityMode, eccProfile);
        int dataCellCount = eccPlan.dataCellCount();
        byte[] eccBytes = generateParityStream(payloadBytes, eccPlan);
        int eccByteCount = eccPlan.totalParityBytes();
        int eccBitCount = eccBytes.length * 8;
        int eccCellCount = eccPlan.eccCellCount();
        CellPlacementPlan placementPlan = buildCellPlacementPlan(payloadCellCount, dataCellCount, eccCellCount);
        if ((dataCellCount + eccCellCount) > payloadCellCount) {
            logIssue("encode capacity failure"
                    + " payloadLength=" + payloadBytes.length
                    + ", logicalSize=" + logicalSize
                    + ", densityMode=" + densityMode
                    + ", eccProfile=" + eccProfile
                    + ", payloadCells=" + payloadCellCount
                    + ", dataCells=" + dataCellCount
                    + ", eccCells=" + eccCellCount);
            throw new IllegalArgumentException(
                    "Payload plus ECC exceeds DP capacity for logicalSize=" + logicalSize +
                            ", densityMode=" + densityMode +
                            ", eccProfile=" + eccProfile +
                            ": dataCells=" + dataCellCount +
                            ", eccCells=" + eccCellCount +
                            ", payloadCells=" + payloadCellCount
            );
        }

        MaskSelectionResult maskSelection = selectBestMask(
                logicalSize,
                mask.getPayloadCells(),
                densityMode,
                payloadBytes,
                eccBytes,
                placementPlan
        );

        DPHeader header = DPHeader.createV1(
                logicalSize,
                densityMode,
                bitsToInt(bootstrapBits),
                eccProfile.getHeaderValue(),
                payloadBytes.length,
                payloadCrc
        ).withMinorVersion(1).withMaskId(maskSelection.maskId());
        header = DPHeaderCodec.finalizeHeader(header);
        Colors.Color[] encodedHeaderCells = DPHeaderCodec.encodeHeaderCells(header);
        logHeaderCells("encode header cells", mask.getHeaderCells(), encodedHeaderCells);

        Colors.Color[][] grid = new Colors.Color[logicalSize][logicalSize];
        fillPayloadCells(grid, mask, payloadBytes, eccBytes, densityMode, placementPlan, header.maskId());
        writeFixedStructures(grid, mask, bootstrapBits, encodedHeaderCells);

        return new Result(
                grid,
                logicalSize,
                densityMode,
                eccProfile,
                payloadBytes.length,
                payloadCrc,
                payloadCellCount,
                payloadCapacityBits,
                payloadBitCount,
                eccBytes.length,
                eccBitCount,
                dataCellCount,
                eccCellCount,
                buildRoleMapDump(mask),
                buildTraversalDump("Bootstrap", mask.getBootstrapCells()),
                buildTraversalDump("Header", mask.getHeaderCells()),
                buildTraversalDump("Payload", mask.getPayloadCells()),
                buildIndexedTraversalDump("Payload data slots", mask.getPayloadCells(), placementPlan.dataTraversalIndices(), false),
                buildIndexedTraversalDump("Payload ECC slots", mask.getPayloadCells(), placementPlan.eccTraversalIndices(), true)
        );
    }

    /**
     * Decodes a logical DP grid produced by the current DP encoder increment.
     *
     * <p>This decode path assumes a pristine logical grid in memory. It validates
     * fixed structures, reads bootstrap and header metadata, reconstructs raw
     * payload bytes from payload cells, and attempts ECC/CRC validation.
     * Once bootstrap and header decode succeed, this path returns the best
     * payload bytes it can recover and records payload-side issues in the
     * decode result instead of discarding the read entirely.</p>
     *
     * @param logicalGrid logical DP color grid excluding the quiet zone
     * @return decoded payload and metadata
     * @throws IllegalArgumentException if the grid is invalid or bootstrap/header
     *                                  decode fails before payload recovery can begin
     */
    public static DecodedResult decodeStructure(Colors.Color[][] logicalGrid) {
        validateLogicalGrid(logicalGrid);

        int logicalSize = logicalGrid.length;
        DPReservedMask mask = DPReservedMask.create(logicalSize);

        validateFixedStructures(logicalGrid, mask);
        int[] bootstrapBits = readBootstrapBits(logicalGrid, mask);
        validateBootstrapParity(bootstrapBits);

        Colors.Color[] headerCells = readHeaderCells(logicalGrid, mask);
        logHeaderCells("decode sampled header cells", mask.getHeaderCells(), headerCells);
        DPHeader header;
        try {
            header = DPHeaderCodec.decodeHeaderCells(headerCells);
        } catch (IllegalArgumentException exception) {
            logIssue("header decode failure: " + exception.getMessage());
            logHeaderCells("decode sampled header cells on failure", mask.getHeaderCells(), headerCells);
            throw exception;
        }
        if (header.logicalSize() != logicalSize) {
            throw new IllegalArgumentException(
                    "Header size mismatch: header N=" + header.logicalSize() + ", grid N=" + logicalSize
            );
        }
        if (header.bootstrapEcho() != bitsToInt(bootstrapBits)) {
            throw new IllegalArgumentException(
                    "Bootstrap echo mismatch: bootstrap=" + bitsToInt(bootstrapBits) +
                            ", header=" + header.bootstrapEcho()
            );
        }

        int requiredBits = header.payloadLength() * 8;
        int availableBits = mask.getPayloadCells().size() * header.densityMode().getBitsPerCell();
        if (requiredBits > availableBits) {
            throw new IllegalArgumentException(
                    "Header payload length exceeds payload capacity: " + requiredBits + " > " + availableBits
            );
        }

        EccProfile eccProfile = EccProfile.fromHeaderValue(header.eccProfile());
        EccLayoutPlan eccPlan = planEccLayout(
                header.payloadLength(),
                mask.getPayloadCells().size(),
                header.densityMode(),
                eccProfile
        );
        int dataCellCount = eccPlan.dataCellCount();
        int eccByteCount = eccPlan.totalParityBytes();
        int eccBitCount = eccByteCount * 8;
        int eccCellCount = eccPlan.eccCellCount();
        CellPlacementPlan placementPlan = buildCellPlacementPlan(mask.getPayloadCells().size(), dataCellCount, eccCellCount);
        if ((dataCellCount + eccCellCount) > mask.getPayloadCells().size()) {
            logIssue("decode capacity failure"
                    + " logicalSize=" + logicalSize
                    + ", densityMode=" + header.densityMode()
                    + ", eccProfile=" + eccProfile
                    + ", payloadCells=" + mask.getPayloadCells().size()
                    + ", dataCells=" + dataCellCount
                    + ", eccCells=" + eccCellCount);
            throw new IllegalArgumentException("Decoded data/ECC partition exceeds payload traversal capacity");
        }

        byte[] payloadBytes = readPayloadBytes(
                logicalGrid,
                mask,
                header.densityMode(),
                header.payloadLength(),
                placementPlan.dataTraversalIndices(),
                resolvePayloadMaskId(header)
        );
        byte[] eccBytes = readEccBytes(
                logicalGrid,
                mask,
                header.densityMode(),
                eccByteCount,
                placementPlan.eccTraversalIndices(),
                resolvePayloadMaskId(header)
        );
        byte[] rawPayloadBytes = payloadBytes.clone();
        byte[] rawEccBytes = eccBytes.clone();
        PayloadCorrectionResult payloadCorrection = correctCodewords(payloadBytes, eccBytes, eccPlan);
        payloadBytes = payloadCorrection.copyCorrectedPayload();
        int decodeErrors = payloadCorrection.errors();
        int computedPayloadCrc = DPHeaderCodec.computePayloadCrc(payloadBytes);
        boolean payloadVerified = payloadCorrection.payloadVerified();
        if (computedPayloadCrc != header.payloadCrc()) {
            logIssue("decode CRC mismatch"
                    + " logicalSize=" + logicalSize
                    + ", densityMode=" + header.densityMode()
                    + ", eccProfile=" + eccProfile
                    + ", storedCrc=0x" + Integer.toHexString(header.payloadCrc()).toUpperCase()
                    + ", computedCrc=0x" + Integer.toHexString(computedPayloadCrc).toUpperCase()
                    + ", rawPayloadPreview=" + formatBytePreview(rawPayloadBytes, 48)
                    + ", rawEccPreview=" + formatBytePreview(rawEccBytes, 48)
                    + ", correctedPayloadPreview=" + formatBytePreview(payloadBytes, 48)
                    + ", dataSlots=" + formatIntArray(placementPlan.dataTraversalIndices(), 24)
                    + ", eccSlotsWriteOrder=" + formatIntArrayReversed(placementPlan.eccTraversalIndices(), 24));
            decodeErrors++;
            payloadVerified = false;
        }

        return new DecodedResult(
                payloadBytes,
                logicalSize,
                header.densityMode(),
                eccProfile,
                header.payloadLength(),
                header.payloadCrc(),
                decodeErrors,
                payloadVerified,
                bitsToInt(bootstrapBits),
                buildRoleMapDump(mask),
                buildTraversalDump("Bootstrap", mask.getBootstrapCells()),
                buildTraversalDump("Header", mask.getHeaderCells()),
                buildTraversalDump("Payload", mask.getPayloadCells()),
                buildIndexedTraversalDump("Payload data slots", mask.getPayloadCells(), placementPlan.dataTraversalIndices(), false),
                buildIndexedTraversalDump("Payload ECC slots", mask.getPayloadCells(), placementPlan.eccTraversalIndices(), true)
        );
    }

    /**
     * Decodes an exported DP image using the fixed quiet-zone geometry rules.
     *
     * <p>This method infers candidate logical geometries from the square image
     * dimensions, samples each candidate grid at cell centers, and then runs
     * the existing logical-grid decoder until one candidate yields a valid
     * bootstrap/header decode.</p>
     *
     * @param imagePath path to the exported DP image
     * @return decoded payload and metadata
     * @throws IllegalArgumentException if the image cannot be sampled or no candidate geometry
     *                                  reaches a valid bootstrap/header decode
     */
    public static DecodedResult decodeImage(Path imagePath) {
        if (imagePath == null) {
            throw new IllegalArgumentException("imagePath cannot be null");
        }

        java.util.List<DPImageSampler.SampleCandidate> candidates = DPImageSampler.sampleCandidates(imagePath);
        logIssue("decodeImage candidate summary count=" + candidates.size());
        StringBuilder failureSummary = new StringBuilder();
        failureSummary.append("Unable to decode DP image from ").append(imagePath).append(System.lineSeparator());
        failureSummary.append("Tried ").append(candidates.size()).append(" geometry candidate(s):")
                .append(System.lineSeparator());

        for (DPImageSampler.SampleCandidate candidate : candidates) {
            try {
                logIssue("decodeImage attempting candidate"
                        + " logicalSize=" + candidate.logicalSize()
                        + ", cellWidthPx=" + String.format("%.3f", candidate.cellWidthPx())
                        + ", cellHeightPx=" + String.format("%.3f", candidate.cellHeightPx())
                        + ", structureScore=" + String.format("%.4f", candidate.structureScore())
                        + ", bounds=" + candidate.bounds());
                return decodeStructure(candidate.logicalGrid());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                logIssue("decodeImage candidate failed"
                        + " logicalSize=" + candidate.logicalSize()
                        + ", bounds=" + candidate.bounds()
                        + ", reason=" + exception.getMessage());
                failureSummary.append("logicalSize=")
                        .append(candidate.logicalSize())
                        .append(", cellWidthPx=")
                        .append(String.format("%.3f", candidate.cellWidthPx()))
                        .append(", cellHeightPx=")
                        .append(String.format("%.3f", candidate.cellHeightPx()))
                        .append(", structureScore=")
                        .append(String.format("%.4f", candidate.structureScore()))
                        .append(", bounds=")
                        .append(candidate.bounds())
                        .append(" -> ")
                        .append(exception.getMessage())
                        .append(System.lineSeparator());
            }
        }

        throw new IllegalArgumentException(failureSummary.toString());
    }

    /**
     * Structure-only compile result for the current encoder increment.
     *
     * @param logicalGrid logical color grid excluding the quiet zone
     * @param logicalSize logical DP grid size
     * @param densityMode payload density mode recorded in the header
     * @param eccProfile ECC profile recorded in the header
     * @param payloadLength stored payload length in bytes
     * @param payloadCrc CRC-16 of the supplied payload bytes
     * @param payloadCellCount number of payload-eligible cells in the logical grid
     * @param payloadCapacityBits total bits available across the full payload traversal region
     * @param payloadBitCount number of payload bits written from the supplied payload bytes
     * @param eccByteCount number of parity bytes generated for the payload
     * @param eccBitCount number of parity bits generated for the payload
     * @param dataCellCount number of forward-written payload-data slots
     * @param eccCellCount number of reverse-written interleaved ECC slots
     * @param roleMapDump ASCII dump of the logical cell-role grid
     * @param bootstrapTraversalDump ordered bootstrap traversal dump
     * @param headerTraversalDump ordered header traversal dump
     * @param payloadTraversalDump ordered payload traversal dump
     * @param payloadDataRegionDump ordered dump of the forward payload-data slots
     * @param payloadEccRegionDump ordered dump of the reverse-written ECC slots
     */
    public record Result(Colors.Color[][] logicalGrid,
                         int logicalSize,
                         DPDensityMode densityMode,
                         EccProfile eccProfile,
                         int payloadLength,
                         int payloadCrc,
                         int payloadCellCount,
                         int payloadCapacityBits,
                         int payloadBitCount,
                         int eccByteCount,
                         int eccBitCount,
                         int dataCellCount,
                         int eccCellCount,
                         String roleMapDump,
                         String bootstrapTraversalDump,
                         String headerTraversalDump,
                         String payloadTraversalDump,
                         String payloadDataRegionDump,
                         String payloadEccRegionDump) {

        public Result {
            if (logicalGrid == null) {
                throw new IllegalArgumentException("logicalGrid cannot be null");
            }
            Colors.Color[][] copy = new Colors.Color[logicalGrid.length][];
            for (int y = 0; y < logicalGrid.length; y++) {
                if (logicalGrid[y] == null) {
                    throw new IllegalArgumentException("logicalGrid row " + y + " cannot be null");
                }
                copy[y] = logicalGrid[y].clone();
            }
            logicalGrid = copy;
            if (eccProfile == null) {
                throw new IllegalArgumentException("eccProfile cannot be null");
            }
            if (roleMapDump == null
                    || bootstrapTraversalDump == null
                    || headerTraversalDump == null
                    || payloadTraversalDump == null
                    || payloadDataRegionDump == null
                    || payloadEccRegionDump == null) {
                throw new IllegalArgumentException("debug dumps cannot be null");
            }
        }

        /**
         * Creates a defensive copy of the logical color grid.
         *
         * @return deep copy of the logical color grid
         */
        public Colors.Color[][] copyLogicalGrid() {
            Colors.Color[][] copy = new Colors.Color[logicalGrid.length][];
            for (int y = 0; y < logicalGrid.length; y++) {
                copy[y] = logicalGrid[y].clone();
            }
            return copy;
        }

        /**
         * Returns a compact human-readable summary of the compiled symbol.
         *
         * @return one string containing the main size, density, and payload statistics
         */
        public String summary() {
            return "DPEncoder.Result{" +
                    "logicalSize=" + logicalSize +
                    ", densityMode=" + densityMode +
                    ", eccProfile=" + eccProfile +
                    ", payloadLength=" + payloadLength +
                    ", payloadCrc=0x" + Integer.toHexString(payloadCrc).toUpperCase() +
                    ", payloadCellCount=" + payloadCellCount +
                    ", payloadBitCount=" + payloadBitCount +
                    ", payloadCapacityBits=" + payloadCapacityBits +
                    ", eccByteCount=" + eccByteCount +
                    ", eccBitCount=" + eccBitCount +
                    ", dataCellCount=" + dataCellCount +
                    ", eccCellCount=" + eccCellCount +
                    '}';
        }
    }

    /**
     * Ideal-path decode result for the current encoder increment.
     *
     * @param payloadBytes decoded payload bytes
     * @param logicalSize decoded logical DP grid size
     * @param densityMode decoded payload density mode
     * @param eccProfile decoded ECC profile
     * @param payloadLength decoded payload length in bytes
     * @param payloadCrc decoded payload CRC from the header
     * @param errors number of payload-side decode issues encountered after the
     *               header was read successfully; zero means the payload path
     *               validated cleanly
     * @param payloadVerified true when the payload bytes are considered trustworthy
     *                        after ECC/CRC validation, false when the payload is
     *                        best-effort only
     * @param bootstrapEcho decoded 4-bit bootstrap value
     * @param roleMapDump ASCII dump of the logical cell-role grid
     * @param bootstrapTraversalDump ordered bootstrap traversal dump
     * @param headerTraversalDump ordered header traversal dump
     * @param payloadTraversalDump ordered payload traversal dump
     * @param payloadDataRegionDump ordered dump of the forward payload-data slots
     * @param payloadEccRegionDump ordered dump of the reverse-written ECC slots
     */
    public record DecodedResult(byte[] payloadBytes,
                                int logicalSize,
                                DPDensityMode densityMode,
                                EccProfile eccProfile,
                                int payloadLength,
                                int payloadCrc,
                                int errors,
                                boolean payloadVerified,
                                int bootstrapEcho,
                                String roleMapDump,
                                String bootstrapTraversalDump,
                                String headerTraversalDump,
                                String payloadTraversalDump,
                                String payloadDataRegionDump,
                                String payloadEccRegionDump) {

        public DecodedResult {
            if (payloadBytes == null) {
                throw new IllegalArgumentException("payloadBytes cannot be null");
            }
            if (errors < 0) {
                throw new IllegalArgumentException("errors cannot be negative");
            }
            payloadBytes = payloadBytes.clone();
        }

        /**
         * Returns a defensive copy of the decoded payload bytes.
         *
         * @return copy of the decoded payload bytes
         */
        public byte[] copyPayloadBytes() {
            return payloadBytes.clone();
        }

        /**
         * Returns whether the decoded payload bytes are considered verified.
         *
         * <p>A verified payload is one whose payload-side decode path ended in a
         * trustworthy state after ECC and CRC validation. A value of
         * {@code false} means the decoder returned best-effort bytes after a
         * successful bootstrap/header read, but payload integrity was not fully
         * proven.</p>
         *
         * @return true when the payload bytes are considered trustworthy after decode
         */
        public boolean isPayloadVerified() {
            return payloadVerified;
        }

        /**
         * Returns a compact human-readable summary of the decoded symbol.
         *
         * @return one string containing the main decoded metadata
         */
        public String summary() {
            return "DPEncoder.DecodedResult{" +
                    "logicalSize=" + logicalSize +
                    ", densityMode=" + densityMode +
                    ", eccProfile=" + eccProfile +
                    ", payloadLength=" + payloadLength +
                    ", payloadCrc=0x" + Integer.toHexString(payloadCrc).toUpperCase() +
                    ", errors=" + errors +
                    ", payloadVerified=" + payloadVerified +
                    ", bootstrapEcho=" + bootstrapEcho +
                    '}';
        }
    }

    private static void fillPayloadCells(Colors.Color[][] grid,
                                         DPReservedMask mask,
                                         byte[] payloadBytes,
                                         byte[] eccBytes,
                                         DPDensityMode densityMode,
                                         CellPlacementPlan placementPlan,
                                         int maskId) {
        java.util.List<DPReservedMask.Point> payloadCells = mask.getPayloadCells();
        for (DPReservedMask.Point point : payloadCells) {
            grid[point.y()][point.x()] = PAYLOAD_PADDING;
        }

        writeBitstreamToSlots(
                grid,
                payloadCells,
                payloadBytes,
                densityMode,
                placementPlan.dataTraversalIndices(),
                false,
                maskId
        );
        writeBitstreamToSlots(
                grid,
                payloadCells,
                eccBytes,
                densityMode,
                placementPlan.eccTraversalIndices(),
                true,
                maskId
        );
    }

    private static void writeBitstreamToSlots(Colors.Color[][] grid,
                                              java.util.List<DPReservedMask.Point> payloadCells,
                                              byte[] bytes,
                                              DPDensityMode densityMode,
                                              int[] traversalIndices,
                                              boolean reverseSlotOrder,
                                              int maskId) {
        int bitIndex = 0;
        int totalBits = bytes.length * 8;

        for (int cellOffset = 0; cellOffset < traversalIndices.length; cellOffset++) {
            int traversalIndex = reverseSlotOrder
                    ? traversalIndices[traversalIndices.length - 1 - cellOffset]
                    : traversalIndices[cellOffset];
            DPReservedMask.Point point = payloadCells.get(traversalIndex);

            if (bitIndex >= totalBits) {
                grid[point.y()][point.x()] = PAYLOAD_PADDING;
                continue;
            }

            int cellValue = 0;
            for (int bit = 0; bit < densityMode.getBitsPerCell(); bit++) {
                cellValue <<= 1;
                if (bitIndex < totalBits) {
                    cellValue |= readBit(bytes, bitIndex);
                    bitIndex++;
                }
            }
            cellValue = applyMaskToCellBits(cellValue, point.x(), point.y(), densityMode, maskId);
            grid[point.y()][point.x()] = densityMode.colorFromCellBits(cellValue);
        }
    }

    private static void writeFixedStructures(Colors.Color[][] grid,
                                             DPReservedMask mask,
                                             int[] bootstrapBits,
                                             Colors.Color[] headerCells) {
        int headerIndex = 0;
        for (DPReservedMask.Point point : mask.getHeaderCells()) {
            grid[point.y()][point.x()] = headerCells[headerIndex++];
        }

        int bootstrapIndex = 0;
        for (DPReservedMask.Point point : mask.getBootstrapCells()) {
            grid[point.y()][point.x()] = (bootstrapBits[bootstrapIndex++] == 0) ? WHITE : BLACK;
        }

        int logicalSize = grid.length;
        for (int y = 0; y < logicalSize; y++) {
            for (int x = 0; x < logicalSize; x++) {
                switch (mask.getRole(x, y)) {
                    case TOP_LEFT_MARKER, BOTTOM_RIGHT_MARKER, CONTROL_BLACK, FLOATER_ARM_BLACK -> grid[y][x] = BLACK;
                    case TOP_TIMING_WHITE, RIGHT_TIMING_WHITE, FLOATER_CENTER_WHITE -> grid[y][x] = WHITE;
                    case TOP_TIMING_BLACK, RIGHT_TIMING_BLACK -> grid[y][x] = BLACK;
                    case BOOTSTRAP, HEADER, PAYLOAD -> {
                        // These cells are written by their dedicated payload/bootstrap/header passes.
                    }
                }
            }
        }
    }

    private static int bitsToInt(int[] bits) {
        int value = 0;
        for (int bit : bits) {
            value = (value << 1) | (bit & 1);
        }
        return value;
    }

    private static int readBit(byte[] payloadBytes, int bitIndex) {
        int byteIndex = bitIndex / 8;
        int bitOffset = 7 - (bitIndex % 8);
        return (payloadBytes[byteIndex] >>> bitOffset) & 1;
    }

    private static MaskSelectionResult selectBestMask(int logicalSize,
                                                      java.util.List<DPReservedMask.Point> payloadCells,
                                                      DPDensityMode densityMode,
                                                      byte[] payloadBytes,
                                                      byte[] eccBytes,
                                                      CellPlacementPlan placementPlan) {
        if (logicalSize <= 0) {
            throw new IllegalArgumentException("logicalSize must be positive");
        }
        if (payloadCells == null) {
            throw new IllegalArgumentException("payloadCells cannot be null");
        }
        if (densityMode == null) {
            throw new IllegalArgumentException("densityMode cannot be null");
        }
        if (payloadBytes == null) {
            throw new IllegalArgumentException("payloadBytes cannot be null");
        }
        if (eccBytes == null) {
            throw new IllegalArgumentException("eccBytes cannot be null");
        }
        if (placementPlan == null) {
            throw new IllegalArgumentException("placementPlan cannot be null");
        }

        int occupiedCellCount = placementPlan.dataTraversalIndices().length + placementPlan.eccTraversalIndices().length;
        if (occupiedCellCount == 0) {
            return new MaskSelectionResult(0, 0);
        }

        int earlyExitThreshold = Math.max(logicalSize * 2, (int) Math.ceil(occupiedCellCount * MASK_EARLY_EXIT_FACTOR));
        MaskSelectionResult best = null;
        for (int maskId = 0; maskId < MASK_COUNT; maskId++) {
            int[][] maskedGrid = buildMaskedPayloadSymbolGrid(
                    logicalSize,
                    payloadCells,
                    densityMode,
                    payloadBytes,
                    eccBytes,
                    placementPlan,
                    maskId
            );
            int score = scoreMaskedPayloadGrid(maskedGrid, densityMode);
            MaskSelectionResult candidate = new MaskSelectionResult(maskId, score);
            if (best == null || candidate.score() < best.score()) {
                best = candidate;
            }
            if (candidate.score() <= earlyExitThreshold) {
                return candidate;
            }
        }
        return best;
    }

    private static int[][] buildMaskedPayloadSymbolGrid(int logicalSize,
                                                        java.util.List<DPReservedMask.Point> payloadCells,
                                                        DPDensityMode densityMode,
                                                        byte[] payloadBytes,
                                                        byte[] eccBytes,
                                                        CellPlacementPlan placementPlan,
                                                        int maskId) {
        int[][] grid = new int[logicalSize][logicalSize];
        for (int y = 0; y < logicalSize; y++) {
            java.util.Arrays.fill(grid[y], -1);
        }

        placeMaskedCellValuesInGrid(
                grid,
                payloadCells,
                payloadBytes,
                densityMode,
                placementPlan.dataTraversalIndices(),
                false,
                maskId
        );
        placeMaskedCellValuesInGrid(
                grid,
                payloadCells,
                eccBytes,
                densityMode,
                placementPlan.eccTraversalIndices(),
                true,
                maskId
        );
        return grid;
    }

    private static void placeMaskedCellValuesInGrid(int[][] grid,
                                                    java.util.List<DPReservedMask.Point> payloadCells,
                                                    byte[] bytes,
                                                    DPDensityMode densityMode,
                                                    int[] traversalIndices,
                                                    boolean reverseSlotOrder,
                                                    int maskId) {
        int bitIndex = 0;
        int totalBits = bytes.length * 8;

        for (int cellOffset = 0; cellOffset < traversalIndices.length; cellOffset++) {
            int traversalIndex = reverseSlotOrder
                    ? traversalIndices[traversalIndices.length - 1 - cellOffset]
                    : traversalIndices[cellOffset];
            DPReservedMask.Point point = payloadCells.get(traversalIndex);

            int cellValue = 0;
            for (int bit = 0; bit < densityMode.getBitsPerCell(); bit++) {
                cellValue <<= 1;
                if (bitIndex < totalBits) {
                    cellValue |= readBit(bytes, bitIndex);
                    bitIndex++;
                }
            }

            grid[point.y()][point.x()] = applyMaskToCellBits(cellValue, point.x(), point.y(), densityMode, maskId);
        }
    }

    private static int scoreMaskedPayloadGrid(int[][] maskedGrid, DPDensityMode densityMode) {
        int logicalSize = maskedGrid.length;
        int occupied = 0;
        int score = 0;
        int levels = densityMode.getLevelsPerChannel();
        int[] redCounts = new int[levels];
        int[] greenCounts = new int[levels];
        int[] blueCounts = new int[levels];
        int channelMask = levels - 1;
        int bitsPerChannel = densityMode.getBitsPerChannel();

        for (int y = 0; y < logicalSize; y++) {
            for (int x = 0; x < logicalSize; x++) {
                int value = maskedGrid[y][x];
                if (value < 0) {
                    continue;
                }
                occupied++;
                int blue = value & channelMask;
                int green = (value >>> bitsPerChannel) & channelMask;
                int red = (value >>> (bitsPerChannel * 2)) & channelMask;
                redCounts[red]++;
                greenCounts[green]++;
                blueCounts[blue]++;

                if (x > 0 && maskedGrid[y][x - 1] == value) {
                    score += 3;
                }
                if (y > 0 && maskedGrid[y - 1][x] == value) {
                    score += 3;
                }
            }
        }

        for (int y = 0; y < logicalSize; y++) {
            int runLength = 0;
            int previous = -2;
            for (int x = 0; x < logicalSize; x++) {
                int value = maskedGrid[y][x];
                if (value >= 0 && value == previous) {
                    runLength++;
                } else {
                    if (runLength >= 3) {
                        score += runLength * runLength;
                    }
                    runLength = (value >= 0) ? 1 : 0;
                    previous = value;
                }
            }
            if (runLength >= 3) {
                score += runLength * runLength;
            }
        }

        for (int x = 0; x < logicalSize; x++) {
            int runLength = 0;
            int previous = -2;
            for (int y = 0; y < logicalSize; y++) {
                int value = maskedGrid[y][x];
                if (value >= 0 && value == previous) {
                    runLength++;
                } else {
                    if (runLength >= 3) {
                        score += runLength * runLength;
                    }
                    runLength = (value >= 0) ? 1 : 0;
                    previous = value;
                }
            }
            if (runLength >= 3) {
                score += runLength * runLength;
            }
        }

        for (int y = 0; y < logicalSize - 1; y++) {
            for (int x = 0; x < logicalSize - 1; x++) {
                int topLeft = maskedGrid[y][x];
                if (topLeft < 0) {
                    continue;
                }
                if (maskedGrid[y][x + 1] == topLeft
                        && maskedGrid[y + 1][x] == topLeft
                        && maskedGrid[y + 1][x + 1] == topLeft) {
                    score += 8;
                }
            }
        }

        if (occupied == 0) {
            return score;
        }

        double expectedPerLevel = occupied / (double) levels;
        score += histogramPenalty(redCounts, expectedPerLevel);
        score += histogramPenalty(greenCounts, expectedPerLevel);
        score += histogramPenalty(blueCounts, expectedPerLevel);
        return score;
    }

    private static int histogramPenalty(int[] counts, double expectedPerLevel) {
        int penalty = 0;
        for (int count : counts) {
            penalty += (int) Math.round(Math.abs(count - expectedPerLevel));
        }
        return penalty;
    }

    private static int resolvePayloadMaskId(DPHeader header) {
        if (header == null) {
            throw new IllegalArgumentException("header cannot be null");
        }
        if (header.minorVersion() == 0 && header.maskId() == 0) {
            return -1;
        }
        return header.maskId();
    }

    private static int applyMaskToCellBits(int cellBits,
                                           int x,
                                           int y,
                                           DPDensityMode densityMode,
                                           int maskId) {
        if (maskId < 0) {
            return cellBits;
        }
        int maskBits = computeMaskBits(maskId, x, y, densityMode.getBitsPerCell());
        return cellBits ^ maskBits;
    }

    private static int computeMaskBits(int maskId, int x, int y, int bitsPerCell) {
        if (maskId < 0 || maskId >= MASK_COUNT) {
            throw new IllegalArgumentException("maskId out of range: " + maskId);
        }
        if (bitsPerCell <= 0 || bitsPerCell >= 31) {
            throw new IllegalArgumentException("bitsPerCell out of range: " + bitsPerCell);
        }

        int maxCellValue = (1 << bitsPerCell) - 1;
        long mix = switch (maskId) {
            case 0 -> (long) (x + 1) * 0x45D9F3BL ^ (long) (y + 1) * 0x27D4EB2DL;
            case 1 -> (long) (x + 1) * (y + 1) * 0x9E3779B1L;
            case 2 -> ((long) (x + 1) << 21) ^ ((long) (y + 1) * 0x7F4A7C15L);
            case 3 -> (long) ((x + 1) * (x + 3)) ^ (long) ((y + 1) * 0x632BE5ABL);
            case 4 -> (long) ((x + 1) + ((y + 1) * 3)) * 0x6C8E9CF5L;
            case 5 -> ((long) (x + 1) * 17L + (long) (y + 1) * 31L) * 0x165667B1L;
            case 6 -> ((long) ((x + 1) ^ ((y + 1) * 5))) * 0xD3A2646CL;
            case 7 -> ((long) ((x + 1) * 13) ^ (long) ((y + 1) * 29) ^ (long) ((x + 1) * (y + 1))) * 0x9E3779B9L;
            default -> throw new IllegalStateException("Unexpected maskId: " + maskId);
        };
        int folded = (int) (mix ^ (mix >>> 32));
        return Math.floorMod(folded, maxCellValue) + 1;
    }

    private static void validateLogicalGrid(Colors.Color[][] logicalGrid) {
        if (logicalGrid == null || logicalGrid.length == 0) {
            throw new IllegalArgumentException("logicalGrid cannot be null or empty");
        }

        int expectedWidth = logicalGrid[0].length;
        if (expectedWidth == 0) {
            throw new IllegalArgumentException("logicalGrid must contain at least one column");
        }

        for (int y = 0; y < logicalGrid.length; y++) {
            Colors.Color[] row = logicalGrid[y];
            if (row == null) {
                throw new IllegalArgumentException("logicalGrid row " + y + " cannot be null");
            }
            if (row.length != expectedWidth) {
                throw new IllegalArgumentException("logicalGrid must be rectangular");
            }
        }
        if (logicalGrid.length != expectedWidth) {
            throw new IllegalArgumentException("logicalGrid must be square");
        }
    }

    private static void validateFixedStructures(Colors.Color[][] logicalGrid, DPReservedMask mask) {
        int logicalSize = logicalGrid.length;
        for (int y = 0; y < logicalSize; y++) {
            for (int x = 0; x < logicalSize; x++) {
                switch (mask.getRole(x, y)) {
                    case TOP_LEFT_MARKER, BOTTOM_RIGHT_MARKER, CONTROL_BLACK, FLOATER_ARM_BLACK,
                            TOP_TIMING_BLACK, RIGHT_TIMING_BLACK -> {
                        if (!isExactColor(logicalGrid[y][x], BLACK)) {
                            throw new IllegalArgumentException("Expected black structural cell at (" + x + "," + y + ")");
                        }
                    }
                    case TOP_TIMING_WHITE, RIGHT_TIMING_WHITE, FLOATER_CENTER_WHITE -> {
                        if (!isExactColor(logicalGrid[y][x], WHITE)) {
                            throw new IllegalArgumentException("Expected white structural cell at (" + x + "," + y + ")");
                        }
                    }
                    case BOOTSTRAP, HEADER, PAYLOAD -> {
                        // These cells are validated by their dedicated decode paths.
                    }
                }
            }
        }
    }

    private static int[] readBootstrapBits(Colors.Color[][] logicalGrid, DPReservedMask mask) {
        int[] bits = new int[mask.getBootstrapCells().size()];
        int index = 0;
        for (DPReservedMask.Point point : mask.getBootstrapCells()) {
            Colors.Color color = logicalGrid[point.y()][point.x()];
            if (isExactColor(color, BLACK)) {
                bits[index++] = 1;
            } else if (isExactColor(color, WHITE)) {
                bits[index++] = 0;
            } else {
                throw new IllegalArgumentException(
                        "Bootstrap cell is neither black nor white at (" + point.x() + "," + point.y() + ")"
                );
            }
        }
        return bits;
    }

    private static void validateBootstrapParity(int[] bootstrapBits) {
        int expectedParity = bootstrapBits[0] ^ bootstrapBits[1] ^ bootstrapBits[2];
        if (bootstrapBits[3] != expectedParity) {
            throw new IllegalArgumentException(
                    "Bootstrap parity mismatch: expected " + expectedParity + ", found " + bootstrapBits[3]
            );
        }
    }

    private static Colors.Color[] readHeaderCells(Colors.Color[][] logicalGrid, DPReservedMask mask) {
        Colors.Color[] headerCells = new Colors.Color[mask.getHeaderCells().size()];
        int index = 0;
        for (DPReservedMask.Point point : mask.getHeaderCells()) {
            headerCells[index++] = logicalGrid[point.y()][point.x()];
        }
        return headerCells;
    }

    private static byte[] readPayloadBytes(Colors.Color[][] logicalGrid,
                                           DPReservedMask mask,
                                           DPDensityMode densityMode,
                                           int payloadLength,
                                           int[] dataTraversalIndices,
                                           int maskId) {
        return readBitstreamFromSlots(
                logicalGrid,
                mask.getPayloadCells(),
                densityMode,
                payloadLength,
                dataTraversalIndices,
                false,
                "Payload",
                maskId
        );
    }

    private static byte[] readEccBytes(Colors.Color[][] logicalGrid,
                                       DPReservedMask mask,
                                       DPDensityMode densityMode,
                                       int eccByteCount,
                                       int[] eccTraversalIndices,
                                       int maskId) {
        return readBitstreamFromSlots(
                logicalGrid,
                mask.getPayloadCells(),
                densityMode,
                eccByteCount,
                eccTraversalIndices,
                true,
                "ECC",
                maskId
        );
    }

    private static byte[] readBitstreamFromSlots(Colors.Color[][] logicalGrid,
                                                 java.util.List<DPReservedMask.Point> payloadCells,
                                                 DPDensityMode densityMode,
                                                 int byteCount,
                                                 int[] traversalIndices,
                                                 boolean reverseSlotOrder,
                                                 String label,
                                                 int maskId) {
        if (payloadCells == null) {
            throw new IllegalArgumentException("payloadCells cannot be null");
        }
        if (densityMode == null) {
            throw new IllegalArgumentException("densityMode cannot be null");
        }
        if (traversalIndices == null) {
            throw new IllegalArgumentException("traversalIndices cannot be null");
        }

        byte[] bytes = new byte[byteCount];
        int totalBits = byteCount * 8;
        int bitIndex = 0;

        for (int cellOffset = 0; cellOffset < traversalIndices.length; cellOffset++) {
            if (bitIndex >= totalBits) {
                break;
            }

            int traversalIndex = reverseSlotOrder
                    ? traversalIndices[traversalIndices.length - 1 - cellOffset]
                    : traversalIndices[cellOffset];
            if (traversalIndex < 0 || traversalIndex >= payloadCells.size()) {
                throw new IllegalArgumentException(label + " traversal index out of range: " + traversalIndex);
            }
            DPReservedMask.Point point = payloadCells.get(traversalIndex);
            int cellBits = densityMode.cellBitsFromColor(logicalGrid[point.y()][point.x()]);
            cellBits = applyMaskToCellBits(cellBits, point.x(), point.y(), densityMode, maskId);
            for (int bit = densityMode.getBitsPerCell() - 1; bit >= 0 && bitIndex < totalBits; bit--) {
                int bitValue = (cellBits >>> bit) & 1;
                writeBit(bytes, bitIndex, bitValue);
                bitIndex++;
            }
        }

        if (bitIndex != totalBits) {
            throw new IllegalArgumentException(label + " extraction underflow: " + bitIndex + " != " + totalBits);
        }
        return bytes;
    }

    private static void writeBit(byte[] payloadBytes, int bitIndex, int bitValue) {
        int byteIndex = bitIndex / 8;
        int bitOffset = 7 - (bitIndex % 8);
        if (bitValue != 0) {
            payloadBytes[byteIndex] |= (byte) (1 << bitOffset);
        }
    }

    private static boolean isExactColor(Colors.Color left, Colors.Color right) {
        return left != null
                && right != null
                && left.getR() == right.getR()
                && left.getG() == right.getG()
                && left.getB() == right.getB();
    }

    static record CellPlacementPlan(int[] dataTraversalIndices,
                                    int[] eccTraversalIndices) {}

    static record EccLayoutPlan(int blockCount,
                                int[] dataBlockLengths,
                                int[] parityBlockLengths,
                                int totalParityBytes,
                                int dataCellCount,
                                int eccCellCount) {}

    private static CellPlacementPlan buildCellPlacementPlan(int payloadCellCount,
                                                            int dataCellCount,
                                                            int eccCellCount) {
        if (payloadCellCount < 0 || dataCellCount < 0 || eccCellCount < 0) {
            throw new IllegalArgumentException("Cell placement counts cannot be negative");
        }
        if ((dataCellCount + eccCellCount) > payloadCellCount) {
            throw new IllegalArgumentException(
                    "Cell placement exceeds payload traversal capacity: payloadCells=" + payloadCellCount +
                            ", dataCells=" + dataCellCount +
                            ", eccCells=" + eccCellCount
            );
        }

        int[] eccTraversalIndices = subdivisionCenterIndices(payloadCellCount, eccCellCount);
        boolean[] eccSlot = new boolean[payloadCellCount];
        for (int traversalIndex : eccTraversalIndices) {
            if (traversalIndex < 0 || traversalIndex >= payloadCellCount) {
                throw new IllegalStateException("ECC traversal index out of range: " + traversalIndex);
            }
            if (eccSlot[traversalIndex]) {
                throw new IllegalStateException("Duplicate ECC traversal index: " + traversalIndex);
            }
            eccSlot[traversalIndex] = true;
        }

        int[] dataTraversalIndices = new int[dataCellCount];
        int dataWriteIndex = 0;
        for (int traversalIndex = 0; traversalIndex < payloadCellCount && dataWriteIndex < dataCellCount; traversalIndex++) {
            if (!eccSlot[traversalIndex]) {
                dataTraversalIndices[dataWriteIndex++] = traversalIndex;
            }
        }
        if (dataWriteIndex != dataCellCount) {
            throw new IllegalStateException(
                    "Unable to allocate enough payload data slots: allocated=" + dataWriteIndex +
                            ", required=" + dataCellCount
            );
        }

        return new CellPlacementPlan(dataTraversalIndices, eccTraversalIndices);
    }

    /**
     * Returns the center traversal index of each equal subdivision across the
     * full payload traversal span.
     *
     * <p>These indices define the encoder-side ECC slots. For example, 1 ECC
     * slot lands near the midpoint of the full payload traversal, 2 ECC slots
     * land near the quarter points, and 4 ECC slots land near the eighth,
     * three-eighths, five-eighths, and seven-eighths points.</p>
     */
    private static int[] subdivisionCenterIndices(int payloadCellCount, int slotCount) {
        if (slotCount < 0) {
            throw new IllegalArgumentException("slotCount cannot be negative");
        }
        if (slotCount == 0) {
            return new int[0];
        }
        if (payloadCellCount <= 0) {
            throw new IllegalArgumentException("payloadCellCount must be positive when slotCount > 0");
        }
        if (slotCount > payloadCellCount) {
            throw new IllegalArgumentException(
                    "slotCount cannot exceed payloadCellCount: " + slotCount + " > " + payloadCellCount
            );
        }

        int[] indices = new int[slotCount];
        for (int i = 0; i < slotCount; i++) {
            double center = ((((2.0 * i) + 1.0) * payloadCellCount) / (2.0 * slotCount)) - 0.5;
            indices[i] = Math.max(0, Math.min(payloadCellCount - 1, (int) Math.round(center)));
        }
        return indices;
    }

    private static int cellsRequired(int bitCount, int bitsPerCell) {
        if (bitCount < 0) {
            throw new IllegalArgumentException("bitCount cannot be negative");
        }
        if (bitsPerCell <= 0) {
            throw new IllegalArgumentException("bitsPerCell must be positive");
        }
        if (bitCount == 0) {
            return 0;
        }
        return (bitCount + bitsPerCell - 1) / bitsPerCell;
    }

    static EccLayoutPlan planEccLayout(int payloadLength,
                                       int payloadCellCount,
                                       DPDensityMode densityMode,
                                       EccProfile eccProfile) {
        if (payloadLength < 0) {
            throw new IllegalArgumentException("payloadLength cannot be negative");
        }
        if (densityMode == null) {
            throw new IllegalArgumentException("densityMode cannot be null");
        }
        if (eccProfile == null) {
            throw new IllegalArgumentException("eccProfile cannot be null");
        }

        int dataCellCount = cellsRequired(payloadLength * 8, densityMode.getBitsPerCell());
        int remainingCells = payloadCellCount - dataCellCount;
        if (remainingCells < 0) {
            throw new IllegalArgumentException(
                    "Payload exceeds payload traversal capacity: payloadLength=" + payloadLength +
                            ", payloadCells=" + payloadCellCount +
                            ", densityMode=" + densityMode
            );
        }
        if (payloadLength == 0) {
            return new EccLayoutPlan(0, new int[0], new int[0], 0, 0, 0);
        }
        if (remainingCells == 0) {
            int blockCount = minimumBlockCount(payloadLength, 0);
            return new EccLayoutPlan(
                    blockCount,
                    distributeLengths(payloadLength, blockCount),
                    new int[blockCount],
                    0,
                    dataCellCount,
                    0
            );
        }

        int maxParityBytesByTraversal = (remainingCells * densityMode.getBitsPerCell()) / 8;
        int targetParityBytes = Math.min(eccProfile.targetParityBytes(payloadLength), maxParityBytesByTraversal);
        int blockCount = minimumBlockCount(payloadLength, targetParityBytes);
        int[] dataBlockLengths = distributeLengths(payloadLength, blockCount);
        int[] parityBlockLengths = distributeLengths(targetParityBytes, blockCount);
        int eccCellCount = cellsRequired(targetParityBytes * 8, densityMode.getBitsPerCell());

        for (int i = 0; i < blockCount; i++) {
            if ((dataBlockLengths[i] + parityBlockLengths[i]) > 255) {
                throw new IllegalArgumentException(
                        "ECC block exceeds 255 bytes at block " + i +
                                ": data=" + dataBlockLengths[i] +
                                ", parity=" + parityBlockLengths[i]
                );
            }
        }

        return new EccLayoutPlan(
                blockCount,
                dataBlockLengths,
                parityBlockLengths,
                targetParityBytes,
                dataCellCount,
                eccCellCount
        );
    }

    private static int minimumBlockCount(int payloadLength, int totalParityBytes) {
        if (payloadLength < 0 || totalParityBytes < 0) {
            throw new IllegalArgumentException("Block planner lengths cannot be negative");
        }
        if (payloadLength == 0) {
            return 0;
        }

        int low = 1;
        int high = Math.max(1, payloadLength);
        while (low < high) {
            int mid = low + ((high - low) / 2);
            int maxDataLength = ceilDiv(payloadLength, mid);
            int maxParityLength = ceilDiv(totalParityBytes, mid);
            if ((maxDataLength + maxParityLength) <= 255) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }

    private static int[] distributeLengths(int total, int blocks) {
        if (total < 0 || blocks < 0) {
            throw new IllegalArgumentException("Distribution lengths cannot be negative");
        }
        if (blocks == 0) {
            return new int[0];
        }

        int[] lengths = new int[blocks];
        int baseLength = total / blocks;
        int extraCount = total % blocks;
        for (int i = 0; i < blocks; i++) {
            lengths[i] = baseLength + ((i < extraCount) ? 1 : 0);
        }
        return lengths;
    }

    private static int ceilDiv(int numerator, int denominator) {
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be positive");
        }
        if (numerator <= 0) {
            return 0;
        }
        return (numerator + denominator - 1) / denominator;
    }

    private static byte[] generateParityStream(byte[] payloadBytes, EccLayoutPlan plan) {
        if (payloadBytes == null) {
            throw new IllegalArgumentException("payloadBytes cannot be null");
        }
        if (plan == null) {
            throw new IllegalArgumentException("plan cannot be null");
        }
        if (plan.totalParityBytes() == 0) {
            return new byte[0];
        }

        byte[] parityBytes = new byte[plan.totalParityBytes()];
        int payloadOffset = 0;
        int parityOffset = 0;
        for (int block = 0; block < plan.blockCount(); block++) {
            int dataLength = plan.dataBlockLengths()[block];
            int parityLength = plan.parityBlockLengths()[block];
            byte[] dataBlock = new byte[dataLength];
            System.arraycopy(payloadBytes, payloadOffset, dataBlock, 0, dataLength);
            byte[] parityBlock = generateParityBytes(dataBlock, parityLength);
            System.arraycopy(parityBlock, 0, parityBytes, parityOffset, parityLength);
            payloadOffset += dataLength;
            parityOffset += parityLength;
        }
        return parityBytes;
    }

    private static byte[] generateParityBytes(byte[] dataBytes, int parityByteCount) {
        if (dataBytes == null) {
            throw new IllegalArgumentException("dataBytes cannot be null");
        }
        if (parityByteCount < 0) {
            throw new IllegalArgumentException("parityByteCount cannot be negative");
        }
        if (parityByteCount == 0) {
            return new byte[0];
        }
        if ((dataBytes.length + parityByteCount) > 255) {
            throw new IllegalArgumentException(
                    "Current ECC stage supports one RS block with at most 255 total bytes: dataBytes=" +
                            dataBytes.length + ", parityByteCount=" + parityByteCount
            );
        }

        int[] generator = buildGeneratorPolynomial(parityByteCount);
        int[] remainder = new int[parityByteCount];

        for (byte dataByte : dataBytes) {
            int factor = (dataByte & 0xFF) ^ remainder[0];
            for (int i = 0; i < parityByteCount - 1; i++) {
                remainder[i] = remainder[i + 1];
            }
            remainder[parityByteCount - 1] = 0;
            if (factor != 0) {
                for (int i = 0; i < parityByteCount; i++) {
                    remainder[i] ^= gfMultiply(generator[i], factor);
                }
            }
        }

        byte[] parityBytes = new byte[parityByteCount];
        for (int i = 0; i < parityByteCount; i++) {
            parityBytes[i] = (byte) remainder[i];
        }
        return parityBytes;
    }

    private static PayloadCorrectionResult correctCodewords(byte[] payloadBytes, byte[] eccBytes, EccLayoutPlan plan) {
        if (payloadBytes == null) {
            throw new IllegalArgumentException("payloadBytes cannot be null");
        }
        if (eccBytes == null) {
            throw new IllegalArgumentException("eccBytes cannot be null");
        }
        if (plan == null) {
            throw new IllegalArgumentException("plan cannot be null");
        }
        if (plan.blockCount() == 0 || plan.totalParityBytes() == 0) {
            return new PayloadCorrectionResult(payloadBytes.clone(), 0, true);
        }

        byte[] correctedPayload = new byte[payloadBytes.length];
        int totalErrors = 0;
        boolean payloadVerified = true;
        int payloadOffset = 0;
        int parityOffset = 0;
        for (int block = 0; block < plan.blockCount(); block++) {
            int dataLength = plan.dataBlockLengths()[block];
            int parityLength = plan.parityBlockLengths()[block];

            byte[] dataBlock = new byte[dataLength];
            byte[] parityBlock = new byte[parityLength];
            System.arraycopy(payloadBytes, payloadOffset, dataBlock, 0, dataLength);
            System.arraycopy(eccBytes, parityOffset, parityBlock, 0, parityLength);

            CodewordCorrectionResult correction = correctCodeword(dataBlock, parityBlock);
            System.arraycopy(correction.correctedCodeword(), 0, correctedPayload, payloadOffset, dataLength);
            totalErrors += correction.errorCount();
            if (correction.failureReason() != null) {
                payloadVerified = false;
            }
            if (correction.corrected() || !isZeroSyndrome(correction.initialSyndromes())) {
                logIssue("ECC activity block=" + block
                        + " payloadOffset=" + payloadOffset
                        + ", parityOffset=" + parityOffset
                        + ", dataLength=" + dataLength
                        + ", parityLength=" + parityLength
                        + ", initialSyndromes=" + buildSyndromeSummary(correction.initialSyndromes())
                        + ", corrected=" + correction.corrected()
                        + ", errorCount=" + correction.errorCount()
                        + ", positions=" + formatCorrectionPositions(
                                correction.errorPositions(),
                                dataLength,
                                payloadOffset,
                                parityOffset
                        )
                        + ", magnitudes=" + formatIntArray(correction.errorMagnitudes(), 32)
                        + ", rawDataPreview=" + formatBytePreview(dataBlock, 24)
                        + ", rawParityPreview=" + formatBytePreview(parityBlock, 24)
                        + ", correctedDataPreview="
                        + formatBytePreview(java.util.Arrays.copyOf(correction.correctedCodeword(), dataLength), 24)
                        + ", correctedSyndromes="
                        + buildSyndromeSummary(correction.correctedSyndromes())
                        + (correction.failureReason() == null
                        ? ""
                        : ", failureReason=" + correction.failureReason()));
            }

            payloadOffset += dataLength;
            parityOffset += parityLength;
        }
        return new PayloadCorrectionResult(correctedPayload, totalErrors, payloadVerified);
    }

    private static void validateParityBytes(byte[] payloadBytes, byte[] eccBytes) {
        if (payloadBytes == null) {
            throw new IllegalArgumentException("payloadBytes cannot be null");
        }
        if (eccBytes == null) {
            throw new IllegalArgumentException("eccBytes cannot be null");
        }

        byte[] expectedParity = generateParityBytes(payloadBytes, eccBytes.length);
        for (int i = 0; i < eccBytes.length; i++) {
            if ((eccBytes[i] & 0xFF) != (expectedParity[i] & 0xFF)) {
                throw new IllegalArgumentException(
                        "ECC parity mismatch at byte " + i +
                                ": stored=0x" + Integer.toHexString(eccBytes[i] & 0xFF).toUpperCase() +
                                ", computed=0x" + Integer.toHexString(expectedParity[i] & 0xFF).toUpperCase()
                );
            }
        }
    }

    private static int[] computeSyndromes(byte[] payloadBytes, byte[] eccBytes) {
        if (payloadBytes == null) {
            throw new IllegalArgumentException("payloadBytes cannot be null");
        }
        if (eccBytes == null) {
            throw new IllegalArgumentException("eccBytes cannot be null");
        }
        if (eccBytes.length == 0) {
            return new int[0];
        }

        byte[] codewordBlock = new byte[payloadBytes.length + eccBytes.length];
        System.arraycopy(payloadBytes, 0, codewordBlock, 0, payloadBytes.length);
        System.arraycopy(eccBytes, 0, codewordBlock, payloadBytes.length, eccBytes.length);

        int[] syndromes = new int[eccBytes.length];
        for (int syndromeIndex = 0; syndromeIndex < eccBytes.length; syndromeIndex++) {
            int evaluation = 0;
            int alpha = GF_EXP[syndromeIndex];
            for (byte codeword : codewordBlock) {
                evaluation = (codeword & 0xFF) ^ gfMultiply(evaluation, alpha);
            }
            syndromes[syndromeIndex] = evaluation;
        }
        return syndromes;
    }

    private static CodewordCorrectionResult correctCodeword(byte[] payloadBytes, byte[] eccBytes) {
        if (payloadBytes == null) {
            throw new IllegalArgumentException("payloadBytes cannot be null");
        }
        if (eccBytes == null) {
            throw new IllegalArgumentException("eccBytes cannot be null");
        }

        byte[] codewordBlock = new byte[payloadBytes.length + eccBytes.length];
        System.arraycopy(payloadBytes, 0, codewordBlock, 0, payloadBytes.length);
        System.arraycopy(eccBytes, 0, codewordBlock, payloadBytes.length, eccBytes.length);
        if (eccBytes.length == 0) {
            return new CodewordCorrectionResult(codewordBlock, new int[0], new int[0], new int[0], new int[0], false, 0, null);
        }

        int[] syndromes = computeSyndromes(payloadBytes, eccBytes);
        if (isZeroSyndrome(syndromes)) {
            return new CodewordCorrectionResult(codewordBlock, syndromes, syndromes.clone(), new int[0], new int[0], false, 0, null);
        }

        int[] errorLocator = buildErrorLocator(syndromes);
        logIssue("ECC solve start"
                + " dataLength=" + payloadBytes.length
                + ", parityLength=" + eccBytes.length
                + ", codewordLength=" + codewordBlock.length
                + ", initialSyndromes=" + buildSyndromeSummary(syndromes)
                + ", errorLocator=" + formatIntArray(errorLocator, 64)
                + ", rawCodewordPreview=" + formatBytePreview(codewordBlock, 48));
        int errorCount = errorLocator.length - 1;
        if (errorCount <= 0) {
            String failureReason = "ECC correction failed to derive an error locator";
            logIssue(failureReason + " syndromes=" + buildSyndromeSummary(syndromes));
            return new CodewordCorrectionResult(
                    codewordBlock,
                    syndromes,
                    syndromes.clone(),
                    new int[0],
                    new int[0],
                    false,
                    1,
                    failureReason
            );
        }
        if (errorCount > (eccBytes.length / 2)) {
            String failureReason =
                    "ECC correction exceeds correction capacity: errors=" + errorCount +
                            ", parityBytes=" + eccBytes.length;
            logIssue("ECC correction exceeds capacity"
                    + " syndromes=" + buildSyndromeSummary(syndromes)
                    + ", errorCount=" + errorCount
                    + ", parityBytes=" + eccBytes.length);
            return new CodewordCorrectionResult(
                    codewordBlock,
                    syndromes,
                    syndromes.clone(),
                    new int[0],
                    new int[0],
                    false,
                    errorCount,
                    failureReason
            );
        }

        try {
            int[] errorPositions = findErrorPositions(errorLocator, codewordBlock.length);
            int[] errorMagnitudes = computeErrorMagnitudes(syndromes, errorLocator, errorPositions, codewordBlock.length);
            byte[] correctedCodeword = codewordBlock.clone();
            logIssue("ECC solve positions"
                    + " errorCount=" + errorCount
                    + ", errorPositions=" + formatIntArray(errorPositions, 64)
                    + ", errorMagnitudes=" + formatIntArray(errorMagnitudes, 64));
            for (int i = 0; i < errorPositions.length; i++) {
                correctedCodeword[errorPositions[i]] ^= (byte) errorMagnitudes[i];
            }

            int[] correctedSyndromes = computeSyndromes(
                    java.util.Arrays.copyOfRange(correctedCodeword, 0, payloadBytes.length),
                    java.util.Arrays.copyOfRange(correctedCodeword, payloadBytes.length, correctedCodeword.length)
            );
            logIssue("ECC solve result"
                    + " correctedCodewordPreview=" + formatBytePreview(correctedCodeword, 48)
                    + ", correctedSyndromes=" + buildSyndromeSummary(correctedSyndromes));
            if (!isZeroSyndrome(correctedSyndromes)) {
                String failureReason = "ECC syndrome mismatch: " + buildSyndromeSummary(correctedSyndromes);
                logIssue("ECC corrected block still has non-zero syndromes "
                        + buildSyndromeSummary(correctedSyndromes));
                return new CodewordCorrectionResult(
                        codewordBlock,
                        syndromes,
                        correctedSyndromes,
                        errorPositions,
                        errorMagnitudes,
                        false,
                        Math.max(errorPositions.length, errorCount),
                        failureReason
                );
            }
            return new CodewordCorrectionResult(
                    correctedCodeword,
                    syndromes,
                    correctedSyndromes,
                    errorPositions,
                    errorMagnitudes,
                    true,
                    errorPositions.length,
                    null
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            logIssue("ECC correction exception"
                    + " errorCount=" + errorCount
                    + ", reason=" + exception.getMessage());
            return new CodewordCorrectionResult(
                    codewordBlock,
                    syndromes,
                    syndromes.clone(),
                    new int[0],
                    new int[0],
                    false,
                    errorCount,
                    exception.getMessage()
            );
        }
    }

    private static void validateSyndromes(int[] syndromes) {
        if (syndromes == null) {
            throw new IllegalArgumentException("syndromes cannot be null");
        }

        for (int syndrome : syndromes) {
            if (syndrome != 0) {
                logIssue("ECC corrected block still has non-zero syndromes "
                        + buildSyndromeSummary(syndromes));
                throw new IllegalArgumentException("ECC syndrome mismatch: " + buildSyndromeSummary(syndromes));
            }
        }
    }

    private static boolean isZeroSyndrome(int[] syndromes) {
        for (int syndrome : syndromes) {
            if (syndrome != 0) {
                return false;
            }
        }
        return true;
    }

    private static int[] buildErrorLocator(int[] syndromes) {
        if (syndromes == null) {
            throw new IllegalArgumentException("syndromes cannot be null");
        }
        if (syndromes.length == 0) {
            return new int[]{1};
        }

        int[] locator = new int[]{1};
        int[] previous = new int[]{1};
        int locatorDegree = 0;
        int previousShift = 1;
        int previousDiscrepancy = 1;

        for (int n = 0; n < syndromes.length; n++) {
            int discrepancy = syndromes[n];
            for (int i = 1; i <= locatorDegree; i++) {
                discrepancy ^= gfMultiply(locator[i], syndromes[n - i]);
            }

            if (discrepancy == 0) {
                previousShift++;
                continue;
            }

            int[] scaledPrevious = polyScaleAndShiftLow(
                    previous,
                    gfDivide(discrepancy, previousDiscrepancy),
                    previousShift
            );
            int[] updatedLocator = polyAddLow(locator, scaledPrevious);

            if ((2 * locatorDegree) <= n) {
                previous = locator;
                previousDiscrepancy = discrepancy;
                locatorDegree = n + 1 - locatorDegree;
                previousShift = 1;
            } else {
                previousShift++;
            }
            locator = updatedLocator;
        }

        locator = trimTrailingZerosLow(locator);
        if (locator.length == 0 || locator[0] == 0) {
            throw new IllegalArgumentException("Derived error locator polynomial is invalid");
        }
        return locator;
    }

    private static int[] findErrorPositions(int[] errorLocator, int codewordLength) {
        int errorCount = errorLocator.length - 1;
        int[] positions = new int[errorCount];
        int found = 0;

        for (int coefficientPosition = 0; coefficientPosition < codewordLength; coefficientPosition++) {
            int evaluationPoint = GF_EXP[(255 - coefficientPosition) % 255];
            if (evaluatePolynomialLow(errorLocator, evaluationPoint) == 0) {
                if (found >= positions.length) {
                    throw new IllegalArgumentException("Found more error positions than expected");
                }
                positions[found++] = codewordLength - 1 - coefficientPosition;
            }
        }

        if (found != errorCount) {
            throw new IllegalArgumentException(
                    "Unable to locate all error positions: expected=" + errorCount + ", found=" + found
            );
        }
        return positions;
    }

    private static int[] computeErrorMagnitudes(int[] syndromes,
                                                int[] errorLocator,
                                                int[] errorPositions,
                                                int codewordLength) {
        int[] errorEvaluator = trimToFirstTerms(polyMultiplyLow(syndromes, errorLocator), syndromes.length);
        int[] magnitudes = new int[errorPositions.length];
        int[] errorRoots = new int[errorPositions.length];

        for (int i = 0; i < errorPositions.length; i++) {
            int coefficientPosition = codewordLength - 1 - errorPositions[i];
            errorRoots[i] = GF_EXP[coefficientPosition % 255];
        }

        for (int i = 0; i < errorPositions.length; i++) {
            int root = errorRoots[i];
            int inverseRoot = gfInverse(root);
            int locatorDerivative = 1;
            for (int j = 0; j < errorRoots.length; j++) {
                if (i == j) {
                    continue;
                }
                locatorDerivative = gfMultiply(locatorDerivative, 1 ^ gfMultiply(inverseRoot, errorRoots[j]));
            }
            if (locatorDerivative == 0) {
                throw new IllegalArgumentException("ECC locator derivative is zero");
            }

            int numerator = evaluatePolynomialLow(errorEvaluator, inverseRoot);
            magnitudes[i] = gfDivide(numerator, locatorDerivative);
        }
        return magnitudes;
    }

    private static int[] polyAddLow(int[] left, int[] right) {
        int[] result = new int[Math.max(left.length, right.length)];
        for (int i = 0; i < result.length; i++) {
            int leftValue = i < left.length ? left[i] : 0;
            int rightValue = i < right.length ? right[i] : 0;
            result[i] = leftValue ^ rightValue;
        }
        return trimTrailingZerosLow(result);
    }

    private static int[] polyScaleAndShiftLow(int[] polynomial, int scalar, int shift) {
        if (polynomial == null) {
            throw new IllegalArgumentException("polynomial cannot be null");
        }
        if (shift < 0) {
            throw new IllegalArgumentException("shift cannot be negative");
        }
        if (scalar == 0) {
            return new int[]{0};
        }

        int[] result = new int[polynomial.length + shift];
        for (int i = 0; i < polynomial.length; i++) {
            result[i + shift] = gfMultiply(polynomial[i], scalar);
        }
        return trimTrailingZerosLow(result);
    }

    private static int[] polyMultiplyLow(int[] left, int[] right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("polynomials cannot be null");
        }
        int[] result = new int[left.length + right.length - 1];
        for (int i = 0; i < left.length; i++) {
            for (int j = 0; j < right.length; j++) {
                result[i + j] ^= gfMultiply(left[i], right[j]);
            }
        }
        return trimTrailingZerosLow(result);
    }

    private static int evaluatePolynomialLow(int[] polynomial, int x) {
        if (polynomial == null) {
            throw new IllegalArgumentException("polynomial cannot be null");
        }
        int value = 0;
        for (int i = polynomial.length - 1; i >= 0; i--) {
            value = gfMultiply(value, x) ^ polynomial[i];
        }
        return value;
    }

    private static int[] trimTrailingZerosLow(int[] polynomial) {
        if (polynomial == null) {
            throw new IllegalArgumentException("polynomial cannot be null");
        }
        int lastNonZero = polynomial.length - 1;
        while (lastNonZero > 0 && polynomial[lastNonZero] == 0) {
            lastNonZero--;
        }
        int[] result = new int[lastNonZero + 1];
        System.arraycopy(polynomial, 0, result, 0, result.length);
        return result;
    }

    private static int[] trimToFirstTerms(int[] polynomial, int count) {
        if (polynomial == null) {
            throw new IllegalArgumentException("polynomial cannot be null");
        }
        if (count <= 0) {
            return new int[]{0};
        }
        if (polynomial.length <= count) {
            return polynomial.clone();
        }

        int[] result = new int[count];
        System.arraycopy(polynomial, 0, result, 0, count);
        return trimTrailingZerosLow(result);
    }

    private static int[] buildGeneratorPolynomial(int degree) {
        if (degree <= 0) {
            throw new IllegalArgumentException("degree must be positive");
        }

        int[] polynomial = {1};
        for (int i = 0; i < degree; i++) {
            polynomial = multiplyPolynomials(polynomial, new int[]{1, GF_EXP[i]});
        }

        int[] generator = new int[degree];
        System.arraycopy(polynomial, 1, generator, 0, degree);
        return generator;
    }

    private static int[] multiplyPolynomials(int[] left, int[] right) {
        int[] result = new int[left.length + right.length - 1];
        for (int i = 0; i < left.length; i++) {
            for (int j = 0; j < right.length; j++) {
                result[i + j] ^= gfMultiply(left[i], right[j]);
            }
        }
        return result;
    }

    private static int gfMultiply(int left, int right) {
        if (left == 0 || right == 0) {
            return 0;
        }
        return GF_EXP[GF_LOG[left] + GF_LOG[right]];
    }

    private static int gfDivide(int numerator, int denominator) {
        if (denominator == 0) {
            throw new IllegalArgumentException("Cannot divide by zero in GF(256)");
        }
        if (numerator == 0) {
            return 0;
        }
        int index = GF_LOG[numerator] - GF_LOG[denominator];
        if (index < 0) {
            index += 255;
        }
        return GF_EXP[index];
    }

    private static int gfInverse(int value) {
        if (value == 0) {
            throw new IllegalArgumentException("Cannot invert zero in GF(256)");
        }
        return GF_EXP[255 - GF_LOG[value]];
    }

    private static int[] addPolynomials(int[] left, int[] right) {
        int[] result = new int[Math.max(left.length, right.length)];
        int leftOffset = result.length - left.length;
        int rightOffset = result.length - right.length;

        for (int i = 0; i < left.length; i++) {
            result[i + leftOffset] ^= left[i];
        }
        for (int i = 0; i < right.length; i++) {
            result[i + rightOffset] ^= right[i];
        }
        return trimLeadingZeros(result);
    }

    private static int[] scalePolynomial(int[] polynomial, int scalar) {
        if (scalar == 0) {
            return new int[]{0};
        }

        int[] result = new int[polynomial.length];
        for (int i = 0; i < polynomial.length; i++) {
            result[i] = gfMultiply(polynomial[i], scalar);
        }
        return result;
    }

    private static int[] appendTrailingZero(int[] polynomial) {
        int[] result = new int[polynomial.length + 1];
        System.arraycopy(polynomial, 0, result, 0, polynomial.length);
        return result;
    }

    private static int[] trimLeadingZeros(int[] polynomial) {
        int firstNonZero = 0;
        while (firstNonZero < polynomial.length && polynomial[firstNonZero] == 0) {
            firstNonZero++;
        }
        if (firstNonZero == polynomial.length) {
            return new int[]{0};
        }
        int[] result = new int[polynomial.length - firstNonZero];
        System.arraycopy(polynomial, firstNonZero, result, 0, result.length);
        return result;
    }

    private static int[] trimToLastTerms(int[] polynomial, int count) {
        if (count <= 0) {
            return new int[]{0};
        }
        if (polynomial.length <= count) {
            return polynomial.clone();
        }

        int[] result = new int[count];
        System.arraycopy(polynomial, polynomial.length - count, result, 0, count);
        return result;
    }

    private static int[] reverseArray(int[] values) {
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[values.length - 1 - i];
        }
        return result;
    }

    private static int evaluatePolynomial(int[] polynomial, int x) {
        int value = 0;
        for (int coefficient : polynomial) {
            value = gfMultiply(value, x) ^ coefficient;
        }
        return value;
    }

    private static String buildSyndromeSummary(int[] syndromes) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < syndromes.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("S").append(i + 1).append("=0x")
                    .append(Integer.toHexString(syndromes[i]).toUpperCase());
        }
        builder.append(']');
        return builder.toString();
    }

    private static void logIssue(String message) {
        if (ISSUE_DIAGNOSTICS) {
            System.out.println("[DPEncoder] " + message);
        }
    }

    private static void logHeaderCells(String label,
                                       java.util.List<DPReservedMask.Point> headerPoints,
                                       Colors.Color[] headerCells) {
        if (!HEADER_COLOR_DEBUG_LOGGING) {
            return;
        }
        if (headerPoints == null || headerCells == null) {
            throw new IllegalArgumentException("Header cell logging inputs cannot be null");
        }
        if (headerPoints.size() != headerCells.length) {
            throw new IllegalArgumentException(
                    "Header cell logging size mismatch: points=" + headerPoints.size() +
                            ", cells=" + headerCells.length
            );
        }

        System.out.println("[DPEncoder] " + label + " count=" + headerCells.length);
        for (int index = 0; index < headerCells.length; index++) {
            DPReservedMask.Point point = headerPoints.get(index);
            Colors.Color color = headerCells[index];
            System.out.println("[DPEncoder] header[" + index + "] @("
                    + point.x() + "," + point.y() + ")="
                    + formatColor(color));
        }
    }

    private static String formatColor(Colors.Color color) {
        if (color == null) {
            return "null";
        }
        return "(" + color.getR() + "," + color.getG() + "," + color.getB() + ")";
    }

    private static String formatBytePreview(byte[] bytes, int maxBytes) {
        if (bytes == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("len=").append(bytes.length).append(" [");
        int limit = Math.min(bytes.length, Math.max(0, maxBytes));
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", bytes[i] & 0xFF));
        }
        if (bytes.length > limit) {
            if (limit > 0) {
                builder.append(' ');
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

    private static String formatIntArrayReversed(int[] values, int maxValues) {
        if (values == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("len=").append(values.length).append(" [");
        int limit = Math.min(values.length, Math.max(0, maxValues));
        for (int offset = 0; offset < limit; offset++) {
            if (offset > 0) {
                builder.append(", ");
            }
            builder.append(values[values.length - 1 - offset]);
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

    private static String formatCorrectionPositions(int[] errorPositions,
                                                    int dataLength,
                                                    int payloadOffset,
                                                    int parityOffset) {
        if (errorPositions == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < errorPositions.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            int position = errorPositions[i];
            if (position < dataLength) {
                builder.append("payload[").append(payloadOffset + position).append(']');
            } else {
                builder.append("ecc[").append(parityOffset + (position - dataLength)).append(']');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private static String buildIndexedTraversalDump(String label,
                                                    java.util.List<DPReservedMask.Point> points,
                                                    int[] traversalIndices,
                                                    boolean reverseOrder) {
        if (points == null) {
            throw new IllegalArgumentException("points cannot be null");
        }
        if (traversalIndices == null) {
            throw new IllegalArgumentException("traversalIndices cannot be null");
        }

        StringBuilder builder = new StringBuilder();
        builder.append(label)
                .append(" (count=")
                .append(traversalIndices.length);
        if (reverseOrder) {
            builder.append(", writeOrder=reverse");
        }
        builder.append(')')
                .append(System.lineSeparator());

        for (int offset = 0; offset < traversalIndices.length; offset++) {
            int traversalIndex = reverseOrder
                    ? traversalIndices[traversalIndices.length - 1 - offset]
                    : traversalIndices[offset];
            if (traversalIndex < 0 || traversalIndex >= points.size()) {
                throw new IllegalArgumentException("Traversal index out of range: " + traversalIndex);
            }
            DPReservedMask.Point point = points.get(traversalIndex);
            builder.append(String.format("%04d -> (%d,%d)%n", traversalIndex, point.x(), point.y()));
        }
        return builder.toString();
    }

    private static String buildRoleMapDump(DPReservedMask mask) {
        StringBuilder builder = new StringBuilder();
        builder.append("Role map for N=").append(mask.getLogicalSize()).append(System.lineSeparator());
        builder.append("Legend: P=payload, A=top-left marker, Z=bottom-right marker, ")
                .append("w/W=top timing white/black, r/R=right timing white/black, ")
                .append("C=control black, B=bootstrap, H=header, F/f=floater arm black/center white")
                .append(System.lineSeparator());

        for (int y = 0; y < mask.getLogicalSize(); y++) {
            builder.append(String.format("%02d: ", y));
            for (int x = 0; x < mask.getLogicalSize(); x++) {
                builder.append(roleCode(mask.getRole(x, y)));
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String buildTraversalDump(String label, java.util.List<DPReservedMask.Point> points) {
        StringBuilder builder = new StringBuilder();
        builder.append(label)
                .append(" traversal")
                .append(" (count=")
                .append(points.size())
                .append(")")
                .append(System.lineSeparator());

        for (int i = 0; i < points.size(); i++) {
            DPReservedMask.Point point = points.get(i);
            builder.append(String.format("%04d -> (%d,%d)%n", i, point.x(), point.y()));
        }
        return builder.toString();
    }

    private static char roleCode(DPReservedMask.CellRole role) {
        return switch (role) {
            case PAYLOAD -> 'P';
            case TOP_LEFT_MARKER -> 'A';
            case BOTTOM_RIGHT_MARKER -> 'Z';
            case TOP_TIMING_WHITE -> 'w';
            case TOP_TIMING_BLACK -> 'W';
            case RIGHT_TIMING_WHITE -> 'r';
            case RIGHT_TIMING_BLACK -> 'R';
            case CONTROL_BLACK -> 'C';
            case BOOTSTRAP -> 'B';
            case HEADER -> 'H';
            case FLOATER_ARM_BLACK -> 'F';
            case FLOATER_CENTER_WHITE -> 'f';
        };
    }

    private record PayloadCorrectionResult(byte[] correctedPayload,
                                           int errors,
                                           boolean payloadVerified) {

        private byte[] copyCorrectedPayload() {
            return correctedPayload.clone();
        }
    }

    private record MaskSelectionResult(int maskId,
                                       int score) {}

    private record CodewordCorrectionResult(byte[] correctedCodeword,
                                            int[] initialSyndromes,
                                            int[] correctedSyndromes,
                                            int[] errorPositions,
                                            int[] errorMagnitudes,
                                            boolean corrected,
                                            int errorCount,
                                            String failureReason) {}
}
