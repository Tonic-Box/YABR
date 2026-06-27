package com.tonic.analysis.execution.heap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleHeapManagerTest {

    private SimpleHeapManager heap;

    @BeforeEach
    void setUp() {
        heap = new SimpleHeapManager();
    }

    @Test
    void testNewObjectCreatesUniqueIds() {
        ObjectInstance obj1 = heap.newObject("java/lang/Object");
        ObjectInstance obj2 = heap.newObject("java/lang/Object");

        assertNotEquals(obj1.getId(), obj2.getId());
    }

    @Test
    void testNewObjectWithClassName() {
        ObjectInstance obj = heap.newObject("com/example/Person");
        assertEquals("com/example/Person", obj.getClassName());
    }

    @Test
    void testNewObjectIncrementsCount() {
        assertEquals(0, heap.objectCount());

        heap.newObject("java/lang/Object");
        assertEquals(1, heap.objectCount());

        heap.newObject("java/lang/Object");
        assertEquals(2, heap.objectCount());
    }

    @Test
    void testNewIntArray() {
        ArrayInstance array = heap.newArray("I", 10);

        assertEquals("I", array.getComponentType());
        assertEquals(10, array.getLength());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testNewLongArray() {
        ArrayInstance array = heap.newArray("J", 5);

        assertEquals("J", array.getComponentType());
        assertEquals(5, array.getLength());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testNewBooleanArray() {
        ArrayInstance array = heap.newArray("Z", 8);

        assertEquals("Z", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testNewByteArray() {
        ArrayInstance array = heap.newArray("B", 100);

        assertEquals("B", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testNewCharArray() {
        ArrayInstance array = heap.newArray("C", 20);

        assertEquals("C", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testNewShortArray() {
        ArrayInstance array = heap.newArray("S", 15);

        assertEquals("S", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testNewFloatArray() {
        ArrayInstance array = heap.newArray("F", 7);

        assertEquals("F", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testNewDoubleArray() {
        ArrayInstance array = heap.newArray("D", 12);

        assertEquals("D", array.getComponentType());
        assertTrue(array.isPrimitiveArray());
    }

    @Test
    void testNewObjectArray() {
        ArrayInstance array = heap.newArray("Ljava/lang/String;", 5);

        assertEquals("Ljava/lang/String;", array.getComponentType());
        assertEquals(5, array.getLength());
        assertFalse(array.isPrimitiveArray());
    }

    @Test
    void testNewArrayNegativeLengthThrows() {
        HeapException ex = assertThrows(HeapException.class, () -> heap.newArray("I", -1));
        assertTrue(ex.getMessage().contains("Negative array length"));
    }

    @Test
    void testNewArrayZeroLength() {
        ArrayInstance array = heap.newArray("I", 0);
        assertEquals(0, array.getLength());
    }

    @Test
    void testNewMultiArray2D() {
        ArrayInstance array = heap.newMultiArray("I", new int[]{3, 4});

        assertEquals(3, array.getLength());
        assertEquals("[[I", array.getClassName());

        for (int i = 0; i < 3; i++) {
            ArrayInstance subArray = (ArrayInstance) array.get(i);
            assertNotNull(subArray);
            assertEquals(4, subArray.getLength());
            assertEquals("I", subArray.getComponentType());
        }
    }

    @Test
    void testNewMultiArray3D() {
        ArrayInstance array = heap.newMultiArray("I", new int[]{2, 3, 4});

        assertEquals(2, array.getLength());

        for (int i = 0; i < 2; i++) {
            ArrayInstance level2 = (ArrayInstance) array.get(i);
            assertEquals(3, level2.getLength());

            for (int j = 0; j < 3; j++) {
                ArrayInstance level3 = (ArrayInstance) level2.get(j);
                assertEquals(4, level3.getLength());
                assertEquals("I", level3.getComponentType());
            }
        }
    }

    @Test
    void testNewMultiArrayNullDimensionsThrows() {
        assertThrows(HeapException.class, () -> heap.newMultiArray("I", null));
    }

    @Test
    void testNewMultiArrayEmptyDimensionsThrows() {
        assertThrows(HeapException.class, () -> heap.newMultiArray("I", new int[]{}));
    }

    @Test
    void testNewMultiArrayNegativeDimensionThrows() {
        HeapException ex = assertThrows(HeapException.class,
            () -> heap.newMultiArray("I", new int[]{3, -1}));
        assertTrue(ex.getMessage().contains("Negative dimension"));
    }

    @Test
    void testInternStringReturnsSameInstance() {
        ObjectInstance str1 = heap.internString("hello");
        ObjectInstance str2 = heap.internString("hello");

        assertSame(str1, str2);
    }

    @Test
    void testInternStringDifferentStrings() {
        ObjectInstance str1 = heap.internString("hello");
        ObjectInstance str2 = heap.internString("world");

        assertNotSame(str1, str2);
    }

    @Test
    void testInternStringIncrementsObjectCount() {
        long initialCount = heap.objectCount();

        heap.internString("test");
        long afterCount = heap.objectCount();

        assertTrue(afterCount > initialCount);
    }

    @Test
    void testIsNullWithNull() {
        assertTrue(heap.isNull(null));
    }

    @Test
    void testIsNullWithNonNull() {
        ObjectInstance obj = heap.newObject("java/lang/Object");
        assertFalse(heap.isNull(obj));
    }

    @Test
    void testIdentityHashCodeWithNull() {
        assertEquals(0, heap.identityHashCode(null));
    }

    @Test
    void testIdentityHashCodeWithObject() {
        ObjectInstance obj = heap.newObject("java/lang/Object");
        int hash = heap.identityHashCode(obj);

        assertEquals(obj.getIdentityHashCode(), hash);
        assertTrue(hash > 0);
    }

    @Test
    void testObjectCountWithMixedObjects() {
        assertEquals(0, heap.objectCount());

        heap.newObject("java/lang/Object");
        heap.newArray("I", 10);
        heap.internString("test");

        assertTrue(heap.objectCount() >= 3);
    }

    @Test
    void testClassResolverCanBeSet() {
        Object resolver = new Object();
        heap.setClassResolver(resolver);
    }

    @Test
    void testNewObjectsHaveSequentialIds() {
        ObjectInstance obj1 = heap.newObject("java/lang/Object");
        ObjectInstance obj2 = heap.newObject("java/lang/Object");

        assertTrue(obj2.getId() > obj1.getId());
    }

    @Test
    void testMultipleArrayTypes() {
        heap.newArray("I", 5);
        heap.newArray("J", 3);
        heap.newArray("Ljava/lang/String;", 7);

        assertEquals(3, heap.objectCount());
    }
}
