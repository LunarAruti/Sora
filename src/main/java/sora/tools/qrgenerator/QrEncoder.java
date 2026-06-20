package sora.tools.qrgenerator;

import sora.tools.pixelgenerator.PixelArt;
import sora.tools.pixelgenerator.PixelGenerator;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Public QR generation entrypoint.
 *
 * <p>This class owns the full high-level QR flow used by the project:
 * validate caller input, choose a fitting QR version, build the final QR
 * codeword sequence, construct the masked matrix, convert the matrix into the
 * black/white token grid expected by the pixel generator, and finally render
 * the PNG to the exact caller-supplied output path.</p>
 *
 * <p>The public API intentionally stays small. Callers choose mode, error
 * correction, and text encoding here; QR sizing and quiet-zone rendering are
 * handled internally so callers do not need to manually size the final image.</p>
 */
public final class QrEncoder {

    /**
     * Fixed preferred QR cell size in output pixels.
     *
     * <p>This preserves the spirit of the previous QR PNG writer behavior:
     * QR modules render at a readable size without requiring the caller to
     * manually think about image dimensions.</p>
     */
    private static final int DEFAULT_MODULE_PIXEL_SIZE = 12;

    /**
     * Fixed quiet zone in QR modules.
     *
     * <p>The quiet zone is converted into a pixel border when the QR matrix is
     * handed off to the pixel generator.</p>
     */
    private static final int DEFAULT_QUIET_ZONE_MODULES = 4;

    /**
     * Token used for dark QR modules when rendered via the pixel generator.
     */
    private static final String DARK_MODULE_TOKEN = "b";

    /**
     * Token used for light QR modules and quiet zones when rendered via the
     * pixel generator.
     */
    private static final String LIGHT_MODULE_TOKEN = "w";

    /**
     * Supported QR content modes for this encoder increment.
     *
     * <p>Mode controls how the payload is validated and encoded:</p>
     * <ul>
     *     <li>{@link #NUMERIC}: digits {@code 0-9} only; most space-efficient
     *     when the payload is strictly numeric</li>
     *     <li>{@link #ALPHANUMERIC}: QR's limited base-45 character set,
     *     including uppercase letters, digits, space, and {@code $%*+-./:}</li>
     *     <li>{@link #BYTE}: arbitrary text bytes using the selected
     *     {@link TextEncoding}</li>
     * </ul>
     */
    public enum QrMode {
        NUMERIC,
        ALPHANUMERIC,
        BYTE
    }

    /**
     * Supported QR error-correction levels.
     *
     * <p>Higher correction levels provide more recovery from damage or noise
     * but reduce usable payload capacity for a given QR version.</p>
     *
     * <ul>
     *     <li>{@link #L}: lowest correction, highest capacity</li>
     *     <li>{@link #M}: medium correction</li>
     *     <li>{@link #Q}: high correction</li>
     *     <li>{@link #H}: highest correction, lowest capacity</li>
     * </ul>
     */
    public enum ErrorCorrectionLevel {
        L,
        M,
        Q,
        H
    }

    /**
     * Text encodings supported for byte-mode QR generation.
     *
     * <p>The selected encoding affects both payload byte generation and whether
     * an ECI header must be emitted in the QR data stream.</p>
     *
     * <p>Accepted values:</p>
     * <ul>
     *     <li>{@link #ISO_8859_1}: Latin-1 single-byte encoding. Use this when
     *     the payload is known to fit within ISO-8859-1. The encoder will
     *     throw if the source text contains characters this encoding cannot represent.</li>
     *     <li>{@link #UTF_8}: general Unicode-safe encoding for normal text,
     *     URLs, and non-ASCII content. This is the safer default for arbitrary
     *     modern text input.</li>
     * </ul>
     *
     * <p>This argument is primarily relevant when {@link QrMode#BYTE} is used.
     * Numeric and alphanumeric modes do not depend on character-set byte
     * encoding in the same way.</p>
     */
    public enum TextEncoding {
        ISO_8859_1(StandardCharsets.ISO_8859_1),
        UTF_8(StandardCharsets.UTF_8);

        private final Charset charset;

        TextEncoding(Charset charset) {
            this.charset = charset;
        }

        Charset charset() {
            return charset;
        }
    }

    private QrEncoder() {}

