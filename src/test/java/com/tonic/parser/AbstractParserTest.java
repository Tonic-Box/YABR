package com.tonic.parser;

import com.tonic.exception.IncorrectFormatException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AbstractParserTest {

    @Nested
    class ConstructorTests {

        @Test
        void constructorWithNullByteArray() {
            assertThrows(IllegalArgumentException.class, () -> new TestParser(null));
        }

        @Test
        void constructorClonesArray() {
            byte[] original = {1, 2, 3, 4};
            TestParser parser = new TestParser(original, false);
            original[0] = 99;
            assertEquals(1, parser.getBytes()[0]);
        }

        @Test
        void constructorWithParseCallsVerify() {
            assertThrows(IncorrectFormatException.class, () -> new TestParser(new byte[]{1, 2, 3}, true, false));
        }

        @Test
        void constructorWithParseCallsProcess() {
            TestParser parser = new TestParser(new byte[]{1, 2, 3}, true, true);
            assertTrue(parser.processWasCalled);
        }

        @Test
        void constructorWithoutParseSkipsProcess() {
            TestParser parser = new TestParser(new byte[]{1, 2, 3}, false);
            assertFalse(parser.processWasCalled);
        }
    }

    @Nested
    class BasicReadOperations {

        @Test
        void readByte() {
            byte[] data = {-128, 0, 127};
            TestParser parser = new TestParser(data, false);
            assertEquals(-128, parser.readByte());
            assertEquals(0, parser.readByte());
            assertEquals(127, parser.readByte());
        }

        @Test
        void readUnsignedByte() {
            byte[] data = {(byte) 0xFF, 0, 127};
            TestParser parser = new TestParser(data, false);
            assertEquals(255, parser.readUnsignedByte());
            assertEquals(0, parser.readUnsignedByte());
            assertEquals(127, parser.readUnsignedByte());
        }

        @Test
        void readByteThrowsOnOutOfBounds() {
            TestParser parser = new TestParser(new byte[]{1}, false);
            parser.readByte();
            assertThrows(IndexOutOfBoundsException.class, parser::readByte);
        }
    }

    @Nested
    class ShortReadOperations {

        @Test
        void readShort() {
            byte[] data = {0x12, 0x34};
            TestParser parser = new TestParser(data, false);
            assertEquals(0x1234, parser.readShort());
        }

        @Test
        void readShortNegative() {
            byte[] data = {(byte) 0xFF, (byte) 0xFF};
            TestParser parser = new TestParser(data, false);
            assertEquals(-1, parser.readShort());
        }

        @Test
        void readUnsignedShort() {
            byte[] data = {(byte) 0xFF, (byte) 0xFF};
            TestParser parser = new TestParser(data, false);
            assertEquals(65535, parser.readUnsignedShort());
        }

        @Test
        void readUnsignedShortZero() {
            byte[] data = {0, 0};
            TestParser parser = new TestParser(data, false);
            assertEquals(0, parser.readUnsignedShort());
        }

        @Test
        void readShortThrowsOnInsufficientBytes() {
            TestParser parser = new TestParser(new byte[]{1}, false);
            assertThrows(IndexOutOfBoundsException.class, parser::readShort);
        }
    }

    @Nested
    class IntReadOperations {

        @Test
        void readInt() {
            byte[] data = {0x12, 0x34, 0x56, 0x78};
            TestParser parser = new TestParser(data, false);
            assertEquals(0x12345678, parser.readInt());
        }

        @Test
        void readIntNegative() {
            byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
            TestParser parser = new TestParser(data, false);
            assertEquals(-1, parser.readInt());
        }

        @Test
        void readUnsignedInt() {
            byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
            TestParser parser = new TestParser(data, false);
            assertEquals(4294967295L, parser.readUnsignedInt());
        }

        @Test
        void readUnsignedIntZero() {
            byte[] data = {0, 0, 0, 0};
            TestParser parser = new TestParser(data, false);
            assertEquals(0L, parser.readUnsignedInt());
        }

        @Test
        void readIntThrowsOnInsufficientBytes() {
            TestParser parser = new TestParser(new byte[]{1, 2, 3}, false);
            assertThrows(IndexOutOfBoundsException.class, parser::readInt);
        }
    }

    @Nested
    class LongReadOperations {

        @Test
        void readLong() {
            byte[] data = {0x12, 0x34, 0x56, 0x78, (byte) 0x9A, (byte) 0xBC, (byte) 0xDE, (byte) 0xF0};
            TestParser parser = new TestParser(data, false);
            assertEquals(0x123456789ABCDEF0L, parser.readLong());
        }

        @Test
        void readLongNegative() {
            byte[] data = new byte[8];
            for (int i = 0; i < 8; i++) data[i] = (byte) 0xFF;
            TestParser parser = new TestParser(data, false);
            assertEquals(-1L, parser.readLong());
        }

        @Test
        void readUnsignedLong() {
            byte[] data = new byte[8];
            for (int i = 0; i < 8; i++) data[i] = (byte) 0xFF;
            TestParser parser = new TestParser(data, false);
            BigInteger expected = new BigInteger("18446744073709551615");
            assertEquals(expected, parser.readUnsignedLong());
        }

        @Test
        void readUnsignedLongZero() {
            byte[] data = new byte[8];
            TestParser parser = new TestParser(data, false);
            assertEquals(BigInteger.ZERO, parser.readUnsignedLong());
        }

        @Test
        void readLongThrowsOnInsufficientBytes() {
            TestParser parser = new TestParser(new byte[]{1, 2, 3, 4, 5, 6, 7}, false);
            assertThrows(IndexOutOfBoundsException.class, parser::readLong);
        }
    }

    @Nested
    class FloatingPointReadOperations {

        @Test
        void readFloat() {
            int bits = Float.floatToIntBits(3.14159f);
            byte[] data = {
                (byte) (bits >> 24),
                (byte) (bits >> 16),
                (byte) (bits >> 8),
                (byte) bits
            };
            TestParser parser = new TestParser(data, false);
            assertEquals(3.14159f, parser.readFloat(), 0.00001f);
        }

        @Test
        void readFloatZero() {
            byte[] data = {0, 0, 0, 0};
            TestParser parser = new TestParser(data, false);
            assertEquals(0.0f, parser.readFloat());
        }

        @Test
        void readDouble() {
            long bits = Double.doubleToLongBits(3.141592653589793);
            byte[] data = new byte[8];
            for (int i = 0; i < 8; i++) {
                data[i] = (byte) (bits >> (56 - i * 8));
            }
            TestParser parser = new TestParser(data, false);
            assertEquals(3.141592653589793, parser.readDouble(), 0.0000000000001);
        }

        @Test
        void readDoubleZero() {
            byte[] data = new byte[8];
            TestParser parser = new TestParser(data, false);
            assertEquals(0.0, parser.readDouble());
        }
    }

    @Nested
    class StringReadOperations {

        @Test
        void readUtf8() {
            String text = "Hello";
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            byte[] data = new byte[2 + textBytes.length];
            data[0] = 0;
            data[1] = (byte) textBytes.length;
            System.arraycopy(textBytes, 0, data, 2, textBytes.length);

            TestParser parser = new TestParser(data, false);
            assertEquals(text, parser.readUtf8());
        }

        @Test
        void readUtf8Empty() {
            byte[] data = {0, 0};
            TestParser parser = new TestParser(data, false);
            assertEquals("", parser.readUtf8());
        }

        @Test
        void readUtf8Unicode() {
            String text = "Hello 世界";
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            byte[] data = new byte[2 + textBytes.length];
            data[0] = (byte) (textBytes.length >> 8);
            data[1] = (byte) textBytes.length;
            System.arraycopy(textBytes, 0, data, 2, textBytes.length);

            TestParser parser = new TestParser(data, false);
            assertEquals(text, parser.readUtf8());
        }

        @Test
        void readUtf8ThrowsOnInsufficientBytes() {
            byte[] data = {0, 10};
            TestParser parser = new TestParser(data, false);
            assertThrows(IndexOutOfBoundsException.class, parser::readUtf8);
        }
    }

    @Nested
    class ByteArrayReadOperations {

        @Test
        void readBytes() {
            byte[] data = {1, 2, 3, 4, 5};
            TestParser parser = new TestParser(data, false);
            byte[] dest = new byte[3];
            parser.readBytes(dest, 0, 3);
            assertArrayEquals(new byte[]{1, 2, 3}, dest);
        }

        @Test
        void readBytesWithOffset() {
            byte[] data = {1, 2, 3, 4, 5};
            TestParser parser = new TestParser(data, false);
            byte[] dest = new byte[5];
            parser.readBytes(dest, 2, 3);
            assertArrayEquals(new byte[]{0, 0, 1, 2, 3}, dest);
        }

        @Test
        void readBytesThrowsOnInsufficientBytes() {
            byte[] data = {1, 2, 3};
            TestParser parser = new TestParser(data, false);
            byte[] dest = new byte[5];
            assertThrows(IndexOutOfBoundsException.class, () -> parser.readBytes(dest, 0, 5));
        }
    }

    @Nested
    class RebuildOperations {

        @Test
        void rebuildSuccessful() throws IOException {
            TestParser parser = new TestParser(new byte[]{1, 2, 3}, false, true);
            parser.rebuild();
            assertArrayEquals(new byte[]{3, 2, 1}, parser.getBytes());
            assertTrue(parser.processWasCalled);
        }

        @Test
        void rebuildThrowsOnNullWrite() {
            TestParser parser = new TestParser(new byte[]{1, 2, 3}, false, true) {
                @Override
                protected byte[] write() {
                    return null;
                }
            };
            assertThrows(IllegalArgumentException.class, parser::rebuild);
        }

        @Test
        void rebuildThrowsOnVerifyFailure() {
            TestParser parser = new TestParser(new byte[]{1, 2, 3}, false, true) {
                @Override
                protected boolean verify() {
                    return false;
                }
            };
            assertThrows(IncorrectFormatException.class, parser::rebuild);
        }
    }

    @Nested
    class IndexManagement {

        @Test
        void getLength() {
            TestParser parser = new TestParser(new byte[]{1, 2, 3, 4, 5}, false);
            assertEquals(5, parser.getLength());
        }

        @Test
        void indexAdvancesOnRead() {
            TestParser parser = new TestParser(new byte[]{1, 2, 3, 4, 5}, false);
            assertEquals(0, parser.getIndex());
            parser.readByte();
            assertEquals(1, parser.getIndex());
            parser.readShort();
            assertEquals(3, parser.getIndex());
        }

        @Test
        void indexResetsOnRebuild() throws IOException {
            TestParser parser = new TestParser(new byte[]{1, 2, 3, 4, 5}, false, true);
            parser.readByte();
            parser.readByte();
            assertEquals(2, parser.getIndex());
            parser.rebuild();
            assertEquals(0, parser.getIndex());
        }
    }

    static class TestParser extends AbstractParser {
        boolean processWasCalled = false;
        private static boolean staticVerifyResult = true;

        TestParser(byte[] bytes) {
            this(bytes, true);
        }

        TestParser(byte[] bytes, boolean parse) {
            super(bytes, parse);
        }

        TestParser(byte[] bytes, boolean parse, boolean verifyResult) {
            super(bytes, false);
            staticVerifyResult = verifyResult;
            if (parse && !verify()) {
                throw new com.tonic.exception.IncorrectFormatException();
            }
            if (parse) {
                process();
            }
        }

        @Override
        protected void process() {
            processWasCalled = true;
        }

        @Override
        protected boolean verify() {
            return staticVerifyResult;
        }

        @Override
        protected byte[] write() {
            byte[] reversed = new byte[getBytes().length];
            for (int i = 0; i < getBytes().length; i++) {
                reversed[i] = getBytes()[getBytes().length - 1 - i];
            }
            return reversed;
        }
    }
}
