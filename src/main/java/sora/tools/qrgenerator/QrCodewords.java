package sora.tools.qrgenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Low-level QR data/codeword builder.
 *
 * <p>This class handles the payload side of QR generation after high-level
 * input validation has already happened in {@link QrEncoder}. Its job is to
 * convert text into the exact final stream of QR codewords and bits that the
 * matrix writer will later place into the symbol.</p>
 *
 * <p>The work is split into the standard QR stages: encode the payload
 * according to the selected mode, append headers and optional ECI data, pad
 * to data capacity, split into blocks, generate Reed-Solomon error-correction
 * codewords, interleave data and EC blocks, and append any version-specific
 * remainder bits.</p>
 */
public final class QrCodewords {

    /**
     * Exponent table for GF(256) arithmetic using the QR polynomial 0x11D.
     *
     * <p>The table is doubled in length so multiplication can index without
     * performing an extra modulo 255 operation.</p>
     */
    private static final int[] GF_EXP = new int[512];

    /**
     * Log table for GF(256) arithmetic using the QR polynomial 0x11D.
     */
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

    private QrCodewords() {}

    /**
     * Counts the number of data bits required to encode the supplied payload.
     *
     * <p>This includes mode indicators, character-count fields, and any ECI
     * header bits that are required for the chosen encoding. It does not
     * include pad bytes, error-correction codewords, or remainder bits.</p>
     *
     * @param rawText source text to encode
     * @param mode QR payload mode
     * @param encoding byte-mode text encoding
     * @param version version being evaluated
     * @return required pre-padding data bit count
     */
    public static int countRequiredDataBits(String rawText,
                                            QrEncoder.QrMode mode,
                                            QrEncoder.TextEncoding encoding,
                                            int version) {
        int bitCount = 0;

        if (shouldAppendEci(mode, encoding)) {
            bitCount += 4;
            bitCount += 8;
        }

        bitCount += 4;
        bitCount += QrSpec.getCharacterCountBits(version, mode);
        bitCount += getEncodedDataBitLength(rawText, mode, encoding);
        return bitCount;
    }

    /**
     * Builds the final QR codeword sequence and final bit stream for one
     * concrete version/error-correction combination.
     *
     * <p>The returned result is the handoff point between payload generation
     * and matrix construction. The boolean bit array is what
     * {@link QrMatrix} will physically place into writable QR data cells.</p>
     *
     * @param rawText source text to encode
     * @param mode QR payload mode
     * @param ecLevel error-correction level
     * @param encoding byte-mode text encoding
     * @param version already-chosen QR version
     * @return final codewords, final bit stream, and debug snapshots
     */
    public static Result buildFinalSequence(String rawText,
                                            QrEncoder.QrMode mode,
                                            QrEncoder.ErrorCorrectionLevel ecLevel,
                                            QrEncoder.TextEncoding encoding,
                                            int version) {
        BitBuffer dataBits = buildDataBitstream(rawText, mode, encoding, version);
        int masklessBitLength = dataBits.bitLength();

        int dataCapacityBits = QrSpec.getDataCodewordCapacity(version, ecLevel) * 8;
        padToDataCapacity(dataBits, dataCapacityBits);

        byte[] dataCodewords = dataBits.toByteArray();
        QrSpec.BlockSpec blockSpec = QrSpec.getBlockSpec(version, ecLevel);
        validateBlockSpec(version, ecLevel, blockSpec, dataCodewords.length);
        BlockBundle bundle = splitIntoBlocks(dataCodewords, blockSpec);

        byte[][] ecBlocks = new byte[bundle.dataBlocks.length][];
        for (int i = 0; i < bundle.dataBlocks.length; i++) {
            ecBlocks[i] = generateEcBytes(bundle.dataBlocks[i], blockSpec.ecCodewordsPerBlock());
        }

        byte[] finalCodewords = interleaveBlocks(bundle.dataBlocks, ecBlocks);

        BitBuffer finalBits = new BitBuffer();
        for (byte codeword : finalCodewords) {
            finalBits.appendByte(codeword & 0xFF);
        }
        int remainderBits = QrSpec.getRemainderBitCount(version);
        finalBits.appendZeroBits(remainderBits);

        validateFinalSequence(blockSpec, dataCodewords, ecBlocks, finalCodewords, finalBits.bitLength(), remainderBits);

        DebugData debug = new DebugData(
                copyBytes(dataCodewords),
                copyBlocks(bundle.dataBlocks),
                copyBlocks(ecBlocks),
                QrSpec.getDataCodewordCapacity(version, ecLevel),
                finalCodewords.length,
                remainderBits
        );

        return new Result(copyBytes(finalCodewords), finalBits.toBooleanArray(), version, masklessBitLength, debug);
    }

