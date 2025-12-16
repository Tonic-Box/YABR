package com.tonic.parser;

import com.tonic.exception.IncorrectFormatException;
import lombok.Getter;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * An abstract parser for reading various data types from a byte array.
 * Supports both signed and unsigned read operations.
 */
@Getter
public abstract class AbstractParser {
    private byte[] bytes;
    private int index;

    /**
     * Parses a class file byte array with verification.
     *
     * @param bytes the byte array representing the class file
     * @throws IllegalArgumentException if the byte array is null
     * @throws IncorrectFormatException if the byte array fails verification
     */
    protected AbstractParser(byte[] bytes) {
        this(bytes, true);
    }

    /**
     * Initializes parser with optional processing.
     *
     * @param bytes the byte array to parse or build
     * @param parse whether to parse the byte array
     * @throws IllegalArgumentException if the byte array is null
     * @throws IncorrectFormatException if the byte array fails verification when parsing
     */
    protected AbstractParser(byte[] bytes, boolean parse) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null.");
        }
        this.bytes = bytes.clone();
        this.index = 0;
        if (parse && !verify()) {
            throw new IncorrectFormatException();
        }
        if (parse) {
            process();
        }
    }

    /**
     * Gets the byte array length.
     *
     * @return length of the byte array
     */
    public final int getLength() {
        return bytes.length;
    }

    /**
     * Processes the parsed byte array.
     */
    protected abstract void process();

    /**
     * Verifies byte array format integrity.
     *
     * @return true if valid, false otherwise
     */
    protected boolean verify() {
        return true;
    }

    /**
     * Rebuilds byte array from current state.
     *
     * @throws IOException if writing fails
     * @throws IncorrectFormatException if rebuilt array fails verification
     */
    public final void rebuild() throws IOException {
        bytes = write();
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null.");
        }
        this.index = 0;
        if (!verify()) {
            throw new IncorrectFormatException();
        }
        process();
    }

    /**
     * Writes current state to byte array.
     *
     * @return the byte array representation
     * @throws IOException if writing fails
     */
    protected abstract byte[] write() throws IOException;

    /**
     * Reads a signed byte.
     *
     * @return signed byte value
     * @throws IndexOutOfBoundsException if out of bounds
     */
    public final byte readByte() {
        validateIndex(index, 1);
        return bytes[index++];
    }

    /**
     * Reads an unsigned byte (0 to 255).
     *
     * @return unsigned byte as int
     * @throws IndexOutOfBoundsException if out of bounds
     */
    public final int readUnsignedByte() {
        validateIndex(index, 1);
        return bytes[index++] & 0xFF;
    }

    /**
     * Reads a signed short in big-endian order.
     *
     * @return signed short value
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final short readShort() {
        validateIndex(index, 2);
        int high = bytes[index++] & 0xFF;
        int low = bytes[index++] & 0xFF;
        return (short) ((high << 8) | low);
    }

    /**
     * Reads an unsigned short (0 to 65,535) in big-endian order.
     *
     * @return unsigned short as int
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final int readUnsignedShort() {
        validateIndex(index, 2);
        int high = bytes[index++] & 0xFF;
        int low = bytes[index++] & 0xFF;
        return (high << 8) | low;
    }

    /**
     * Reads a signed int in big-endian order.
     *
     * @return signed int value
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final int readInt() {
        validateIndex(index, 4);
        int byte1 = bytes[index++] & 0xFF;
        int byte2 = bytes[index++] & 0xFF;
        int byte3 = bytes[index++] & 0xFF;
        int byte4 = bytes[index++] & 0xFF;
        return (byte1 << 24) | (byte2 << 16) | (byte3 << 8) | byte4;
    }

    /**
     * Reads an unsigned int (0 to 4,294,967,295) in big-endian order.
     *
     * @return unsigned int as long
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final long readUnsignedInt() {
        validateIndex(index, 4);
        long byte1 = bytes[index++] & 0xFF;
        long byte2 = bytes[index++] & 0xFF;
        long byte3 = bytes[index++] & 0xFF;
        long byte4 = bytes[index++] & 0xFF;
        return (byte1 << 24) | (byte2 << 16) | (byte3 << 8) | byte4;
    }

    /**
     * Reads a signed long in big-endian order.
     *
     * @return signed long value
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final long readLong() {
        validateIndex(index, 8);
        long byte1 = bytes[index++] & 0xFF;
        long byte2 = bytes[index++] & 0xFF;
        long byte3 = bytes[index++] & 0xFF;
        long byte4 = bytes[index++] & 0xFF;
        long byte5 = bytes[index++] & 0xFF;
        long byte6 = bytes[index++] & 0xFF;
        long byte7 = bytes[index++] & 0xFF;
        long byte8 = bytes[index++] & 0xFF;
        return (byte1 << 56) | (byte2 << 48) | (byte3 << 40) | (byte4 << 32) |
                (byte5 << 24) | (byte6 << 16) | (byte7 << 8) | byte8;
    }

    /**
     * Reads an unsigned long in big-endian order.
     *
     * @return unsigned long as BigInteger
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final BigInteger readUnsignedLong() {
        validateIndex(index, 8);
        byte[] longBytes = new byte[8];
        System.arraycopy(bytes, index, longBytes, 0, 8);
        index += 8;
        return new BigInteger(1, longBytes);
    }

    /**
     * Reads a double value.
     *
     * @return double value
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final double readDouble() {
        long bits = readLong();
        return Double.longBitsToDouble(bits);
    }

    /**
     * Reads a float value.
     *
     * @return float value
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final float readFloat() {
        int bits = readInt();
        return Float.intBitsToFloat(bits);
    }

    /**
     * Reads a UTF-8 encoded string.
     *
     * @return UTF-8 string
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final String readUtf8() {
        int length = readUnsignedShort();
        if (index + length > bytes.length) {
            throw new IndexOutOfBoundsException("Not enough bytes to read a UTF-8 string.");
        }
        String value = new String(bytes, index, length, StandardCharsets.UTF_8);
        index += length;
        return value;
    }

    private void validateIndex(int currentIndex, int length) {
        if (currentIndex < 0 || currentIndex + length > bytes.length) {
            throw new IndexOutOfBoundsException(
                    "Cannot read " + length + " bytes from index " + currentIndex +
                            ". Byte array length is " + bytes.length + ".");
        }
    }

    /**
     * Reads multiple bytes into destination array.
     *
     * @param code destination array
     * @param start starting position in destination
     * @param length number of bytes to read
     * @throws IndexOutOfBoundsException if insufficient bytes
     */
    public final void readBytes(byte[] code, int start, int length) {
        validateIndex(index, length);
        System.arraycopy(bytes, index, code, start, length);
        index += length;
    }
}
