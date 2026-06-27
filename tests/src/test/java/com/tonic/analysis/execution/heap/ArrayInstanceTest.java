package com.tonic.analysis.execution.heap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArrayInstanceTest {

    @Test
    void testIntArrayCreation() {
        ArrayInstance array = new ArrayInstance(1, "I", 10);
        assertEquals(10, array.getLength());
        assertEquals("I", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
        assertEquals("[I", array.getClassName());
    }

    @Test
    void testIntArrayAccess() {
        ArrayInstance array = new ArrayInstance(1, "I", 5);
        array.setInt(0, 100);
        array.setInt(4, 500);

        assertEquals(100, array.getInt(0));
        assertEquals(500, array.getInt(4));
        assertEquals(0, array.getInt(2));
    }

    @Test
    void testIntArrayGenericAccess() {
        ArrayInstance array = new ArrayInstance(1, "I", 3);
        array.set(0, 10);
        array.set(1, 20);

        assertEquals(10, array.get(0));
        assertEquals(20, array.get(1));
    }

    @Test
    void testLongArrayCreation() {
        ArrayInstance array = new ArrayInstance(1, "J", 5);
        assertEquals("J", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testLongArrayAccess() {
        ArrayInstance array = new ArrayInstance(1, "J", 3);
        array.setLong(0, 1000000000000L);
        array.setLong(2, 9999999999999L);

        assertEquals(1000000000000L, array.getLong(0));
        assertEquals(9999999999999L, array.getLong(2));
    }

    @Test
    void testBooleanArrayCreation() {
        ArrayInstance array = new ArrayInstance(1, "Z", 4);
        assertEquals("Z", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testBooleanArrayAccess() {
        ArrayInstance array = new ArrayInstance(1, "Z", 3);
        array.setBoolean(0, true);
        array.setBoolean(1, false);
        array.setBoolean(2, true);

        assertTrue(array.getBoolean(0));
        assertFalse(array.getBoolean(1));
        assertTrue(array.getBoolean(2));
    }

    @Test
    void testByteArrayCreation() {
        ArrayInstance array = new ArrayInstance(1, "B", 10);
        assertEquals("B", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testByteArrayAccess() {
        ArrayInstance array = new ArrayInstance(1, "B", 3);
        array.setByte(0, (byte) 10);
        array.setByte(1, (byte) 20);

        assertEquals((byte) 10, array.getByte(0));
        assertEquals((byte) 20, array.getByte(1));
    }

    @Test
    void testCharArrayCreation() {
        ArrayInstance array = new ArrayInstance(1, "C", 5);
        assertEquals("C", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testCharArrayAccess() {
        ArrayInstance array = new ArrayInstance(1, "C", 5);
        array.setChar(0, 'H');
        array.setChar(1, 'e');
        array.setChar(2, 'l');
        array.setChar(3, 'l');
        array.setChar(4, 'o');

        assertEquals('H', array.getChar(0));
        assertEquals('e', array.getChar(1));
        assertEquals('o', array.getChar(4));
    }

    @Test
    void testShortArrayCreation() {
        ArrayInstance array = new ArrayInstance(1, "S", 3);
        assertEquals("S", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testShortArrayAccess() {
        ArrayInstance array = new ArrayInstance(1, "S", 2);
        array.setShort(0, (short) 1000);
        array.setShort(1, (short) 2000);

        assertEquals((short) 1000, array.getShort(0));
        assertEquals((short) 2000, array.getShort(1));
    }

    @Test
    void testFloatArrayCreation() {
        ArrayInstance array = new ArrayInstance(1, "F", 4);
        assertEquals("F", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testFloatArrayAccess() {
        ArrayInstance array = new ArrayInstance(1, "F", 3);
        array.setFloat(0, 3.14f);
        array.setFloat(1, 2.71f);

        assertEquals(3.14f, array.getFloat(0), 0.001f);
        assertEquals(2.71f, array.getFloat(1), 0.001f);
    }

    @Test
    void testDoubleArrayCreation() {
        ArrayInstance array = new ArrayInstance(1, "D", 5);
        assertEquals("D", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testDoubleArrayAccess() {
        ArrayInstance array = new ArrayInstance(1, "D", 2);
        array.setDouble(0, 3.14159265359);
        array.setDouble(1, 2.71828182846);

        assertEquals(3.14159265359, array.getDouble(0), 0.0000001);
        assertEquals(2.71828182846, array.getDouble(1), 0.0000001);
    }

    @Test
    void testObjectArrayCreation() {
        ArrayInstance array = new ArrayInstance(1, "Ljava/lang/String;", 5);
        assertEquals("Ljava/lang/String;", array.getComponentType());
        assertFalse(array.isPrimitiveArray());
        assertEquals("[Ljava/lang/String;", array.getClassName());
    }

    @Test
    void testObjectArrayAccess() {
        ArrayInstance array = new ArrayInstance(1, "Ljava/lang/Object;", 3);
        ObjectInstance obj1 = new ObjectInstance(10, "java/lang/String");
        ObjectInstance obj2 = new ObjectInstance(20, "java/lang/Integer");

        array.set(0, obj1);
        array.set(1, obj2);
        array.set(2, null);

        assertSame(obj1, array.get(0));
        assertSame(obj2, array.get(1));
        assertNull(array.get(2));
    }

    @Test
    void testBoundsCheckingLowerBound() {
        ArrayInstance array = new ArrayInstance(1, "I", 5);

        HeapException ex = assertThrows(HeapException.class, () -> array.getInt(-1));
        assertTrue(ex.getMessage().contains("out of bounds"));
    }

    @Test
    void testBoundsCheckingUpperBound() {
        ArrayInstance array = new ArrayInstance(1, "I", 5);

        HeapException ex = assertThrows(HeapException.class, () -> array.getInt(5));
        assertTrue(ex.getMessage().contains("out of bounds"));
    }

    @Test
    void testBoundsCheckingOnSet() {
        ArrayInstance array = new ArrayInstance(1, "I", 3);

        assertThrows(HeapException.class, () -> array.setInt(-1, 100));
        assertThrows(HeapException.class, () -> array.setInt(3, 100));
    }

    @Test
    void testNegativeLengthThrows() {
        HeapException ex = assertThrows(HeapException.class, () -> new ArrayInstance(1, "I", -1));
        assertTrue(ex.getMessage().contains("Negative array length"));
    }

    @Test
    void testZeroLengthArray() {
        ArrayInstance array = new ArrayInstance(1, "I", 0);
        assertEquals(0, array.getLength());

        assertThrows(HeapException.class, () -> array.getInt(0));
    }

    @Test
    void testToString() {
        ArrayInstance array = new ArrayInstance(255, "I", 10);
        String str = array.toString();

        assertTrue(str.contains("[I"));
        assertTrue(str.contains("@"));
        assertTrue(str.contains("[10]"));
    }

    @Test
    void testWrongTypedAccessThrows() {
        ArrayInstance array = new ArrayInstance(1, "I", 5);

        assertThrows(HeapException.class, () -> array.getLong(0));
        assertThrows(HeapException.class, () -> array.setLong(0, 100L));
    }

    @Test
    void testMultiDimensionalArrayComponentType() {
        ArrayInstance array = new ArrayInstance(1, "[I", 5);
        assertEquals("[I", array.getComponentType());
        assertFalse(array.isPrimitiveArray());
    }

    @Test
    void testArrayIsInstanceOfObject() {
        ArrayInstance array = new ArrayInstance(1, "I", 5);
        assertTrue(array.isInstanceOf("java/lang/Object"));
    }

    @Test
    void testGenericGetWithBoolean() {
        ArrayInstance array = new ArrayInstance(1, "Z", 3);
        array.set(0, true);
        array.set(1, false);

        assertEquals(true, array.get(0));
        assertEquals(false, array.get(1));
    }

    @Test
    void testGenericGetWithByte() {
        ArrayInstance array = new ArrayInstance(1, "B", 2);
        array.set(0, (byte) 42);

        assertEquals((byte) 42, array.get(0));
    }

    @Test
    void testGenericGetWithChar() {
        ArrayInstance array = new ArrayInstance(1, "C", 2);
        array.set(0, 'X');

        assertEquals('X', array.get(0));
    }

    @Test
    void testGenericGetWithShort() {
        ArrayInstance array = new ArrayInstance(1, "S", 2);
        array.set(0, (short) 100);

        assertEquals((short) 100, array.get(0));
    }

    @Test
    void testGenericGetWithLong() {
        ArrayInstance array = new ArrayInstance(1, "J", 2);
        array.set(0, 123456789L);

        assertEquals(123456789L, array.get(0));
    }

    @Test
    void testGenericGetWithFloat() {
        ArrayInstance array = new ArrayInstance(1, "F", 2);
        array.set(0, 1.5f);

        assertEquals(1.5f, array.get(0));
    }

    @Test
    void testGenericGetWithDouble() {
        ArrayInstance array = new ArrayInstance(1, "D", 2);
        array.set(0, 2.5);

        assertEquals(2.5, array.get(0));
    }

    @Test
    void testArrayIdentity() {
        ArrayInstance array = new ArrayInstance(42, "I", 5);
        assertEquals(42, array.getId());
        assertEquals(42, array.getIdentityHashCode());
    }

    @Test
    void testArrayInheritance() {
        ArrayInstance array = new ArrayInstance(1, "I", 5);
        assertTrue(array instanceof ObjectInstance);
    }
}