    /**
     * Generates a QR code image and returns the final rendered output path.
     *
     * <p>The QR matrix is still built internally, but the public API now
     * renders directly through the pixel generator instead of returning the
     * raw boolean module matrix.</p>
     *
     * <p>Argument expectations:</p>
     * <ul>
     *     <li>{@code rawText}: non-null source text to encode</li>
     *     <li>{@code mode}: must match the contents of {@code rawText}; the
     *     encoder throws if the content is invalid for the selected mode</li>
     *     <li>{@code ecLevel}: controls the balance between recovery strength
     *     and payload capacity</li>
     *     <li>{@code encoding}: the text character set to use for
     *     {@link QrMode#BYTE}. Accepted values are
     *     {@link TextEncoding#ISO_8859_1} and {@link TextEncoding#UTF_8}. Use
     *     UTF-8 for general modern text; use ISO-8859-1 only when the payload
     *     is known to fit that character set</li>
     *     <li>{@code outputPath}: exact final PNG file path</li>
     * </ul>
     *
     * <p>Current mode rules:</p>
     * <ul>
     *     <li>{@code NUMERIC} accepts digits only</li>
     *     <li>{@code ALPHANUMERIC} accepts only QR alphanumeric characters and
     *     expects uppercase letters for alphabetic content</li>
     *     <li>{@code BYTE} accepts general text and uses the selected
     *     {@link TextEncoding}</li>
     * </ul>
     *
     * @param rawText source text to encode
     * @param mode QR mode
     * @param ecLevel error correction level
     * @param encoding text encoding for byte mode; accepts {@link TextEncoding#ISO_8859_1}
     *                 or {@link TextEncoding#UTF_8}
     * @param outputPath exact PNG output path
     * @return final rendered PNG path
     */
    public static Path encode(String rawText,
                              QrMode mode,
                              ErrorCorrectionLevel ecLevel,
                              TextEncoding encoding,
                              String outputPath) {
        return encodeDebug(rawText, mode, ecLevel, encoding, outputPath).outputPath();
    }

    /**
     * Generates a QR code, renders it through the pixel generator, and returns
     * the debug details along with the rendered token matrix and final path.
     *
     * <p>This is the same generation path as {@link #encode(String, QrMode,
     * ErrorCorrectionLevel, TextEncoding, String)}, but it also exposes the
     * chosen version, selected mask, per-mask penalties, and payload/codeword
     * debug data for inspection.</p>
     *
     * @param rawText source text to encode
     * @param mode QR mode
     * @param ecLevel error correction level
     * @param encoding text encoding for byte mode; accepts {@link TextEncoding#ISO_8859_1}
     *                 or {@link TextEncoding#UTF_8}
     * @param outputPath exact PNG output path
     * @return debug details for the generated QR code and rendered image
     */
    public static DebugResult encodeDebug(String rawText,
                                          QrMode mode,
                                          ErrorCorrectionLevel ecLevel,
                                          TextEncoding encoding,
                                          String outputPath) {
        validateInput(rawText, mode, ecLevel, encoding);
        validateOutputPath(outputPath);

        int version = chooseVersion(rawText, mode, ecLevel, encoding);
        QrCodewords.Result result = QrCodewords.buildFinalSequence(rawText, mode, ecLevel, encoding, version);
        QrMatrix.MatrixBuild matrix = QrMatrix.buildMatrixDetails(result, ecLevel);
        String[][] tokenMatrix = toPixelTokenMatrix(matrix.modules());
        Path renderedPath = renderQrTokenMatrix(tokenMatrix, outputPath);

        return new DebugResult(
                tokenMatrix,
                renderedPath,
                version,
                matrix.chosenMask(),
                matrix.maskPenalties(),
                result.debug()
        );
    }

    private static void validateInput(String rawText,
                                      QrMode mode,
                                      ErrorCorrectionLevel ecLevel,
                                      TextEncoding encoding) {
        Objects.requireNonNull(rawText, "rawText");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(ecLevel, "ecLevel");
        Objects.requireNonNull(encoding, "encoding");

        validateModeContent(rawText, mode);

        if (mode == QrMode.BYTE) {
            validateEncodingCompatibility(rawText, encoding);
        }
    }