    /**
     * Builds the pre-padding payload bit stream.
     *
     * <p>This stage writes the optional ECI header, the mode indicator, the
     * version-specific character count field, and the actual mode-encoded
     * payload bits.</p>
     */
    private static BitBuffer buildDataBitstream(String rawText,
                                                QrEncoder.QrMode mode,
                                                QrEncoder.TextEncoding encoding,
                                                int version) {
        BitBuffer bits = new BitBuffer();
        appendEciIfNeeded(bits, mode, encoding);
        bits.appendBits(QrSpec.getModeIndicator(mode), 4);

        int characterCount = getCharacterCount(rawText, mode, encoding);
        bits.appendBits(characterCount, QrSpec.getCharacterCountBits(version, mode));

        switch (mode) {
            case NUMERIC -> appendNumericData(bits, rawText);
            case ALPHANUMERIC -> appendAlphanumericData(bits, rawText);
            case BYTE -> appendByteData(bits, rawText, encoding);
            default -> throw new IllegalStateException("Unhandled mode: " + mode);
        }
        return bits;
    }

    /**
     * Appends an ECI header when the selected byte encoding requires one.
     *
     * <p>In the current increment that means UTF-8 byte mode. ISO-8859-1 uses
     * the default QR interpretation and therefore does not require an ECI
     * designator.</p>
     */
    private static void appendEciIfNeeded(BitBuffer bits,
                                          QrEncoder.QrMode mode,
                                          QrEncoder.TextEncoding encoding) {
        if (!shouldAppendEci(mode, encoding)) {
            return;
        }

        int assignment = QrSpec.getEciAssignment(encoding);
        bits.appendBits(0x7, 4);
        if (assignment < 128) {
            bits.appendBits(assignment, 8);
            return;
        }
        throw new UnsupportedOperationException("Only 8-bit ECI assignment numbers are implemented in this increment.");
    }

    private static boolean shouldAppendEci(QrEncoder.QrMode mode, QrEncoder.TextEncoding encoding) {
        return mode == QrEncoder.QrMode.BYTE && encoding == QrEncoder.TextEncoding.UTF_8;
    }

    private static int getCharacterCount(String rawText,
                                         QrEncoder.QrMode mode,
                                         QrEncoder.TextEncoding encoding) {
        return switch (mode) {
            case NUMERIC, ALPHANUMERIC -> rawText.length();
            case BYTE -> encodeByteMode(rawText, encoding).length;
        };
    }

    private static int getEncodedDataBitLength(String rawText,
                                               QrEncoder.QrMode mode,
                                               QrEncoder.TextEncoding encoding) {
        return switch (mode) {
            case NUMERIC -> {
                int groupsOfThree = rawText.length() / 3;
                int remainder = rawText.length() % 3;
                int bits = groupsOfThree * 10;
                if (remainder == 1) {
                    bits += 4;
                } else if (remainder == 2) {
                    bits += 7;
                }
                yield bits;
            }
            case ALPHANUMERIC -> {
                int pairs = rawText.length() / 2;
                int bits = pairs * 11;
                if ((rawText.length() & 1) != 0) {
                    bits += 6;
                }
                yield bits;
            }
            case BYTE -> encodeByteMode(rawText, encoding).length * 8;
        };
    }

