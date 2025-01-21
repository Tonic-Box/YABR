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
     * Constructs an AbstractParser with the provided byte array.
     *
     * @param bytes The byte array to parse.
     * @throws IllegalArgumentException If the byte array is null.
     * @throws IncorrectFormatException If the byte array fails verification.
     */
    protected AbstractParser(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null.");
        }
        this.bytes = bytes.clone();
        this.index = 0;
        if (!verify()) {
            throw new IncorrectFormatException();
        }
        process();
    }

    /**
     * Returns the length of the byte array.
     *
     * @return Length of the byte array.
     */
    public final int getLength() {
        return bytes.length;
    }

    /**
     * Processes the byte array. Must be implemented by subclasses.
     */
    protected abstract void process();

    /**
     * Verifies the integrity or format of the byte array.
     *
     * @return True if verification succeeds, false otherwise.
     */
    protected boolean verify() {
        return true;
    }

    public final void rebuild() throws IOException
    {
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

    protected abstract byte[] write() throws IOException;

    /**
     * Reads a signed byte from the current index.
     *
     * @return The signed byte value.
     * @throws IndexOutOfBoundsException If the index is out of bounds.
     */
    public final byte readByte() {
        validateIndex(index, 1);
        return bytes[index++];
    }

    /**
     * Reads an unsigned byte (0 to 255) from the current index.
     *
     * @return The unsigned byte value as an int.
     * @throws IndexOutOfBoundsException If the index is out of bounds.
     */
    public final int readUnsignedByte() {
        validateIndex(index, 1);
        return bytes[index++] & 0xFF;
    }

    /**
     * Reads a signed short (−32,768 to 32,767) from the current index.
     * Assumes big-endian byte order.
     *
     * @return The signed short value.
     * @throws IndexOutOfBoundsException If there are not enough bytes to read a short.
     */
    public final short readShort() {
        validateIndex(index, 2);
        int high = bytes[index++] & 0xFF;
        int low = bytes[index++] & 0xFF;
        return (short) ((high << 8) | low);
    }

    /**
     * Reads an unsigned short (0 to 65,535) from the current index.
     * Assumes big-endian byte order.
     *
     * @return The unsigned short value as an int.
     * @throws IndexOutOfBoundsException If there are not enough bytes to read a short.
     */
    public final int readUnsignedShort() {
        validateIndex(index, 2);
        int high = bytes[index++] & 0xFF;
        int low = bytes[index++] & 0xFF;
        return (high << 8) | low;
    }

    /**
     * Reads a signed integer (−2,147,483,648 to 2,147,483,647) from the current index.
     * Assumes big-endian byte order.
     *
     * @return The signed integer value.
     * @throws IndexOutOfBoundsException If there are not enough bytes to read an integer.
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
     * Reads an unsigned integer (0 to 4,294,967,295) from the current index.
     * Assumes big-endian byte order.
     *
     * @return The unsigned integer value as a long.
     * @throws IndexOutOfBoundsException If there are not enough bytes to read an integer.
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
     * Reads a signed long (−9,223,372,036,854,775,808 to 9,223,372,036,854,775,807) from the current index.
     * Assumes big-endian byte order.
     *
     * @return The signed long value.
     * @throws IndexOutOfBoundsException If there are not enough bytes to read a long.
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
     * Reads an unsigned long (0 to 18,446,744,073,709,551,615) from the current index.
     * Assumes big-endian byte order.
     *
     * @return The unsigned long value as a BigInteger.
     * @throws IndexOutOfBoundsException If there are not enough bytes to read a long.
     */
    public final BigInteger readUnsignedLong() {
        validateIndex(index, 8);
        byte[] longBytes = new byte[8];
        System.arraycopy(bytes, index, longBytes, 0, 8);
        index += 8;
        return new BigInteger(1, longBytes); // 1 for positive number
    }

    /**
     * Reads a double value from the current index.
     *
     * @return The double value.
     * @throws IndexOutOfBoundsException If there are not enough bytes to read a double.
     */
    public final double readDouble() {
        long bits = readLong();
        return Double.longBitsToDouble(bits);
    }

    /**
     * Reads a signed float from the current index.
     *
     * @return The float value.
     * @throws IndexOutOfBoundsException If there are not enough bytes to read a float.
     */
    public final float readFloat() {
        int bits = readInt();
        return Float.intBitsToFloat(bits);
    }

    /**
     * Reads a UTF-8 encoded string from the current index.
     *
     * @return The UTF-8 string.
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

    /**
     * Validates that the byte array has enough bytes starting from the given index
     * to read the desired number of bytes.
     *
     * @param currentIndex The current reading index.
     * @param length       The number of bytes to read.
     * @throws IndexOutOfBoundsException If the byte array does not have enough bytes.
     */
    private void validateIndex(int currentIndex, int length) {
        if (currentIndex < 0 || currentIndex + length > bytes.length) {
            throw new IndexOutOfBoundsException(
                    "Cannot read " + length + " bytes from index " + currentIndex +
                            ". Byte array length is " + bytes.length + ".");
        }
    }

    public final void readBytes(byte[] code, int start, int length) {
        validateIndex(index, length); // Ensure there are enough bytes to read
        System.arraycopy(bytes, index, code, start, length); // Copy bytes from current index
        index += length; // Advance the index by the number of bytes read
    }
}