    /**
     * Validates the required final output path for QR rendering.
     *
     * @param outputPath exact target PNG path
     */
    private static void validateOutputPath(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("QR outputPath cannot be null or blank.");
        }
    }

    /**
     * Validates that the requested QR mode can represent the supplied content.
     *
     * <p>This method rejects invalid content before version-selection work
     * begins so the caller gets a direct mode/content error instead of a later
     * capacity or matrix-build failure.</p>
     *
     * @param rawText source text to validate
     * @param mode requested QR mode
     */
    private static void validateModeContent(String rawText, QrMode mode) {
        switch (mode) {
            case NUMERIC:
                for (int i = 0; i < rawText.length(); i++) {
                    char ch = rawText.charAt(i);
                    if (ch < '0' || ch > '9') {
                        throw new IllegalArgumentException("Numeric mode only accepts digits.");
                    }
                }
                break;

            case ALPHANUMERIC:
                for (int i = 0; i < rawText.length(); i++) {
                    if (QrSpec.getAlphanumericValue(rawText.charAt(i)) < 0) {
                        throw new IllegalArgumentException("Alphanumeric mode contains unsupported characters.");
                    }
                }
                break;

            case BYTE:
                break;

            default:
                throw new IllegalStateException("Unhandled mode: " + mode);
        }
    }

    /**
     * Validates encoding compatibility for byte-mode payloads.
     *
     * <p>ISO-8859-1 is stricter because not every Java string can be encoded
     * into that character set. UTF-8 is always accepted by this increment.</p>
     *
     * @param rawText source text
     * @param encoding requested byte encoding
     */
    private static void validateEncodingCompatibility(String rawText, TextEncoding encoding) {
        if (encoding != TextEncoding.ISO_8859_1) {
            return;
        }
        CharsetEncoder encoder = encoding.charset().newEncoder();
        if (!encoder.canEncode(rawText)) {
            throw new IllegalArgumentException("ISO-8859-1 cannot encode the provided text.");
        }
    }

    /**
     * Chooses the first QR version whose data capacity can hold the payload.
     *
     * <p>The search is bounded by both the QR spec maximum and the subset of
     * versions this codebase can currently render. Capacity checks are based on
     * the complete data bit count, including mode headers and any required ECI
     * data.</p>
     *
     * @param rawText source text
     * @param mode requested QR mode
     * @param ecLevel requested error-correction level
     * @param encoding requested text encoding
     * @return the smallest renderable fitting QR version
     */
    private static int chooseVersion(String rawText,
                                     QrMode mode,
                                     ErrorCorrectionLevel ecLevel,
                                     TextEncoding encoding) {
        int maxRenderableVersion = QrMatrix.maxRenderableVersion();
        for (int version = 1; version <= Math.min(QrSpec.MAX_SUPPORTED_VERSION, maxRenderableVersion); version++) {
            int requiredBits = QrCodewords.countRequiredDataBits(rawText, mode, encoding, version);
            int capacityBits = QrSpec.getDataCodewordCapacity(version, ecLevel) * 8;
            if (requiredBits <= capacityBits) {
                return version;
            }
        }
        throw new IllegalArgumentException("Input does not fit within the currently renderable QR versions.");
    }

    /**
     * Converts a QR module matrix into a black/white token grid suitable for
     * direct pixel-generator rendering.
     *
     * @param matrix QR module matrix where true means dark and false means light
     * @return token grid using fixed black/white palette tokens
     */
    private static String[][] toPixelTokenMatrix(boolean[][] matrix) {
        if (matrix == null || matrix.length == 0) {
            throw new IllegalArgumentException("QR matrix must not be null or empty.");
        }

        String[][] out = new String[matrix.length][];
        for (int row = 0; row < matrix.length; row++) {
            if (matrix[row] == null || matrix[row].length != matrix.length) {
                throw new IllegalArgumentException("QR matrix must be square and non-null.");
            }

            out[row] = new String[matrix[row].length];
            for (int col = 0; col < matrix[row].length; col++) {
                out[row][col] = matrix[row][col] ? DARK_MODULE_TOKEN : LIGHT_MODULE_TOKEN;
            }
        }
        return out;
    }

    /**
     * Renders the QR token matrix through the pixel generator using a fixed
     * per-module pixel size and a white quiet zone border.
     *
     * @param tokenMatrix black/white token matrix
     * @param outputPath exact PNG output path
     * @return final rendered PNG path
     */
    private static Path renderQrTokenMatrix(String[][] tokenMatrix, String outputPath) {
        int contentWidth = tokenMatrix[0].length * DEFAULT_MODULE_PIXEL_SIZE;
        int contentHeight = tokenMatrix.length * DEFAULT_MODULE_PIXEL_SIZE;
        int borderPixels = DEFAULT_QUIET_ZONE_MODULES * DEFAULT_MODULE_PIXEL_SIZE;

        PixelArt art = new PixelArt(
                tokenMatrix,
                contentWidth,
                contentHeight,
                outputPath,
                borderPixels,
                LIGHT_MODULE_TOKEN
        );
        return PixelGenerator.render(art);
    }

    /**
     * Debug snapshot of a generated QR render request.
     *
     * <p>The matrix exposed here is the black/white token grid handed to the
     * pixel generator rather than the earlier boolean module matrix. Mutable
     * array inputs are defensively copied so callers cannot accidentally mutate
     * the stored debug snapshot.</p>
     *
     * @param matrix rendered token matrix
     * @param outputPath final PNG output path
     * @param version chosen QR version
     * @param chosenMask selected mask pattern index
     * @param maskPenalties penalty scores for all mask candidates
     * @param codewords detailed codeword/debug data from the encoder pipeline
     */
    public record DebugResult(String[][] matrix,
                              Path outputPath,
                              int version,
                              int chosenMask,
                              int[] maskPenalties,
                              QrCodewords.DebugData codewords) {
        public DebugResult {
            String[][] matrixCopy = new String[matrix.length][];
            for (int row = 0; row < matrix.length; row++) {
                matrixCopy[row] = matrix[row].clone();
            }
            matrix = matrixCopy;
            maskPenalties = maskPenalties.clone();
        }
    }
}
