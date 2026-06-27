package com.tonic.analysis.execution.heap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjectInstanceTest {

    @Test
    void testConstructor() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/Object");
        assertEquals(1, obj.getId());
        assertEquals("java/lang/Object", obj.getClassName());
    }

    @Test
    void testSetAndGetField() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Person");
        obj.setField("com/example/Person", "name", "Ljava/lang/String;", "John");

        Object value = obj.getField("com/example/Person", "name", "Ljava/lang/String;");
        assertEquals("John", value);
    }

    @Test
    void testGetNonExistentField() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Person");
        Object value = obj.getField("com/example/Person", "nonexistent", "I");
        assertNull(value);
    }

    @Test
    void testFieldWithDifferentOwnerClasses() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Child");

        obj.setField("com/example/Parent", "parentField", "I", 10);
        obj.setField("com/example/Child", "childField", "I", 20);

        assertEquals(10, obj.getField("com/example/Parent", "parentField", "I"));
        assertEquals(20, obj.getField("com/example/Child", "childField", "I"));
    }

    @Test
    void testFieldShadowing() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Child");

        obj.setField("com/example/Parent", "field", "I", 100);
        obj.setField("com/example/Child", "field", "I", 200);

        assertEquals(100, obj.getField("com/example/Parent", "field", "I"));
        assertEquals(200, obj.getField("com/example/Child", "field", "I"));
    }

    @Test
    void testFieldOverwrite() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Person");

        obj.setField("com/example/Person", "age", "I", 25);
        assertEquals(25, obj.getField("com/example/Person", "age", "I"));

        obj.setField("com/example/Person", "age", "I", 30);
        assertEquals(30, obj.getField("com/example/Person", "age", "I"));
    }

    @Test
    void testMultipleFieldsDifferentDescriptors() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Test");

        obj.setField("com/example/Test", "value", "I", 42);
        obj.setField("com/example/Test", "value", "J", 100L);

        assertEquals(42, obj.getField("com/example/Test", "value", "I"));
        assertEquals(100L, obj.getField("com/example/Test", "value", "J"));
    }

    @Test
    void testFieldStorageWithNullValue() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Test");

        obj.setField("com/example/Test", "ref", "Ljava/lang/Object;", null);
        assertNull(obj.getField("com/example/Test", "ref", "Ljava/lang/Object;"));
    }

    @Test
    void testFieldStorageWithObjectReference() {
        ObjectInstance obj1 = new ObjectInstance(1, "com/example/Test");
        ObjectInstance obj2 = new ObjectInstance(2, "java/lang/String");

        obj1.setField("com/example/Test", "ref", "Ljava/lang/String;", obj2);
        assertSame(obj2, obj1.getField("com/example/Test", "ref", "Ljava/lang/String;"));
    }

    @Test
    void testIdentityHashCode() {
        ObjectInstance obj1 = new ObjectInstance(1, "java/lang/Object");
        ObjectInstance obj2 = new ObjectInstance(2, "java/lang/Object");

        assertEquals(1, obj1.getIdentityHashCode());
        assertEquals(2, obj2.getIdentityHashCode());
    }

    @Test
    void testReferenceEquality() {
        ObjectInstance obj1 = new ObjectInstance(1, "java/lang/Object");
        ObjectInstance obj2 = new ObjectInstance(2, "java/lang/Object");
        ObjectInstance obj1Ref = obj1;

        assertTrue(obj1.equals(obj1));
        assertTrue(obj1.equals(obj1Ref));
        assertFalse(obj1.equals(obj2));
        assertFalse(obj1.equals(null));
        assertFalse(obj1.equals("string"));
    }

    @Test
    void testHashCodeBasedOnId() {
        ObjectInstance obj1 = new ObjectInstance(1, "java/lang/Object");
        ObjectInstance obj2 = new ObjectInstance(2, "java/lang/Object");

        assertEquals(1, obj1.hashCode());
        assertEquals(2, obj2.hashCode());
    }

    @Test
    void testToString() {
        ObjectInstance obj = new ObjectInstance(255, "com/example/Test");
        String str = obj.toString();

        assertTrue(str.contains("com/example/Test"));
        assertTrue(str.contains("@"));
        assertTrue(str.contains("ff"));
    }

    @Test
    void testIsInstanceOfSameClass() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Test");
        assertTrue(obj.isInstanceOf("com/example/Test"));
    }

    @Test
    void testIsInstanceOfObject() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Test");
        assertTrue(obj.isInstanceOf("java/lang/Object"));
    }

    @Test
    void testIsInstanceOfUnrelatedClass() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Test");
        assertFalse(obj.isInstanceOf("com/example/Other"));
    }

    @Test
    void testMultipleFieldsOnSameObject() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Person");

        obj.setField("com/example/Person", "name", "Ljava/lang/String;", "Alice");
        obj.setField("com/example/Person", "age", "I", 25);
        obj.setField("com/example/Person", "salary", "D", 50000.0);

        assertEquals("Alice", obj.getField("com/example/Person", "name", "Ljava/lang/String;"));
        assertEquals(25, obj.getField("com/example/Person", "age", "I"));
        assertEquals(50000.0, obj.getField("com/example/Person", "salary", "D"));
    }

    @Test
    void testFieldKeyEquality() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Test");

        obj.setField("com/example/Test", "field", "I", 100);
        obj.setField("com/example/Test", "field", "I", 200);

        assertEquals(200, obj.getField("com/example/Test", "field", "I"));
    }

    @Test
    void testClassResolverCanBeSet() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Test");
        Object resolver = new Object();

        obj.setClassResolver(resolver);
    }

    @Test
    void testDifferentInstancesHaveDifferentIds() {
        ObjectInstance obj1 = new ObjectInstance(1, "java/lang/Object");
        ObjectInstance obj2 = new ObjectInstance(2, "java/lang/Object");

        assertNotEquals(obj1.getId(), obj2.getId());
        assertNotEquals(obj1.getIdentityHashCode(), obj2.getIdentityHashCode());
    }

    @Test
    void testFieldStorageWithPrimitiveTypes() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/Test");

        obj.setField("com/example/Test", "byteVal", "B", (byte) 1);
        obj.setField("com/example/Test", "shortVal", "S", (short) 2);
        obj.setField("com/example/Test", "intVal", "I", 3);
        obj.setField("com/example/Test", "longVal", "J", 4L);
        obj.setField("com/example/Test", "floatVal", "F", 5.0f);
        obj.setField("com/example/Test", "doubleVal", "D", 6.0);
        obj.setField("com/example/Test", "boolVal", "Z", true);
        obj.setField("com/example/Test", "charVal", "C", 'A');

        assertEquals((byte) 1, obj.getField("com/example/Test", "byteVal", "B"));
        assertEquals((short) 2, obj.getField("com/example/Test", "shortVal", "S"));
        assertEquals(3, obj.getField("com/example/Test", "intVal", "I"));
        assertEquals(4L, obj.getField("com/example/Test", "longVal", "J"));
        assertEquals(5.0f, obj.getField("com/example/Test", "floatVal", "F"));
        assertEquals(6.0, obj.getField("com/example/Test", "doubleVal", "D"));
        assertEquals(true, obj.getField("com/example/Test", "boolVal", "Z"));
        assertEquals('A', obj.getField("com/example/Test", "charVal", "C"));
    }

    @Test
    void testComplexFieldInheritanceScenario() {
        ObjectInstance obj = new ObjectInstance(1, "com/example/GrandChild");

        obj.setField("com/example/GrandParent", "field1", "I", 1);
        obj.setField("com/example/Parent", "field2", "I", 2);
        obj.setField("com/example/GrandChild", "field3", "I", 3);

        obj.setField("com/example/GrandParent", "common", "I", 100);
        obj.setField("com/example/Parent", "common", "I", 200);
        obj.setField("com/example/GrandChild", "common", "I", 300);

        assertEquals(1, obj.getField("com/example/GrandParent", "field1", "I"));
        assertEquals(2, obj.getField("com/example/Parent", "field2", "I"));
        assertEquals(3, obj.getField("com/example/GrandChild", "field3", "I"));

        assertEquals(100, obj.getField("com/example/GrandParent", "common", "I"));
        assertEquals(200, obj.getField("com/example/Parent", "common", "I"));
        assertEquals(300, obj.getField("com/example/GrandChild", "common", "I"));
    }

    @Test
    void testToStringWithZeroId() {
        ObjectInstance obj = new ObjectInstance(0, "java/lang/Object");
        String str = obj.toString();
        assertTrue(str.contains("java/lang/Object"));
        assertTrue(str.contains("@0"));
    }

    @Test
    void testToStringWithLargeId() {
        ObjectInstance obj = new ObjectInstance(0xFFFFFF, "java/lang/Object");
        String str = obj.toString();
        assertTrue(str.contains("ffffff"));
    }

    @Test
    void testFieldsAreIndependentBetweenInstances() {
        ObjectInstance obj1 = new ObjectInstance(1, "com/example/Test");
        ObjectInstance obj2 = new ObjectInstance(2, "com/example/Test");

        obj1.setField("com/example/Test", "value", "I", 100);
        obj2.setField("com/example/Test", "value", "I", 200);

        assertEquals(100, obj1.getField("com/example/Test", "value", "I"));
        assertEquals(200, obj2.getField("com/example/Test", "value", "I"));
    }

    @Test
    void testEqualsSameInstance() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/Object");
        assertTrue(obj.equals(obj));
    }

    @Test
    void testEqualsDifferentInstancesSameId() {
        ObjectInstance obj1 = new ObjectInstance(1, "java/lang/Object");
        ObjectInstance obj2 = new ObjectInstance(1, "java/lang/Object");
        assertFalse(obj1.equals(obj2));
    }

    @Test
    void testHashCodeConsistency() {
        ObjectInstance obj = new ObjectInstance(42, "java/lang/Object");
        int hash1 = obj.hashCode();
        int hash2 = obj.hashCode();
        assertEquals(hash1, hash2);
    }
}
