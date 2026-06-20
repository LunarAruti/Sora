package sora.tools.dpcode;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Public DP image reader facade.
 *
 * <p>This is the simple high-level decode entry point for exported DP PNG
 * files. It delegates to the internal image sampler and logical decoder, then
 * exposes the recovered payload plus the most useful header-derived metadata
 * through a stable result object.</p>
 */
public final class DPCodeReader {

    private static final boolean PAYLOAD_DEBUG_LOGGING = true;

    private DPCodeReader() {}

    /**
     * Decodes a DP image from a path string.
     *
     * @param imagePath path to the exported DP image; must not be null or blank
     * @return decoded DP image result
     * @throws IllegalArgumentException if imagePath is invalid or decode fails
     */
    public static ReadResult read(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("DPCodeReader: imagePath cannot be null or blank.");
        }
        return read(Path.of(imagePath));
    }

    /**
     * Decodes a DP image from a filesystem path.
     *
     * @param imagePath path to the exported DP image; must not be null
     * @return decoded DP image result
     * @throws IllegalArgumentException if imagePath is invalid or decode fails
     */
    public static ReadResult read(Path imagePath) {
        if (imagePath == null) {
            throw new IllegalArgumentException("DPCodeReader: imagePath cannot be null.");
        }

        DPEncoder.DecodedResult decoded = DPEncoder.decodeImage(imagePath);
        byte[] payloadBytes = decoded.copyPayloadBytes();
        logDecodedPayloadBytes(imagePath, payloadBytes);
        return new ReadResult(
                imagePath,
                payloadBytes,
                decoded.logicalSize(),
                DPCode.PayloadMode.RAW_BYTES,
                DPCode.PreprocessMode.NONE,
                DPCode.BootstrapProfile.fromId(decoded.bootstrapEcho() >>> 1),
                decoded.densityMode(),
                DPCode.EccProfile.fromInternal(decoded.eccProfile()),
                decoded.payloadLength(),
                decoded.payloadCrc(),
                decoded.errors(),
                decoded.isPayloadVerified(),
                decoded.bootstrapEcho(),
                decoded.roleMapDump(),
                decoded.bootstrapTraversalDump(),
                decoded.headerTraversalDump(),
                decoded.payloadTraversalDump(),
                decoded.payloadDataRegionDump(),
                decoded.payloadEccRegionDump()
        );
    }

    private static void logDecodedPayloadBytes(Path imagePath, byte[] payloadBytes) {
        if (!PAYLOAD_DEBUG_LOGGING) {
            return;
        }

        System.out.println("[DPCodeReader] decoded payload image="
                + imagePath
                + ", summary="
                + summarizeBytes(payloadBytes));
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
            throw new IllegalArgumentException("DPCodeReader: invalid hex range.");
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
            throw new IllegalStateException("DPCodeReader: unable to compute SHA-256 payload digest.", exception);
        }
    }

    /**
     * Public decode result for the DP image reader facade.
     *
     * <p>The payload bytes are stored defensively. Text access is optional and
     * caller-controlled through either UTF-8 convenience or a caller-supplied
     * charset.</p>
     */
    public static final class ReadResult {

        private final Path imagePath;
        private final byte[] payloadBytes;
        private final int logicalSize;
        private final DPCode.PayloadMode payloadMode;
        private final DPCode.PreprocessMode preprocessMode;
        private final DPCode.BootstrapProfile bootstrapProfile;
        private final DPDensityMode densityMode;
        private final DPCode.EccProfile eccProfile;
        private final int payloadLength;
        private final int payloadCrc;
        private final int errors;
        private final boolean payloadVerified;
        private final int bootstrapEcho;
        private final String roleMapDump;
        private final String bootstrapTraversalDump;
        private final String headerTraversalDump;
        private final String payloadTraversalDump;
        private final String payloadDataRegionDump;
        private final String payloadEccRegionDump;

        /**
         * Creates one immutable public decode result.
         *
         * @param imagePath path of the decoded image
         * @param payloadBytes decoded payload bytes
         * @param logicalSize decoded logical grid size
         * @param payloadMode decoded payload mode
         * @param preprocessMode decoded preprocessing mode
         * @param bootstrapProfile decoded bootstrap/header profile
         * @param densityMode decoded density mode
         * @param eccProfile decoded ECC profile
         * @param payloadLength decoded payload length in bytes
         * @param payloadCrc decoded payload CRC
         * @param errors number of payload-side decode issues encountered; zero means
         *               the payload path validated cleanly
         * @param payloadVerified true when the returned payload bytes are considered
         *                        trustworthy after ECC/CRC validation, false when the
         *                        payload is best-effort only
         * @param bootstrapEcho decoded bootstrap echo value
         * @param roleMapDump structural role dump
         * @param bootstrapTraversalDump bootstrap traversal dump
         * @param headerTraversalDump header traversal dump
         * @param payloadTraversalDump full payload traversal dump
         * @param payloadDataRegionDump forward data-region traversal dump
         * @param payloadEccRegionDump reverse ECC-region traversal dump
         */
        public ReadResult(Path imagePath,
                          byte[] payloadBytes,
                          int logicalSize,
                          DPCode.PayloadMode payloadMode,
                          DPCode.PreprocessMode preprocessMode,
                          DPCode.BootstrapProfile bootstrapProfile,
                          DPDensityMode densityMode,
                          DPCode.EccProfile eccProfile,
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
            if (imagePath == null) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: imagePath cannot be null.");
            }
            if (payloadBytes == null) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: payloadBytes cannot be null.");
            }
            if (payloadMode == null) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: payloadMode cannot be null.");
            }
            if (preprocessMode == null) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: preprocessMode cannot be null.");
            }
            if (bootstrapProfile == null) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: bootstrapProfile cannot be null.");
            }
            if (densityMode == null) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: densityMode cannot be null.");
            }
            if (eccProfile == null) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: eccProfile cannot be null.");
            }
            if (errors < 0) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: errors cannot be negative.");
            }
            if (roleMapDump == null
                    || bootstrapTraversalDump == null
                    || headerTraversalDump == null
                    || payloadTraversalDump == null
                    || payloadDataRegionDump == null
                    || payloadEccRegionDump == null) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: debug dumps cannot be null.");
            }

            this.imagePath = imagePath;
            this.payloadBytes = payloadBytes.clone();
            this.logicalSize = logicalSize;
            this.payloadMode = payloadMode;
            this.preprocessMode = preprocessMode;
            this.bootstrapProfile = bootstrapProfile;
            this.densityMode = densityMode;
            this.eccProfile = eccProfile;
            this.payloadLength = payloadLength;
            this.payloadCrc = payloadCrc;
            this.errors = errors;
            this.payloadVerified = payloadVerified;
            this.bootstrapEcho = bootstrapEcho;
            this.roleMapDump = roleMapDump;
            this.bootstrapTraversalDump = bootstrapTraversalDump;
            this.headerTraversalDump = headerTraversalDump;
            this.payloadTraversalDump = payloadTraversalDump;
            this.payloadDataRegionDump = payloadDataRegionDump;
            this.payloadEccRegionDump = payloadEccRegionDump;
        }

        /**
         * Returns the decoded image path.
         *
         * @return decoded image path
         */
        public Path getImagePath() {
            return imagePath;
        }

        /**
         * Returns a defensive copy of the decoded payload bytes.
         *
         * @return decoded payload bytes
         */
        public byte[] copyPayloadBytes() {
            return payloadBytes.clone();
        }

        /**
         * Decodes the payload bytes as UTF-8 text.
         *
         * @return payload interpreted as UTF-8 text
         */
        public String getPayloadAsUtf8String() {
            return getPayloadAsString(StandardCharsets.UTF_8);
        }

        /**
         * Decodes the payload bytes as text using the supplied charset.
         *
         * @param charset charset used to decode the payload bytes; must not be null
         * @return payload interpreted as text using the supplied charset
         * @throws IllegalArgumentException if charset is null
         */
        public String getPayloadAsString(Charset charset) {
            if (charset == null) {
                throw new IllegalArgumentException("DPCodeReader.ReadResult: charset cannot be null.");
            }
            return new String(payloadBytes, charset);
        }

        /**
         * Returns the decoded logical DP grid size.
         *
         * @return decoded logical DP grid size
         */
        public int getLogicalSize() {
            return logicalSize;
        }

        /**
         * Returns the decoded payload mode.
         *
         * @return decoded payload mode
         */
        public DPCode.PayloadMode getPayloadMode() {
            return payloadMode;
        }

        /**
         * Returns the decoded preprocessing mode.
         *
         * @return decoded preprocessing mode
         */
        public DPCode.PreprocessMode getPreprocessMode() {
            return preprocessMode;
        }

        /**
         * Returns the decoded bootstrap/header profile.
         *
         * @return decoded bootstrap/header profile
         */
        public DPCode.BootstrapProfile getBootstrapProfile() {
            return bootstrapProfile;
        }

        /**
         * Returns the decoded density mode.
         *
         * @return decoded density mode
         */
        public DPDensityMode getDensityMode() {
            return densityMode;
        }

        /**
         * Returns the decoded ECC profile.
         *
         * @return decoded ECC profile
         */
        public DPCode.EccProfile getEccProfile() {
            return eccProfile;
        }

        /**
         * Returns the decoded payload length from the header.
         *
         * @return decoded payload length in bytes
         */
        public int getPayloadLength() {
            return payloadLength;
        }

        /**
         * Returns the decoded payload CRC from the header.
         *
         * @return decoded payload CRC
         */
        public int getPayloadCrc() {
            return payloadCrc;
        }

        /**
         * Returns the number of payload-side decode issues encountered after
         * the header was read successfully.
         *
         * <p>A value of {@code 0} means the payload path validated cleanly.
         * Any non-zero value means the payload had to be repaired, failed a
         * repair attempt, or still failed its CRC after best-effort decode.</p>
         *
         * @return payload-side decode issue count
         */
        public int getErrors() {
            return errors;
        }

        /**
         * Returns whether the decoded payload bytes are considered verified.
         *
         * <p>A verified payload is one whose payload-side decode path ended in a
         * trustworthy state after ECC and CRC validation. A value of
         * {@code false} means the reader returned best-effort bytes after a
         * successful bootstrap/header decode, but payload integrity was not
         * fully proven.</p>
         *
         * @return true when the returned payload bytes are considered trustworthy
         */
        public boolean isPayloadVerified() {
            return payloadVerified;
        }

        /**
         * Returns the decoded 4-bit bootstrap echo value.
         *
         * @return decoded bootstrap echo
         */
        public int getBootstrapEcho() {
            return bootstrapEcho;
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
         * Returns the structural role-map debug dump.
         *
         * @return structural role-map debug dump
         */
        public String getRoleMapDump() {
            return roleMapDump;
        }

        /**
         * Returns the bootstrap traversal debug dump.
         *
         * @return bootstrap traversal debug dump
         */
        public String getBootstrapTraversalDump() {
            return bootstrapTraversalDump;
        }

        /**
         * Returns the header traversal debug dump.
         *
         * @return header traversal debug dump
         */
        public String getHeaderTraversalDump() {
            return headerTraversalDump;
        }

        /**
         * Returns the full payload traversal debug dump.
         *
         * @return full payload traversal debug dump
         */
        public String getPayloadTraversalDump() {
            return payloadTraversalDump;
        }

        /**
         * Returns the forward payload-data region dump.
         *
         * @return forward payload-data region dump
         */
        public String getPayloadDataRegionDump() {
            return payloadDataRegionDump;
        }

        /**
         * Returns the reverse ECC region dump.
         *
         * @return reverse ECC region dump
         */
        public String getPayloadEccRegionDump() {
            return payloadEccRegionDump;
        }

        /**
         * Returns a compact one-line summary of the decoded DP image.
         *
         * @return compact decoded-image summary
         */
        public String summary() {
            return "DPCodeReader.ReadResult{" +
                    "imagePath=" + imagePath +
                    ", logicalSize=" + logicalSize +
                    ", payloadMode=" + payloadMode +
                    ", preprocessMode=" + preprocessMode +
                    ", bootstrapProfile=" + bootstrapProfile +
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
}
