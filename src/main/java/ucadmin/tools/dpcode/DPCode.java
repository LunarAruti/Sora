package ucadmin.tools.dpcode;

import ucadmin.tools.Colors;
import ucadmin.tools.pixelgenerator.PixelColorArt;
import ucadmin.tools.pixelgenerator.PixelGenerator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Reusable public DP encode request object.
 *
 * <p>This is the main high-level encode surface for DP Code. The intended
 * usage mirrors the pixel generator request objects:</p>
 *
 * <ol>
 *     <li>Create one {@code DPCode} with a required payload</li>
 *     <li>Optionally adjust settings through setters</li>
 *     <li>Call {@link #encode()} repeatedly to reuse the same request object</li>
 * </ol>
 *
 * <p>The logical DP grid size is selected automatically. Encode always picks
 * the smallest legal logical size that can hold the current payload for the
 * current density and ECC settings. Callers therefore do not provide a
 * logical size directly in the public API.</p>
 *
 * <p>The exported image always includes the fixed 2-cell white quiet zone.
 * Callers provide an image size in pixels for the logical content area, and
 * this class converts that into an exact rendered cell size plus quiet-zone
 * border at encode time.</p>
 */
public final class DPCode {

    private static final boolean PAYLOAD_DEBUG_LOGGING = true;

    /**
     * Public payload source modes for the reusable request object.
     */
    public enum PayloadSource {
        BYTES,
        TEXT,
        FILE
    }

    /**
     * Public payload mode placeholder for future format expansion.
     *
     * <p>Only raw bytes are currently implemented in the DP internals.</p>
     */
    public enum PayloadMode {
        RAW_BYTES
    }

    /**
     * Public preprocessing mode placeholder for future format expansion.
     *
     * <p>No preprocessing transforms are currently implemented.</p>
     */
    public enum PreprocessMode {
        NONE
    }

    /**
     * Public ECC profiles for the reusable DP request object.
     *
     * <p>These map directly onto the current internal chunked ECC
     * profiles while keeping the public API independent from the lower-level
     * encoder class.</p>
     */
    public enum EccProfile {
        LOW(DPEncoder.EccProfile.LOW, 0),
        MEDIUM(DPEncoder.EccProfile.MEDIUM, 1),
        HIGH(DPEncoder.EccProfile.HIGH, 2),
        VERY_HIGH(DPEncoder.EccProfile.VERY_HIGH, 3);

        private final DPEncoder.EccProfile internalProfile;
        private final int headerValue;

        EccProfile(DPEncoder.EccProfile internalProfile, int headerValue) {
            this.internalProfile = internalProfile;
            this.headerValue = headerValue;
        }

        DPEncoder.EccProfile toInternal() {
            return internalProfile;
        }

        int targetParityBytes(int payloadLength) {
            return internalProfile.targetParityBytes(payloadLength);
        }

        /**
         * Returns the current 2-bit header value associated with this ECC profile.
         *
         * @return current 2-bit header value
         */
        public int getHeaderValue() {
            return headerValue;
        }

        static EccProfile fromInternal(DPEncoder.EccProfile internalProfile) {
            for (EccProfile profile : values()) {
                if (profile.internalProfile == internalProfile) {
                    return profile;
                }
            }
            throw new IllegalArgumentException("Unknown internal ECC profile: " + internalProfile);
        }
    }

    /**
     * Public bootstrap/header profiles for the reusable DP request object.
     *
     * <p>This profile is the monochrome bootstrap identifier and the header
     * interpretation selector. In other words, the bootstrap profile tells the
     * decoder what kind of header it should expect and how that header should
     * be interpreted once the bootstrap block has been read.</p>
     *
     * <p>Profile {@code id 0} is the current standard v1 format:
     * bootstrap selects the fixed upper-right 4x4 density-4 header layout and
     * the existing v1 header field interpretation.</p>
     */
    public enum BootstrapProfile {
        STANDARD_V1(0),
        RESERVED_1(1),
        RESERVED_2(2),
        RESERVED_3(3),
        RESERVED_4(4),
        RESERVED_5(5),
        RESERVED_6(6),
        RESERVED_7(7);

        private final int id;

        BootstrapProfile(int id) {
            this.id = id;
        }

        /**
         * Returns the raw 3-bit bootstrap profile identifier.
         *
         * @return raw bootstrap profile identifier in the range 0..7
         */
        public int getId() {
            return id;
        }

        static BootstrapProfile fromId(int id) {
            for (BootstrapProfile profile : values()) {
                if (profile.id == id) {
                    return profile;
                }
            }
            throw new IllegalArgumentException("Unknown bootstrap profile id: " + id);
        }
    }

    private static final int QUIET_ZONE_CELLS = 2;
    private static final int MIN_CELL_SIZE_PX = 5;
    private static final int MAX_AUTO_LOGICAL_SIZE = 1022;
    private static final Colors.Color QUIET_ZONE_COLOR = new Colors.Color(Colors.Preset.WHITE);

    private PayloadSource payloadSource;
    private byte[] payloadBytes;
    private String payloadText;
    private String payloadFilePath;
    private Charset payloadCharset;
    private PayloadMode payloadMode;
    private PreprocessMode preprocessMode;
    private DPDensityMode densityMode;
    private EccProfile eccProfile;
    private BootstrapProfile bootstrapProfile;
    private int imageSizePx;
    private String outputPath;

    /**
     * Creates a reusable DP encode request from raw bytes.
     *
     * <p>This constructor is intentionally minimal. Payload is the only
     * required value at construction time. The remaining configuration values
     * are optional and can be changed through setters before each encode.</p>
     *
     * @param payloadBytes raw payload bytes to encode; must not be null
     * @throws IllegalArgumentException if payloadBytes is null
     */
    public DPCode(byte[] payloadBytes) {
        setDefaultSettings();
        setPayloadBytes(payloadBytes);
    }

    /**
     * Creates a reusable DP encode request from source text.
     *
     * <p>The supplied text is converted into bytes using the current text
     * encoding setting. That setting defaults to UTF-8, so callers do not
     * need to configure it unless they intentionally want a different byte
     * encoding for text payloads.</p>
     *
     * @param payloadText source text to encode; must not be null
     * @throws IllegalArgumentException if payloadText is null
     */
    public DPCode(String payloadText) {
        setDefaultSettings();
        setPayloadText(payloadText);
    }

    /**
     * Creates a reusable DP encode request from a string payload that can be
     * interpreted either as source text or as a file path.
     *
     * <p>When {@code treatAsPath} is {@code false}, this behaves the same as
     * {@link #DPCode(String)} and stores source text. When {@code treatAsPath}
     * is {@code true}, the string is treated as a payload file path whose bytes
     * will be read at encode time.</p>
     *
     * @param payloadTextOrPath source text or payload file path; must not be null
     * @param treatAsPath whether the string should be treated as a payload file path
     * @throws IllegalArgumentException if the supplied text/path is invalid
     */
    public DPCode(String payloadTextOrPath, boolean treatAsPath) {
        setDefaultSettings();
        if (treatAsPath) {
            setPayloadFilePath(payloadTextOrPath);
        } else {
            setPayloadText(payloadTextOrPath);
        }
    }

    /**
     * Creates a reusable DP encode request from a payload file path.
     *
     * <p>This is the path-typed constructor counterpart to the byte and text
     * constructors. The file is read fresh on each encode, so the request can
     * be reused if the source file contents change.</p>
     *
     * @param payloadFilePath file path whose bytes should be encoded; must not be null
     * @throws IllegalArgumentException if payloadFilePath is null
     */
    public DPCode(Path payloadFilePath) {
        setDefaultSettings();
        setPayloadFilePath(payloadFilePath);
    }

    /**
     * Creates a reusable DP encode request from raw bytes.
     *
     * @param payloadBytes raw payload bytes to encode; must not be null
     * @return reusable DP encode request in raw-byte mode
     * @throws IllegalArgumentException if payloadBytes is null
     */
    public static DPCode fromBytes(byte[] payloadBytes) {
        return new DPCode(payloadBytes);
    }

    /**
     * Creates a reusable DP encode request from source text.
     *
     * @param payloadText source text to encode; must not be null
     * @return reusable DP encode request in text mode
     * @throws IllegalArgumentException if payloadText is null
     */
    public static DPCode fromText(String payloadText) {
        return new DPCode(payloadText);
    }

    /**
     * Creates a reusable DP encode request from a payload file path.
     *
     * @param payloadFilePath file path whose bytes should be encoded; must not be null or blank
     * @return reusable DP encode request in file payload mode
     * @throws IllegalArgumentException if payloadFilePath is null or blank
     */
    public static DPCode fromFilePath(String payloadFilePath) {
        return new DPCode(payloadFilePath, true);
    }

    /**
     * Creates a reusable DP encode request from a payload file path.
     *
     * @param payloadFilePath file path whose bytes should be encoded; must not be null
     * @return reusable DP encode request in file payload mode
     * @throws IllegalArgumentException if payloadFilePath is null
     */
    public static DPCode fromFilePath(Path payloadFilePath) {
        if (payloadFilePath == null) {
            throw new IllegalArgumentException("DPCode: payloadFilePath cannot be null.");
        }
        return new DPCode(payloadFilePath);
    }

    private void setDefaultSettings() {
        this.payloadCharset = StandardCharsets.UTF_8;
        this.payloadMode = PayloadMode.RAW_BYTES;
        this.preprocessMode = PreprocessMode.NONE;
        this.densityMode = DPDensityMode.D4;
        this.eccProfile = EccProfile.LOW;
        this.bootstrapProfile = BootstrapProfile.STANDARD_V1;
        this.imageSizePx = 512;
    }

    /**
     * Returns the active payload source mode.
     *
     * @return active payload source mode
     */
    public PayloadSource getPayloadSource() {
        return payloadSource;
    }

    /**
     * Replaces the payload with exact raw bytes.
     *
     * <p>This switches the request into {@link PayloadSource#BYTES} mode and
     * clears any previously configured text or file source.</p>
     *
     * @param payloadBytes raw payload bytes; must not be null
     * @throws IllegalArgumentException if payloadBytes is null
     */
    public void setPayloadBytes(byte[] payloadBytes) {
        if (payloadBytes == null) {
            throw new IllegalArgumentException("DPCode: payloadBytes cannot be null.");
        }
        this.payloadSource = PayloadSource.BYTES;
        this.payloadBytes = payloadBytes.clone();
        this.payloadText = null;
        this.payloadFilePath = null;
    }

    /**
     * Returns a defensive copy of the stored raw byte payload.
     *
     * <p>This only reflects the byte payload stored directly on the request
     * object. If text or file mode is active, use {@link #copyResolvedPayloadBytes()}
     * to inspect the actual bytes that would be encoded right now.</p>
     *
     * @return defensive copy of the stored raw byte payload
     */
    public byte[] copyPayloadBytes() {
        return (payloadBytes == null) ? new byte[0] : payloadBytes.clone();
    }

    /**
     * Returns a defensive copy of the payload bytes that would be encoded with
     * the current payload source settings.
     *
     * @return resolved encode-time payload bytes
     */
    public byte[] copyResolvedPayloadBytes() {
        return resolvePayloadBytes();
    }

    /**
     * Replaces the payload with source text.
     *
     * <p>This switches the request into {@link PayloadSource#TEXT} mode and
     * clears any previously configured file source.</p>
     *
     * @param payloadText source text to encode; must not be null
     * @throws IllegalArgumentException if payloadText is null
     */
    public void setPayloadText(String payloadText) {
        if (payloadText == null) {
            throw new IllegalArgumentException("DPCode: payloadText cannot be null.");
        }
        this.payloadSource = PayloadSource.TEXT;
        this.payloadText = payloadText;
        this.payloadFilePath = null;
    }

    /**
     * Returns the configured source text, or null when text mode is not active.
     *
     * @return configured source text or null
     */
    public String getPayloadText() {
        return payloadText;
    }

    /**
     * Switches the payload source to a file path.
     *
     * <p>The file is read fresh on every encode so the same request object can
     * be reused even if the source file contents change over time.</p>
     *
     * @param payloadFilePath file path whose bytes should be encoded; must not be null or blank
     * @throws IllegalArgumentException if payloadFilePath is null or blank
     */
    public void setPayloadFilePath(String payloadFilePath) {
        validatePathText("payloadFilePath", payloadFilePath);
        this.payloadSource = PayloadSource.FILE;
        this.payloadFilePath = payloadFilePath;
        this.payloadText = null;
    }

    /**
     * Switches the payload source to a file path.
     *
     * @param payloadFilePath file path whose bytes should be encoded; must not be null
     * @throws IllegalArgumentException if payloadFilePath is null
     */
    public void setPayloadFilePath(Path payloadFilePath) {
        if (payloadFilePath == null) {
            throw new IllegalArgumentException("DPCode: payloadFilePath cannot be null.");
        }
        setPayloadFilePath(payloadFilePath.toString());
    }

    /**
     * Returns the configured source file path, or null when file mode is not active.
     *
     * @return configured source file path or null
     */
    public String getPayloadFilePath() {
        return payloadFilePath;
    }

    /**
     * Returns the text encoding used when text payloads are converted into bytes.
     *
     * <p>This setting only matters when the payload source is text mode.
     * Byte payloads and file payloads ignore it.</p>
     *
     * @return text encoding used for text payload conversion
     */
    public Charset getPayloadCharset() {
        return payloadCharset;
    }

    /**
     * Updates the text encoding used when text payloads are converted into bytes.
     *
     * <p>This setting only affects {@link PayloadSource#TEXT}. It does not
     * affect raw-byte payloads or file payloads. The default is UTF-8, so most
     * callers should not need to change it.</p>
     *
     * @param payloadCharset text encoding used for text-to-byte conversion; must not be null
     * @throws IllegalArgumentException if payloadCharset is null
     */
    public void setPayloadCharset(Charset payloadCharset) {
        if (payloadCharset == null) {
            throw new IllegalArgumentException("DPCode: payloadCharset cannot be null.");
        }
        this.payloadCharset = payloadCharset;
    }

    /**
     * Returns the configured payload mode.
     *
     * <p>This is the byte-interpretation mode used by the DP format itself,
     * not the payload source mode used to obtain bytes. In the current public
     * implementation this stays at {@link PayloadMode#RAW_BYTES}.</p>
     *
     * @return configured payload mode
     */
    public PayloadMode getPayloadMode() {
        return payloadMode;
    }

    /**
     * Updates the payload mode.
     *
     * <p>Only {@link PayloadMode#RAW_BYTES} is currently implemented. The enum
     * exists now so the public API can stay stable when future payload modes
     * are added.</p>
     *
     * @param payloadMode payload mode; must not be null
     * @throws IllegalArgumentException if payloadMode is null
     */
    public void setPayloadMode(PayloadMode payloadMode) {
        if (payloadMode == null) {
            throw new IllegalArgumentException("DPCode: payloadMode cannot be null.");
        }
        this.payloadMode = payloadMode;
    }

    /**
     * Returns the configured preprocessing mode.
     *
     * <p>This is the DP payload preprocessing stage that would run after the
     * payload bytes are resolved but before they are packed into cells. The
     * current implementation only supports {@link PreprocessMode#NONE}.</p>
     *
     * @return configured preprocessing mode
     */
    public PreprocessMode getPreprocessMode() {
        return preprocessMode;
    }

    /**
     * Updates the preprocessing mode.
     *
     * <p>Only {@link PreprocessMode#NONE} is currently implemented.</p>
     *
     * @param preprocessMode preprocessing mode; must not be null
     * @throws IllegalArgumentException if preprocessMode is null
     */
    public void setPreprocessMode(PreprocessMode preprocessMode) {
        if (preprocessMode == null) {
            throw new IllegalArgumentException("DPCode: preprocessMode cannot be null.");
        }
        this.preprocessMode = preprocessMode;
    }

    /**
     * Returns the configured density mode.
     *
     * <p>This controls how many bits each RGB channel can carry. Higher
     * densities can store more data per cell but require tighter color
     * fidelity on decode.</p>
     *
     * @return configured density mode
     */
    public DPDensityMode getDensityMode() {
        return densityMode;
    }

    /**
     * Updates the density mode used for payload cells.
     *
     * <p>This changes the payload capacity per cell and the legal quantized
     * RGB levels used during encode/decode.</p>
     *
     * @param densityMode payload density mode; must not be null
     * @throws IllegalArgumentException if densityMode is null
     */
    public void setDensityMode(DPDensityMode densityMode) {
        if (densityMode == null) {
            throw new IllegalArgumentException("DPCode: densityMode cannot be null.");
        }
        this.densityMode = densityMode;
    }

    /**
     * Returns the configured ECC profile.
     *
     * <p>This controls how much Reed-Solomon parity is generated for the
     * payload. Stronger profiles reserve more space for correction and reduce
     * net payload capacity.</p>
     *
     * @return configured ECC profile
     */
    public EccProfile getEccProfile() {
        return eccProfile;
    }

    /**
     * Updates the ECC profile used during encode.
     *
     * <p>Stronger ECC improves recovery tolerance but increases overhead, so
     * the automatically selected logical size may grow when this value is
     * increased.</p>
     *
     * @param eccProfile ECC profile; must not be null
     * @throws IllegalArgumentException if eccProfile is null
     */
    public void setEccProfile(EccProfile eccProfile) {
        if (eccProfile == null) {
            throw new IllegalArgumentException("DPCode: eccProfile cannot be null.");
        }
        this.eccProfile = eccProfile;
    }

    /**
     * Returns the configured bootstrap/header profile.
     *
     * <p>This is the monochrome profile read first from the bottom-left
     * bootstrap box. It tells decode how the main header should be read.</p>
     *
     * @return configured bootstrap/header profile
     */
    public BootstrapProfile getBootstrapProfile() {
        return bootstrapProfile;
    }

    /**
     * Updates the bootstrap/header profile.
     *
     * <p>For the current implementation, {@link BootstrapProfile#STANDARD_V1}
     * means "read the fixed upper-right 4x4 density-4 v1 header layout".</p>
     *
     * @param bootstrapProfile bootstrap profile; must not be null
     * @throws IllegalArgumentException if bootstrapProfile is null
     */
    public void setBootstrapProfile(BootstrapProfile bootstrapProfile) {
        if (bootstrapProfile == null) {
            throw new IllegalArgumentException("DPCode: bootstrapProfile cannot be null.");
        }
        this.bootstrapProfile = bootstrapProfile;
    }

    /**
     * Returns the raw 3-bit bootstrap profile identifier.
     *
     * @return raw bootstrap profile identifier in the range 0..7
     */
    public int getBootstrapProfileId() {
        return bootstrapProfile.getId();
    }

    /**
     * Updates the bootstrap profile using a raw 3-bit identifier.
     *
     * @param bootstrapProfileId raw bootstrap profile identifier in the range 0..7
     * @throws IllegalArgumentException if bootstrapProfileId is outside 0..7
     */
    public void setBootstrapProfileId(int bootstrapProfileId) {
        this.bootstrapProfile = BootstrapProfile.fromId(bootstrapProfileId);
    }

    /**
     * Returns the requested logical-content image size in pixels.
     *
     * <p>This size applies to the logical DP symbol only. The exported image
     * becomes larger once the fixed quiet-zone border is added.</p>
     *
     * @return requested logical-content image size in pixels
     */
    public int getImageSizePx() {
        return imageSizePx;
    }

    /**
     * Updates the requested logical-content image size in pixels.
     *
     * <p>This request is rounded to the nearest size evenly divisible by the
     * auto-selected logical size. Encode also enforces a minimum rendered cell
     * size of 5x5 pixels.</p>
     *
     * @param imageSizePx requested logical-content image size in pixels; must be greater than zero
     * @throws IllegalArgumentException if imageSizePx is not positive
     */
    public void setImageSizePx(int imageSizePx) {
        if (imageSizePx <= 0) {
            throw new IllegalArgumentException("DPCode: imageSizePx must be > 0.");
        }
        this.imageSizePx = imageSizePx;
    }

    /**
     * Returns the fixed quiet-zone width in logical cells.
     *
     * @return quiet-zone width in logical cells
     */
    public int getQuietZoneCells() {
        return QUIET_ZONE_CELLS;
    }

    /**
     * Returns the configured output PNG path.
     *
     * @return configured output PNG path
     */
    public String getOutputPath() {
        return outputPath;
    }

    /**
     * Updates the configured output PNG path.
     *
     * @param outputPath exact target PNG file path; must not be null or blank
     * @throws IllegalArgumentException if outputPath is null or blank
     */
    public void setOutputPath(String outputPath) {
        validatePathText("outputPath", outputPath);
        this.outputPath = outputPath;
    }

    /**
     * Updates the configured output PNG path.
     *
     * @param outputPath exact target PNG file path; must not be null
     * @throws IllegalArgumentException if outputPath is null
     */
    public void setOutputPath(Path outputPath) {
        if (outputPath == null) {
            throw new IllegalArgumentException("DPCode: outputPath cannot be null.");
        }
        setOutputPath(outputPath.toString());
    }

    /**
     * Compiles the current request into a logical DP symbol without writing an image.
     *
     * <p>The logical size is chosen automatically. The compile result reflects
     * the smallest legal symbol that can carry the current payload under the
     * current density and ECC settings.</p>
     *
     * @return logical compile result for the current request state
     * @throws IllegalArgumentException if the current request state is invalid or no legal symbol size can carry it
     */
    public DPEncoder.Result compile() {
        byte[] resolvedPayloadBytes = resolvePayloadBytes();
        logResolvedPayloadBytes(resolvedPayloadBytes);
        validateImplementedOptions();
        int logicalSize = findSmallestLogicalSize(resolvedPayloadBytes);
        return DPEncoder.compileStructure(
                resolvedPayloadBytes,
                logicalSize,
                densityMode,
                bootstrapProfile.getId(),
                eccProfile.toInternal()
        );
    }

    /**
     * Encodes the current request into a PNG and returns the render metadata.
     *
     * <p>This method compiles the logical symbol, picks the smallest legal
     * grid size automatically, enforces a minimum rendered cell size of 5x5
     * pixels, adds the fixed 2-cell white quiet zone, and writes the final
     * image through the pixel generator.</p>
     *
     * @return encode result including the written path, resolved logical size, and logical compile details
     * @throws IllegalArgumentException if the request is invalid, the image size is too small, or rendering fails
     */
    public EncodeResult encode() {
        validatePathText("outputPath", outputPath);

        DPEncoder.Result compileResult = compile();
        int resolvedContentSize = resolveNearestDivisibleSize(imageSizePx, compileResult.logicalSize());
        int cellSizePx = resolvedContentSize / compileResult.logicalSize();
        if (cellSizePx < MIN_CELL_SIZE_PX) {
            throw new IllegalArgumentException(
                    "DPCode: imageSizePx is too small for the resolved logicalSize=" + compileResult.logicalSize() +
                            ". Minimum cell size is " + MIN_CELL_SIZE_PX + "px but resolved cellSizePx=" + cellSizePx + "."
            );
        }

        int quietZoneSizePx = QUIET_ZONE_CELLS * cellSizePx;
        PixelColorArt art = new PixelColorArt(
                compileResult.copyLogicalGrid(),
                resolvedContentSize,
                resolvedContentSize,
                outputPath,
                quietZoneSizePx,
                QUIET_ZONE_COLOR
        );

        Path renderedPath = PixelGenerator.render(art);
        return new EncodeResult(
                renderedPath,
                compileResult,
                payloadMode,
                preprocessMode,
                eccProfile,
                bootstrapProfile,
                resolvedContentSize,
                cellSizePx,
                quietZoneSizePx
        );
    }

    /**
     * Updates the output path and immediately encodes the current request.
     *
     * @param outputPath exact target PNG file path; must not be null or blank
     * @return encode result including the written path and resolved metadata
     * @throws IllegalArgumentException if outputPath is invalid or encode fails
     */
    public EncodeResult encodeTo(String outputPath) {
        setOutputPath(outputPath);
        return encode();
    }

    /**
     * Updates the output path and immediately encodes the current request.
     *
     * @param outputPath exact target PNG file path; must not be null
     * @return encode result including the written path and resolved metadata
     * @throws IllegalArgumentException if outputPath is invalid or encode fails
     */
    public EncodeResult encodeTo(Path outputPath) {
        setOutputPath(outputPath);
        return encode();
    }

    private void validateImplementedOptions() {
        if (payloadMode != PayloadMode.RAW_BYTES) {
            throw new IllegalArgumentException("DPCode: only PayloadMode.RAW_BYTES is currently implemented.");
        }
        if (preprocessMode != PreprocessMode.NONE) {
            throw new IllegalArgumentException("DPCode: only PreprocessMode.NONE is currently implemented.");
        }
    }

    private byte[] resolvePayloadBytes() {
        return switch (payloadSource) {
            case BYTES -> copyPayloadBytes();
            case TEXT -> payloadText.getBytes(payloadCharset);
            case FILE -> readPayloadFile(payloadFilePath);
        };
    }

    private int findSmallestLogicalSize(byte[] resolvedPayloadBytes) {
        for (int logicalSize = 10; logicalSize <= MAX_AUTO_LOGICAL_SIZE; logicalSize += 4) {
            if (canFitPayload(logicalSize, resolvedPayloadBytes.length)) {
                return logicalSize;
            }
        }

        throw new IllegalArgumentException(
                "DPCode: unable to find a legal logical size for payloadLength=" + resolvedPayloadBytes.length +
                        ", densityMode=" + densityMode +
                        ", eccProfile=" + eccProfile + "."
        );
    }

    private boolean canFitPayload(int logicalSize, int payloadLength) {
        try {
            DPReservedMask mask = DPReservedMask.create(logicalSize);
            DPEncoder.EccLayoutPlan plan = DPEncoder.planEccLayout(
                    payloadLength,
                    mask.getPayloadCells().size(),
                    densityMode,
                    eccProfile.toInternal()
            );
            return (plan.dataCellCount() + plan.eccCellCount()) <= mask.getPayloadCells().size();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] readPayloadFile(String payloadFilePath) {
        validatePathText("payloadFilePath", payloadFilePath);
        try {
            return Files.readAllBytes(Path.of(payloadFilePath));
        } catch (Exception exception) {
            throw new IllegalArgumentException("DPCode: unable to read payload file: " + payloadFilePath, exception);
        }
    }

    private void logResolvedPayloadBytes(byte[] resolvedPayloadBytes) {
        if (!PAYLOAD_DEBUG_LOGGING) {
            return;
        }

        String sourceDetail = switch (payloadSource) {
            case BYTES -> "BYTES";
            case TEXT -> "TEXT charset=" + payloadCharset;
            case FILE -> "FILE path=" + payloadFilePath;
        };

        System.out.println("[DPCode] encode payload source="
                + sourceDetail
                + ", summary="
                + summarizeBytes(resolvedPayloadBytes));
    }

    private static String summarizeBytes(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }

        return "len=" + bytes.length
                + ", sha256=" + sha256Hex(bytes)
                + ", head=" + hexRange(bytes, 0, Math.min(bytes.length, 64))
                + ", tail=" + hexRange(bytes, Math.max(0, bytes.length - 64), bytes.length);
    }

    private static String hexRange(byte[] bytes, int startInclusive, int endExclusive) {
        if (bytes == null) {
            return "null";
        }
        if (startInclusive < 0 || endExclusive < startInclusive || endExclusive > bytes.length) {
            throw new IllegalArgumentException("DPCode: invalid hex range.");
        }
        if (startInclusive == endExclusive) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = startInclusive; i < endExclusive; i++) {
            if (i > startInclusive) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", bytes[i] & 0xFF));
        }
        builder.append(']');
        return builder.toString();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02X", value & 0xFF));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("DPCode: unable to compute SHA-256 payload digest.", exception);
        }
    }

    private static void validatePathText(String fieldName, String pathText) {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalArgumentException("DPCode: " + fieldName + " cannot be null or blank.");
        }
    }

    private static int resolveNearestDivisibleSize(int preferred, int divisor) {
        if (preferred <= 0) {
            throw new IllegalArgumentException("DPCode: image size must be > 0.");
        }
        if (divisor <= 0) {
            throw new IllegalArgumentException("DPCode: divisor must be > 0.");
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
     * Public encode result for the reusable DP request object.
     *
     * @param outputPath normalized path of the written PNG file
     * @param compileResult logical compile result used for rendering
     * @param payloadMode payload mode used for this encode
     * @param preprocessMode preprocessing mode used for this encode
     * @param eccProfile public ECC profile used for this encode
     * @param bootstrapProfile public bootstrap profile used for this encode
     * @param contentSizePx resolved square logical-content image size in pixels
     * @param cellSizePx resolved cell size in pixels
     * @param quietZoneSizePx rendered quiet-zone thickness in pixels on each side
     */
    public record EncodeResult(Path outputPath,
                               DPEncoder.Result compileResult,
                               PayloadMode payloadMode,
                               PreprocessMode preprocessMode,
                               EccProfile eccProfile,
                               BootstrapProfile bootstrapProfile,
                               int contentSizePx,
                               int cellSizePx,
                               int quietZoneSizePx) {

        /**
         * Returns the auto-selected logical DP grid size used for this encode.
         *
         * @return resolved logical DP grid size
         */
        public int getResolvedLogicalSize() {
            return compileResult.logicalSize();
        }

        /**
         * Returns the raw 3-bit bootstrap profile identifier used for this encode.
         *
         * @return raw bootstrap profile identifier in the range 0..7
         */
        public int getBootstrapProfileId() {
            return bootstrapProfile.getId();
        }

        /**
         * Creates a concise summary of the encoded image output.
         *
         * @return one-line summary of the rendered DP image
         */
        public String summary() {
            return "DPCode.EncodeResult{" +
                    "outputPath=" + outputPath +
                    ", resolvedLogicalSize=" + compileResult.logicalSize() +
                    ", payloadMode=" + payloadMode +
                    ", preprocessMode=" + preprocessMode +
                    ", contentSizePx=" + contentSizePx +
                    ", cellSizePx=" + cellSizePx +
                    ", quietZoneSizePx=" + quietZoneSizePx +
                    ", densityMode=" + compileResult.densityMode() +
                    ", eccProfile=" + eccProfile +
                    ", bootstrapProfile=" + bootstrapProfile +
                    ", payloadLength=" + compileResult.payloadLength() +
                    '}';
        }
    }
}
