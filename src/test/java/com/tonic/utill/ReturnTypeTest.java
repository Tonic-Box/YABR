package com.tonic.utill;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReturnTypeTest {

    @Nested
    class OpcodeTests {

        @Test
        void ireturnOpcode() {
            assertEquals(0xAC, ReturnType.IRETURN.getOpcode());
        }

        @Test
        void lreturnOpcode() {
            assertEquals(0xAD, ReturnType.LRETURN.getOpcode());
        }

        @Test
        void freturnOpcode() {
            assertEquals(0xAE, ReturnType.FRETURN.getOpcode());
        }

        @Test
        void dreturnOpcode() {
            assertEquals(0xAF, ReturnType.DRETURN.getOpcode());
        }

        @Test
        void areturnOpcode() {
            assertEquals(0xB0, ReturnType.ARETURN.getOpcode());
        }

        @Test
        void returnOpcode() {
            assertEquals(0xB1, ReturnType.RETURN.getOpcode());
        }
    }

    @Nested
    class MnemonicTests {

        @Test
        void ireturnMnemonic() {
            assertEquals("ireturn", ReturnType.IRETURN.getMnemonic());
        }

        @Test
        void lreturnMnemonic() {
            assertEquals("lreturn", ReturnType.LRETURN.getMnemonic());
        }

        @Test
        void freturnMnemonic() {
            assertEquals("freturn", ReturnType.FRETURN.getMnemonic());
        }

        @Test
        void dreturnMnemonic() {
            assertEquals("dreturn", ReturnType.DRETURN.getMnemonic());
        }

        @Test
        void areturnMnemonic() {
            assertEquals("areturn", ReturnType.ARETURN.getMnemonic());
        }

        @Test
        void returnMnemonic() {
            assertEquals("return", ReturnType.RETURN.getMnemonic());
        }
    }

    @Nested
    class FromOpcodeTests {

        @Test
        void fromOpcodeIreturn() {
            assertEquals(ReturnType.IRETURN, ReturnType.fromOpcode(0xAC));
        }

        @Test
        void fromOpcodeLreturn() {
            assertEquals(ReturnType.LRETURN, ReturnType.fromOpcode(0xAD));
        }

        @Test
        void fromOpcodeFreturn() {
            assertEquals(ReturnType.FRETURN, ReturnType.fromOpcode(0xAE));
        }

        @Test
        void fromOpcodeDreturn() {
            assertEquals(ReturnType.DRETURN, ReturnType.fromOpcode(0xAF));
        }

        @Test
        void fromOpcodeAreturn() {
            assertEquals(ReturnType.ARETURN, ReturnType.fromOpcode(0xB0));
        }

        @Test
        void fromOpcodeReturn() {
            assertEquals(ReturnType.RETURN, ReturnType.fromOpcode(0xB1));
        }

        @Test
        void fromOpcodeInvalid() {
            assertNull(ReturnType.fromOpcode(0x00));
        }

        @Test
        void fromOpcodeNegative() {
            assertNull(ReturnType.fromOpcode(-1));
        }

        @Test
        void fromOpcodeHigh() {
            assertNull(ReturnType.fromOpcode(0xFF));
        }
    }

    @Nested
    class FromDescriptorPrimitiveTests {

        @Test
        void fromDescriptorInt() {
            assertEquals(ReturnType.IRETURN, ReturnType.fromDescriptor("I"));
        }

        @Test
        void fromDescriptorLong() {
            assertEquals(ReturnType.LRETURN, ReturnType.fromDescriptor("J"));
        }

        @Test
        void fromDescriptorFloat() {
            assertEquals(ReturnType.FRETURN, ReturnType.fromDescriptor("F"));
        }

        @Test
        void fromDescriptorDouble() {
            assertEquals(ReturnType.DRETURN, ReturnType.fromDescriptor("D"));
        }

        @Test
        void fromDescriptorVoid() {
            assertEquals(ReturnType.RETURN, ReturnType.fromDescriptor("V"));
        }
    }

    @Nested
    class FromDescriptorObjectTests {

        @Test
        void fromDescriptorObject() {
            assertEquals(ReturnType.ARETURN, ReturnType.fromDescriptor("Ljava/lang/String;"));
        }

        @Test
        void fromDescriptorObjectSimple() {
            assertEquals(ReturnType.ARETURN, ReturnType.fromDescriptor("LObject;"));
        }

        @Test
        void fromDescriptorInnerClass() {
            assertEquals(ReturnType.ARETURN, ReturnType.fromDescriptor("Lcom/example/Outer$Inner;"));
        }
    }

    @Nested
    class FromDescriptorArrayTests {

        @Test
        void fromDescriptorPrimitiveArray() {
            assertEquals(ReturnType.ARETURN, ReturnType.fromDescriptor("[I"));
        }

        @Test
        void fromDescriptorObjectArray() {
            assertEquals(ReturnType.ARETURN, ReturnType.fromDescriptor("[Ljava/lang/String;"));
        }

        @Test
        void fromDescriptorMultiDimensionalArray() {
            assertEquals(ReturnType.ARETURN, ReturnType.fromDescriptor("[[I"));
        }

        @Test
        void fromDescriptor3DArray() {
            assertEquals(ReturnType.ARETURN, ReturnType.fromDescriptor("[[[Ljava/lang/Object;"));
        }
    }

    @Nested
    class FromDescriptorErrorTests {

        @Test
        void fromDescriptorNull() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ReturnType.fromDescriptor(null)
            );
            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        void fromDescriptorEmpty() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ReturnType.fromDescriptor("")
            );
            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }

        @Test
        void fromDescriptorInvalidChar() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ReturnType.fromDescriptor("X")
            );
            assertTrue(exception.getMessage().contains("Unknown descriptor"));
        }

        @Test
        void fromDescriptorInvalidObject() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ReturnType.fromDescriptor("Ljava/lang/String")
            );
            assertTrue(exception.getMessage().contains("Unknown descriptor"));
        }

        @Test
        void fromDescriptorMalformedObject() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ReturnType.fromDescriptor("java/lang/String;")
            );
            assertTrue(exception.getMessage().contains("Unknown descriptor"));
        }
    }

    @Nested
    class IsReturnOpcodeTests {

        @Test
        void isReturnOpcodeIreturn() {
            assertTrue(ReturnType.isReturnOpcode(0xAC));
        }

        @Test
        void isReturnOpcodeLreturn() {
            assertTrue(ReturnType.isReturnOpcode(0xAD));
        }

        @Test
        void isReturnOpcodeFreturn() {
            assertTrue(ReturnType.isReturnOpcode(0xAE));
        }

        @Test
        void isReturnOpcodeDreturn() {
            assertTrue(ReturnType.isReturnOpcode(0xAF));
        }

        @Test
        void isReturnOpcodeAreturn() {
            assertTrue(ReturnType.isReturnOpcode(0xB0));
        }

        @Test
        void isReturnOpcodeReturn() {
            assertTrue(ReturnType.isReturnOpcode(0xB1));
        }

        @Test
        void isReturnOpcodeInvalid() {
            assertFalse(ReturnType.isReturnOpcode(0x00));
        }

        @Test
        void isReturnOpcodeNegative() {
            assertFalse(ReturnType.isReturnOpcode(-1));
        }

        @Test
        void isReturnOpcodeHigh() {
            assertFalse(ReturnType.isReturnOpcode(0xFF));
        }
    }

    @Nested
    class EnumValuesTests {

        @Test
        void allValuesPresent() {
            ReturnType[] values = ReturnType.values();
            assertEquals(6, values.length);
        }

        @Test
        void valueOfIreturn() {
            assertEquals(ReturnType.IRETURN, ReturnType.valueOf("IRETURN"));
        }

        @Test
        void valueOfLreturn() {
            assertEquals(ReturnType.LRETURN, ReturnType.valueOf("LRETURN"));
        }

        @Test
        void valueOfFreturn() {
            assertEquals(ReturnType.FRETURN, ReturnType.valueOf("FRETURN"));
        }

        @Test
        void valueOfDreturn() {
            assertEquals(ReturnType.DRETURN, ReturnType.valueOf("DRETURN"));
        }

        @Test
        void valueOfAreturn() {
            assertEquals(ReturnType.ARETURN, ReturnType.valueOf("ARETURN"));
        }

        @Test
        void valueOfReturn() {
            assertEquals(ReturnType.RETURN, ReturnType.valueOf("RETURN"));
        }
    }

    @Nested
    class IntegrationTests {

        @Test
        void roundTripOpcodeToTypeToOpcode() {
            int originalOpcode = 0xAC;
            ReturnType type = ReturnType.fromOpcode(originalOpcode);
            assertEquals(originalOpcode, type.getOpcode());
        }

        @Test
        void descriptorToTypeToOpcode() {
            ReturnType type = ReturnType.fromDescriptor("I");
            assertEquals(0xAC, type.getOpcode());
            assertEquals("ireturn", type.getMnemonic());
        }

        @Test
        void allReturnOpcodesValid() {
            for (ReturnType type : ReturnType.values()) {
                assertTrue(ReturnType.isReturnOpcode(type.getOpcode()));
                assertEquals(type, ReturnType.fromOpcode(type.getOpcode()));
            }
        }

        @Test
        void descriptorPrimitiveBoolean() {
            assertEquals(ReturnType.IRETURN, ReturnType.fromDescriptor("I"));
        }

        @Test
        void descriptorPrimitiveByte() {
            assertEquals(ReturnType.IRETURN, ReturnType.fromDescriptor("I"));
        }

        @Test
        void descriptorPrimitiveShort() {
            assertEquals(ReturnType.IRETURN, ReturnType.fromDescriptor("I"));
        }

        @Test
        void descriptorPrimitiveChar() {
            assertEquals(ReturnType.IRETURN, ReturnType.fromDescriptor("I"));
        }
    }
}
