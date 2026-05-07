package ucadmin.tools.dpcode;

/**
 * Immutable DP header metadata for the current v1 encoder increment.
 *
 * <p>This record stores the frozen field set for the 96-bit header. The
 * header codec is responsible for validating ranges, packing the fields into
 * bits, and computing the final header CRC.</p>
 */
record DPHeader(int bootstrapEcho,
                int minorVersion,
                int sizeStep,
                DPDensityMode densityMode,
                int payloadType,
                int preprocessMode,
                int eccProfile,
                int maskId,
                int flags,
                int payloadLength,
                int payloadCrc,
                int headerCrc) {

    static DPHeader createV1(int logicalSize,
                             DPDensityMode densityMode,
                             int bootstrapEcho,
                             int eccProfile,
                             int payloadLength,
                             int payloadCrc) {
        if (densityMode == null) {
            throw new IllegalArgumentException("densityMode cannot be null");
        }
        validateLogicalSize(logicalSize);
        validateRange("bootstrapEcho", bootstrapEcho, 0xF);
        validateRange("eccProfile", eccProfile, 0x3);
        validateRange("payloadLength", payloadLength, 0x1FFFFFF);
        validateRange("payloadCrc", payloadCrc, 0xFFFF);

        return new DPHeader(
                bootstrapEcho,
                0,
                (logicalSize - 10) / 4,
                densityMode,
                0,
                0,
                eccProfile,
                0,
                0,
                payloadLength,
                payloadCrc,
                0
        );
    }

    DPHeader withHeaderCrc(int headerCrc) {
        validateRange("headerCrc", headerCrc, 0xFFFF);
        return new DPHeader(
                bootstrapEcho,
                minorVersion,
                sizeStep,
                densityMode,
                payloadType,
                preprocessMode,
                eccProfile,
                maskId,
                flags,
                payloadLength,
                payloadCrc,
                headerCrc
        );
    }

    DPHeader withMinorVersion(int minorVersion) {
        validateRange("minorVersion", minorVersion, 0xF);
        return new DPHeader(
                bootstrapEcho,
                minorVersion,
                sizeStep,
                densityMode,
                payloadType,
                preprocessMode,
                eccProfile,
                maskId,
                flags,
                payloadLength,
                payloadCrc,
                headerCrc
        );
    }

    DPHeader withMaskId(int maskId) {
        validateRange("maskId", maskId, 0x7);
        return new DPHeader(
                bootstrapEcho,
                minorVersion,
                sizeStep,
                densityMode,
                payloadType,
                preprocessMode,
                eccProfile,
                maskId,
                flags,
                payloadLength,
                payloadCrc,
                headerCrc
        );
    }

    int logicalSize() {
        return 10 + (sizeStep * 4);
    }

    private static void validateLogicalSize(int logicalSize) {
        if (logicalSize < 10 || ((logicalSize - 10) % 4) != 0) {
            throw new IllegalArgumentException("Illegal DP logical size: " + logicalSize);
        }
    }

    private static void validateRange(String name, int value, int max) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(name + " out of range: " + value);
        }
    }
}