    /**
     * Appends numeric-mode payload chunks using the QR 3/2/1 digit grouping
     * rules and corresponding 10/7/4 bit widths.
     */
    private static void appendNumericData(BitBuffer bits, String rawText) {
        for (int index = 0; index < rawText.length(); index += 3) {
            int remaining = rawText.length() - index;
            int chunkLength = Math.min(3, remaining);
            int value = Integer.parseInt(rawText.substring(index, index + chunkLength));

            if (chunkLength == 3) {
                bits.appendBits(value, 10);
            } else if (chunkLength == 2) {
                bits.appendBits(value, 7);
            } else {
                bits.appendBits(value, 4);
            }
        }
    }

    /**
     * Appends alphanumeric-mode payload chunks using QR's base-45 pairing
     * rules.
     */
    private static void appendAlphanumericData(BitBuffer bits, String rawText) {
        int index = 0;
        while (index + 1 < rawText.length()) {
            int left = QrSpec.getAlphanumericValue(rawText.charAt(index));
            int right = QrSpec.getAlphanumericValue(rawText.charAt(index + 1));
            bits.appendBits((left * 45) + right, 11);
            index += 2;
        }

        if (index < rawText.length()) {
            bits.appendBits(QrSpec.getAlphanumericValue(rawText.charAt(index)), 6);
        }
    }

    /**
     * Appends raw byte-mode payload data using the selected text encoding.
     */
    private static void appendByteData(BitBuffer bits,
                                       String rawText,
                                       QrEncoder.TextEncoding encoding) {
        bits.appendBytes(encodeByteMode(rawText, encoding));
    }

    private static byte[] encodeByteMode(String rawText, QrEncoder.TextEncoding encoding) {
        return rawText.getBytes(encoding.charset());
    }

    /**
     * Pads the payload bitstream to the exact QR data capacity for the chosen
     * version and error-correction level.
     *
     * <p>The method follows the QR padding rules: terminator bits, byte
     * alignment, then alternating 0xEC / 0x11 pad bytes until capacity is
     * reached.</p>
     */
    private static void padToDataCapacity(BitBuffer bits, int dataCapacityBits) {
        if (bits.bitLength() > dataCapacityBits) {
            throw new IllegalArgumentException("Data bitstream exceeds data capacity.");
        }

        bits.appendZeroBits(Math.min(4, dataCapacityBits - bits.bitLength()));

        int remainderToByte = bits.bitLength() % 8;
        if (remainderToByte != 0) {
            bits.appendZeroBits(8 - remainderToByte);
        }

        int padByte = 0xEC;
        while (bits.bitLength() < dataCapacityBits) {
            bits.appendByte(padByte);
            padByte = (padByte == 0xEC) ? 0x11 : 0xEC;
        }
    }

    /**
     * Splits padded data codewords into the QR block layout defined by the
     * version/error-correction block spec.
     */
    private static BlockBundle splitIntoBlocks(byte[] dataCodewords, QrSpec.BlockSpec blockSpec) {
        int[] lengths = blockSpec.dataBlockLengths();
        byte[][] dataBlocks = new byte[lengths.length][];
        int offset = 0;

        for (int i = 0; i < lengths.length; i++) {
            int blockLength = lengths[i];
            dataBlocks[i] = Arrays.copyOfRange(dataCodewords, offset, offset + blockLength);
            offset += blockLength;
        }

        if (offset != dataCodewords.length) {
            throw new IllegalStateException("Block split did not consume all data codewords.");
        }

        return new BlockBundle(dataBlocks);
    }

    /**
     * Verifies that the selected block spec is internally consistent with the
     * expected QR capacities for the chosen version and correction level.
     */
    private static void validateBlockSpec(int version,
                                          QrEncoder.ErrorCorrectionLevel ecLevel,
                                          QrSpec.BlockSpec blockSpec,
                                          int dataCodewordCount) {
        int expectedDataCodewords = QrSpec.getDataCodewordCapacity(version, ecLevel);
        if (blockSpec.totalDataCodewords() != expectedDataCodewords) {
            throw new IllegalStateException("Block spec data total does not match QR capacity.");
        }
        if (dataCodewordCount != expectedDataCodewords) {
            throw new IllegalStateException("Padded data codeword count does not match QR capacity.");
        }

        int rawCodewords = QrSpec.getRawCodewordCount(version);
        int expectedTotalCodewords = expectedDataCodewords + (blockSpec.totalBlockCount() * blockSpec.ecCodewordsPerBlock());
        if (expectedTotalCodewords != rawCodewords) {
            throw new IllegalStateException("Block spec total codewords do not match raw QR capacity.");
        }
    }

