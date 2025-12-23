package com.tonic.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccessFlagsTest {

    @Test
    void testClassAccessFlags() {
        assertEquals(0x0001, AccessFlags.ACC_PUBLIC);
        assertEquals(0x0010, AccessFlags.ACC_FINAL);
        assertEquals(0x0020, AccessFlags.ACC_SUPER);
        assertEquals(0x0200, AccessFlags.ACC_INTERFACE);
        assertEquals(0x0400, AccessFlags.ACC_ABSTRACT);
        assertEquals(0x1000, AccessFlags.ACC_SYNTHETIC);
        assertEquals(0x2000, AccessFlags.ACC_ANNOTATION);
        assertEquals(0x4000, AccessFlags.ACC_ENUM);
    }

    @Test
    void testFieldAccessFlags() {
        assertEquals(0x0002, AccessFlags.ACC_PRIVATE);
        assertEquals(0x0004, AccessFlags.ACC_PROTECTED);
        assertEquals(0x0008, AccessFlags.ACC_STATIC);
        assertEquals(0x0040, AccessFlags.ACC_VOLATILE);
        assertEquals(0x0080, AccessFlags.ACC_TRANSIENT);
    }

    @Test
    void testMethodAccessFlags() {
        assertEquals(0x0020, AccessFlags.ACC_SYNCHRONIZED);
        assertEquals(0x0040, AccessFlags.ACC_BRIDGE);
        assertEquals(0x0080, AccessFlags.ACC_VARARGS);
        assertEquals(0x0100, AccessFlags.ACC_NATIVE);
        assertEquals(0x0800, AccessFlags.ACC_STRICT);
    }

    @Test
    void testArrayTypeConstants() {
        assertEquals(4, AccessFlags.T_BOOLEAN);
        assertEquals(5, AccessFlags.T_CHAR);
        assertEquals(6, AccessFlags.T_FLOAT);
        assertEquals(7, AccessFlags.T_DOUBLE);
        assertEquals(8, AccessFlags.T_BYTE);
        assertEquals(9, AccessFlags.T_SHORT);
        assertEquals(10, AccessFlags.T_INT);
        assertEquals(11, AccessFlags.T_LONG);
    }

    @Test
    void testVersionConstants() {
        assertEquals(45, AccessFlags.V1_1);
        assertEquals(49, AccessFlags.V1_5);
        assertEquals(50, AccessFlags.V1_6);
        assertEquals(51, AccessFlags.V1_7);
        assertEquals(52, AccessFlags.V1_8);
        assertEquals(53, AccessFlags.V9);
        assertEquals(55, AccessFlags.V11);
        assertEquals(61, AccessFlags.V17);
        assertEquals(65, AccessFlags.V21);
    }

    @Test
    void testFlagCombinations() {
        int publicStatic = AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC;
        assertEquals(0x0009, publicStatic);

        int publicFinalStatic = AccessFlags.ACC_PUBLIC | AccessFlags.ACC_FINAL | AccessFlags.ACC_STATIC;
        assertEquals(0x0019, publicFinalStatic);
    }

    @Test
    void testOpcodeConstants() {
        assertEquals(0x15, AccessFlags.ILOAD);
        assertEquals(0x16, AccessFlags.LLOAD);
        assertEquals(0x17, AccessFlags.FLOAD);
        assertEquals(0x18, AccessFlags.DLOAD);
        assertEquals(0x19, AccessFlags.ALOAD);

        assertEquals(0x36, AccessFlags.ISTORE);
        assertEquals(0x37, AccessFlags.LSTORE);
        assertEquals(0x38, AccessFlags.FSTORE);
        assertEquals(0x39, AccessFlags.DSTORE);
        assertEquals(0x3A, AccessFlags.ASTORE);

        assertEquals(0xAC, AccessFlags.IRETURN);
        assertEquals(0xAD, AccessFlags.LRETURN);
        assertEquals(0xAE, AccessFlags.FRETURN);
        assertEquals(0xAF, AccessFlags.DRETURN);
        assertEquals(0xB0, AccessFlags.ARETURN);
        assertEquals(0xB1, AccessFlags.RETURN);
    }
}
