package sora.tools.dpcode;

import sora.tools.Colors;

import java.util.Arrays;

/**
 * Header and bootstrap packing helpers for the current DP encoder increment.
 *
 * <p>This class owns the deterministic bit packing for bootstrap metadata,
 * header metadata, and the CRC calculations used by those structures.</p>
 */
final class DPHeaderCodec {

    private static final DPDensityMode HEADER_DENSITY = DPDensityMode.D4;
    private static final int HEADER_CELL_COUNT = 16;
    private static final int HEADER_BITS = 96;
    private static final int CRC16_POLY = 0x1021;
    private static final int CRC16_INIT = 0xFFFF;

    private DPHeaderCodec() {}

    static int[] buildBootstrapBits(int profileId) {
        if (profileId < 0 || profileId > 0x7) {
            throw new IllegalArgumentException("bootstrap profileId out of range: " + profileId);
        }

        int[] bits = new int[4];
        bits[0] = (profileId >>> 2) & 1;
        bits[1] = (profileId >>> 1) & 1;
        bits[2] = profileId & 1;
        bits[3] = bits[0] ^ bits[1] ^ bits[2];
        return bits;
    }

    static DPHeader finalizeHeader(DPHeader header) {
        int[] bits = packBits(header);
        int headerCrc = crc16(bits, 0, 80);
        return header.withHeaderCrc(headerCrc);
    }

    static DPHeader decodeHeaderCells(Colors.Color[] headerCells) {
        if (headerCells == null) {
            throw new IllegalArgumentException("headerCells cannot be null");
        }
        if (headerCells.length != HEADER_CELL_COUNT) {
            throw new IllegalArgumentException("headerCells length must be " + HEADER_CELL_COUNT);
        }

        int[] bits = new int[HEADER_BITS];
        int bitIndex = 0;
        for (Colors.Color headerCell : headerCells) {
            int cellValue = HEADER_DENSITY.cellBitsFromColor(headerCell);
            for (int bit = HEADER_DENSITY.getBitsPerCell() - 1; bit >= 0; bit--) {
                bits[bitIndex++] = (cellValue >>> bit) & 1;
            }
        }

        DPHeader header = unpackBits(bits);
        int computedHeaderCrc = crc16(bits, 0, 80);
        if (computedHeaderCrc != header.headerCrc()) {
            throw new IllegalArgumentException(
                    "Header CRC mismatch: stored=0x" + Integer.toHexString(header.headerCrc()).toUpperCase() +
                            ", computed=0x" + Integer.toHexString(computedHeaderCrc).toUpperCase()
            );
        }
        return header;
    }

    static Colors.Color[] encodeHeaderCells(DPHeader header) {
        if (header.densityMode() == null) {
            throw new IllegalArgumentException("header densityMode cannot be null");
        }

        int[] bits = packBits(header);
        Colors.Color[] cells = new Colors.Color[HEADER_CELL_COUNT];
        for (int cell = 0; cell < HEADER_CELL_COUNT; cell++) {
            int value = 0;
            int start = cell * HEADER_DENSITY.getBitsPerCell();
            for (int i = 0; i < HEADER_DENSITY.getBitsPerCell(); i++) {
                value = (value << 1) | bits[start + i];
            }
            cells[cell] = HEADER_DENSITY.colorFromCellBits(value);
        }
        return cells;
    }

    static int computePayloadCrc(byte[] payloadBytes) {
        if (payloadBytes == null) {
            throw new IllegalArgumentException("payloadBytes cannot be null");
        }

        int crc = CRC16_INIT;
        for (byte payloadByte : payloadBytes) {
            crc ^= (payloadByte & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ CRC16_POLY) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }
        return crc & 0xFFFF;
    }

    static int[] packBits(DPHeader header) {
        BitBuffer buffer = new BitBuffer(HEADER_BITS);
        buffer.append(header.bootstrapEcho(), 4);
        buffer.append(header.minorVersion(), 4);
        buffer.append(header.sizeStep(), 12);
        buffer.append(header.densityMode().ordinal(), 3);
        buffer.append(header.payloadType(), 4);
        buffer.append(header.preprocessMode(), 4);
        buffer.append(header.eccProfile(), 2);
        buffer.append(header.maskId(), 3);
        buffer.append(header.flags(), 3);
        buffer.append(header.payloadLength(), 25);
        buffer.append(header.payloadCrc(), 16);
        buffer.append(header.headerCrc(), 16);
        return buffer.toArray();
    }

    private static DPHeader unpackBits(int[] bits) {
        BitReader reader = new BitReader(bits);
        int bootstrapEcho = reader.read(4);
        int minorVersion = reader.read(4);
        int sizeStep = reader.read(12);
        int densityModeOrdinal = reader.read(3);
        int payloadType = reader.read(4);
        int preprocessMode = reader.read(4);
        int eccProfile = reader.read(2);
        int maskId = reader.read(3);
        int flags = reader.read(3);
        int payloadLength = reader.read(25);
        int payloadCrc = reader.read(16);
        int headerCrc = reader.read(16);

        DPDensityMode[] densityModes = DPDensityMode.values();
        if (densityModeOrdinal < 0 || densityModeOrdinal >= densityModes.length) {
            throw new IllegalArgumentException("Invalid density mode ordinal in header: " + densityModeOrdinal);
        }

        return new DPHeader(
                bootstrapEcho,
                minorVersion,
                sizeStep,
                densityModes[densityModeOrdinal],
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

    private static int crc16(int[] bits, int start, int length) {
        if (bits == null) {
            throw new IllegalArgumentException("bits cannot be null");
        }
        if (start < 0 || length < 0 || start + length > bits.length) {
            throw new IllegalArgumentException("Invalid CRC bit range");
        }

        int crc = CRC16_INIT;
        for (int i = start; i < start + length; i++) {
            int bit = bits[i] & 1;
            int msb = ((crc >>> 15) & 1) ^ bit;
            crc = (crc << 1) & 0xFFFF;
            if (msb != 0) {
                crc ^= CRC16_POLY;
            }
        }
        return crc & 0xFFFF;
    }

    private static final class BitBuffer {
        private final int[] bits;
        private int index;

        private BitBuffer(int size) {
            this.bits = new int[size];
        }

        private void append(int value, int bitCount) {
            if (bitCount < 0 || bitCount > 31) {
                throw new IllegalArgumentException("Invalid bitCount: " + bitCount);
            }
            for (int bit = bitCount - 1; bit >= 0; bit--) {
                if (index >= bits.length) {
                    throw new IllegalStateException("Bit buffer overflow");
                }
                bits[index++] = (value >>> bit) & 1;
            }
        }

        private int[] toArray() {
            if (index != bits.length) {
                throw new IllegalStateException("Bit buffer size mismatch: " + index + " != " + bits.length);
            }
            return Arrays.copyOf(bits, bits.length);
        }
    }

    private static final class BitReader {
        private final int[] bits;
        private int index;

        private BitReader(int[] bits) {
            if (bits == null || bits.length != HEADER_BITS) {
                throw new IllegalArgumentException("bits must be length " + HEADER_BITS);
            }
            this.bits = bits;
        }

        private int read(int bitCount) {
            int value = 0;
            for (int i = 0; i < bitCount; i++) {
                value = (value << 1) | bits[index++];
            }
            return value;
        }
    }
}