    /**
     * Verifies that the final interleaved codeword stream matches the expected
     * data/EC/remainder-bit totals.
     */
    private static void validateFinalSequence(QrSpec.BlockSpec blockSpec,
                                              byte[] dataCodewords,
                                              byte[][] ecBlocks,
                                              byte[] finalCodewords,
                                              int finalBitLength,
                                              int remainderBits) {
        int ecCodewordCount = 0;
        for (byte[] ecBlock : ecBlocks) {
            if (ecBlock.length != blockSpec.ecCodewordsPerBlock()) {
                throw new IllegalStateException("EC block length does not match block spec.");
            }
            ecCodewordCount += ecBlock.length;
        }

        int expectedFinalCodewords = dataCodewords.length + ecCodewordCount;
        if (finalCodewords.length != expectedFinalCodewords) {
            throw new IllegalStateException("Final interleaved codeword count is inconsistent.");
        }

        int expectedFinalBits = (expectedFinalCodewords * 8) + remainderBits;
        if (finalBitLength != expectedFinalBits) {
            throw new IllegalStateException("Final bit length does not match codewords plus remainder bits.");
        }
    }

    /**
     * Interleaves data blocks first and error-correction blocks second,
     * matching the QR symbol layout rules.
     */
    private static byte[] interleaveBlocks(byte[][] dataBlocks, byte[][] ecBlocks) {
        List<Byte> out = new ArrayList<>();

        int maxDataLength = 0;
        for (byte[] block : dataBlocks) {
            maxDataLength = Math.max(maxDataLength, block.length);
        }
        for (int index = 0; index < maxDataLength; index++) {
            for (byte[] block : dataBlocks) {
                if (index < block.length) {
                    out.add(block[index]);
                }
            }
        }

        int maxEcLength = 0;
        for (byte[] block : ecBlocks) {
            maxEcLength = Math.max(maxEcLength, block.length);
        }
        for (int index = 0; index < maxEcLength; index++) {
            for (byte[] block : ecBlocks) {
                if (index < block.length) {
                    out.add(block[index]);
                }
            }
        }

        byte[] result = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    /**
     * Generates Reed-Solomon error-correction bytes for one data block.
     */
    private static byte[] generateEcBytes(byte[] dataBlock, int ecCount) {
        int[] divisor = buildGeneratorPolynomial(ecCount);
        int[] remainder = new int[ecCount];

        for (byte value : dataBlock) {
            int factor = (value & 0xFF) ^ remainder[0];

            if (ecCount - 1 >= 0) {
                System.arraycopy(remainder, 1, remainder, 0, ecCount - 1);
            }
            remainder[ecCount - 1] = 0;

            for (int i = 0; i < ecCount; i++) {
                remainder[i] ^= gfMultiply(divisor[i], factor);
            }
        }

        byte[] ecBytes = new byte[ecCount];
        for (int i = 0; i < ecCount; i++) {
            ecBytes[i] = (byte) remainder[i];
        }
        return ecBytes;
    }

    /**
     * Builds the generator polynomial for the requested EC degree in GF(256).
     */
    private static int[] buildGeneratorPolynomial(int degree) {
        int[] polynomial = {1};

        for (int i = 0; i < degree; i++) {
            polynomial = multiplyPolynomials(polynomial, new int[]{1, GF_EXP[i]});
        }

        return Arrays.copyOfRange(polynomial, 1, polynomial.length);
    }

    /**
     * Multiplies two GF(256) polynomials.
     */
    private static int[] multiplyPolynomials(int[] left, int[] right) {
        int[] out = new int[left.length + right.length - 1];
        for (int i = 0; i < left.length; i++) {
            for (int j = 0; j < right.length; j++) {
                out[i + j] ^= gfMultiply(left[i], right[j]);
            }
        }
        return out;
    }

    /**
     * Multiplies two GF(256) values using the precomputed log/exp tables.
     */
    private static int gfMultiply(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return GF_EXP[GF_LOG[a] + GF_LOG[b]];
    }

    private static byte[] copyBytes(byte[] input) {
        return Arrays.copyOf(input, input.length);
    }

    private static byte[][] copyBlocks(byte[][] input) {
        byte[][] copy = new byte[input.length][];
        for (int i = 0; i < input.length; i++) {
            copy[i] = copyBytes(input[i]);
        }
        return copy;
    }

    /**
     * Immutable payload-build result handed to the matrix layer.
     *
     * @param finalCodewords final interleaved data+EC codewords
     * @param finalBits final bit stream including remainder bits
     * @param version chosen QR version
     * @param masklessBitLength bit length before remainder bits were appended
     * @param debug additional debug snapshots from intermediate stages
     */
    public record Result(byte[] finalCodewords,
                         boolean[] finalBits,
                         int version,
                         int masklessBitLength,
                         DebugData debug) {
        public Result {
            finalCodewords = copyBytes(finalCodewords);
            finalBits = Arrays.copyOf(finalBits, finalBits.length);
        }
    }

    /**
     * Immutable debug snapshot of the payload/codeword pipeline.
     *
     * @param dataCodewords padded data codewords before EC generation
     * @param dataBlocks split data blocks
     * @param ecBlocks generated error-correction blocks
     * @param expectedDataCodewords QR data-codeword capacity
     * @param finalCodewordCount final interleaved codeword count
     * @param remainderBits remainder bit count appended after all codewords
     */
    public record DebugData(byte[] dataCodewords,
                            byte[][] dataBlocks,
                            byte[][] ecBlocks,
                            int expectedDataCodewords,
                            int finalCodewordCount,
                            int remainderBits) {
        public DebugData {
            dataCodewords = copyBytes(dataCodewords);
            dataBlocks = copyBlocks(dataBlocks);
            ecBlocks = copyBlocks(ecBlocks);
        }
    }

    private record BlockBundle(byte[][] dataBlocks) {}

    /**
     * Simple append-only bit accumulator used while constructing QR payloads.
     *
     * <p>This stays intentionally minimal because QR payload construction is
     * mostly sequential and write-only.</p>
     */
    private static final class BitBuffer {
        private final List<Boolean> bits = new ArrayList<>();

        void appendBit(boolean bit) {
            bits.add(bit);
        }

        void appendBits(int value, int bitCount) {
            if (bitCount < 0 || bitCount > 31) {
                throw new IllegalArgumentException("Invalid bit count: " + bitCount);
            }
            if (bitCount != 31 && value >>> bitCount != 0) {
                throw new IllegalArgumentException("Value does not fit in requested bit count.");
            }
            for (int i = bitCount - 1; i >= 0; i--) {
                appendBit(((value >>> i) & 1) != 0);
            }
        }

        void appendByte(int value) {
            appendBits(value & 0xFF, 8);
        }

        void appendBytes(byte[] values) {
            for (byte value : values) {
                appendByte(value & 0xFF);
            }
        }

        void appendZeroBits(int count) {
            for (int i = 0; i < count; i++) {
                appendBit(false);
            }
        }

        int bitLength() {
            return bits.size();
        }

        byte[] toByteArray() {
            if (bits.size() % 8 != 0) {
                throw new IllegalStateException("Bit buffer length must be byte-aligned.");
            }

            byte[] out = new byte[bits.size() / 8];
            for (int i = 0; i < bits.size(); i++) {
                if (bits.get(i)) {
                    out[i / 8] |= (byte) (1 << (7 - (i % 8)));
                }
            }
            return out;
        }

        boolean[] toBooleanArray() {
            boolean[] out = new boolean[bits.size()];
            for (int i = 0; i < bits.size(); i++) {
                out[i] = bits.get(i);
            }
            return out;
        }
    }
}
