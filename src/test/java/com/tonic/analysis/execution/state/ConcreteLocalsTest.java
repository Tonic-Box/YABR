package com.tonic.analysis.execution.state;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConcreteLocalsTest {


    @Test
    void testSetGetInt() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setInt(0, 42);
        assertEquals(42, locals.getInt(0));
    }

    @Test
    void testSetGetLong() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setLong(0, 123456789L);
        assertEquals(123456789L, locals.getLong(0));
    }

    @Test
    void testSetGetFloat() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setFloat(0, 3.14f);
        assertEquals(3.14f, locals.getFloat(0), 0.0001f);
    }

    @Test
    void testSetGetDouble() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setDouble(0, 2.718);
        assertEquals(2.718, locals.getDouble(0), 0.0001);
    }

    @Test
    void testSetGetReference() {
        ConcreteLocals locals = new ConcreteLocals(10);
        ObjectInstance obj = new ObjectInstance(1, "java/lang/String");
        locals.setReference(0, obj);
        assertEquals(obj, locals.getReference(0));
    }

    @Test
    void testSetGetNull() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setNull(0);
        assertNull(locals.getReference(0));
    }

    @Test
    void testSetGetValue() {
        ConcreteLocals locals = new ConcreteLocals(10);
        ConcreteValue val = ConcreteValue.intValue(99);
        locals.set(0, val);
        assertEquals(val, locals.get(0));
    }

    @Test
    void testLongOccupiesTwoSlots() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setLong(0, 100L);
        assertTrue(locals.isDefined(0));
        assertFalse(locals.isDefined(1));
        locals.setInt(2, 42);
        assertEquals(42, locals.getInt(2));
    }

    @Test
    void testDoubleOccupiesTwoSlots() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setDouble(5, 3.14);
        assertTrue(locals.isDefined(5));
        assertFalse(locals.isDefined(6));
        locals.setInt(7, 99);
        assertEquals(99, locals.getInt(7));
    }

    @Test
    void testGetUndefinedThrows() {
        ConcreteLocals locals = new ConcreteLocals(10);
        assertThrows(IllegalStateException.class, () -> locals.get(0));
    }

    @Test
    void testGetOutOfBoundsThrows() {
        ConcreteLocals locals = new ConcreteLocals(10);
        assertThrows(IndexOutOfBoundsException.class, () -> locals.get(10));
        assertThrows(IndexOutOfBoundsException.class, () -> locals.get(-1));
    }

    @Test
    void testSetOutOfBoundsThrows() {
        ConcreteLocals locals = new ConcreteLocals(10);
        assertThrows(IndexOutOfBoundsException.class, () -> locals.setInt(10, 42));
        assertThrows(IndexOutOfBoundsException.class, () -> locals.setInt(-1, 42));
    }

    @Test
    void testWideValueAtEndThrows() {
        ConcreteLocals locals = new ConcreteLocals(10);
        assertThrows(IllegalArgumentException.class, () -> locals.setLong(9, 100L));
    }

    @Test
    void testSize() {
        ConcreteLocals locals = new ConcreteLocals(15);
        assertEquals(15, locals.size());
    }

    @Test
    void testIsDefined() {
        ConcreteLocals locals = new ConcreteLocals(10);
        assertFalse(locals.isDefined(0));
        locals.setInt(0, 42);
        assertTrue(locals.isDefined(0));
        assertFalse(locals.isDefined(1));
    }

    @Test
    void testIsDefinedOutOfBounds() {
        ConcreteLocals locals = new ConcreteLocals(10);
        assertFalse(locals.isDefined(10));
        assertFalse(locals.isDefined(-1));
    }

    @Test
    void testSnapshot() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setInt(0, 10);
        locals.setInt(2, 20);
        locals.setInt(5, 30);
        Map<Integer, ConcreteValue> snapshot = locals.snapshot();
        assertEquals(3, snapshot.size());
        assertEquals(10, snapshot.get(0).asInt());
        assertEquals(20, snapshot.get(2).asInt());
        assertEquals(30, snapshot.get(5).asInt());
        assertNull(snapshot.get(1));
    }

    @Test
    void testSnapshotUnmodifiable() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setInt(0, 42);
        Map<Integer, ConcreteValue> snapshot = locals.snapshot();
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.put(1, ConcreteValue.intValue(99)));
    }

    @Test
    void testSnapshotEmpty() {
        ConcreteLocals locals = new ConcreteLocals(10);
        Map<Integer, ConcreteValue> snapshot = locals.snapshot();
        assertTrue(snapshot.isEmpty());
    }

    @Test
    void testForMethodNullMethodThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ConcreteLocals.forMethod(null, new ConcreteValue[0]));
    }

    @Test
    void testToString() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setInt(0, 42);
        String str = locals.toString();
        assertTrue(str.contains("max=10"));
    }

    @Test
    void testOverwriteValue() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setInt(0, 42);
        assertEquals(42, locals.getInt(0));
        locals.setInt(0, 99);
        assertEquals(99, locals.getInt(0));
    }

    @Test
    void testMultipleSlots() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setInt(0, 10);
        locals.setInt(1, 20);
        locals.setInt(2, 30);
        locals.setInt(3, 40);
        assertEquals(10, locals.getInt(0));
        assertEquals(20, locals.getInt(1));
        assertEquals(30, locals.getInt(2));
        assertEquals(40, locals.getInt(3));
    }

    @Test
    void testMixedTypes() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setInt(0, 42);
        locals.setLong(1, 100L);
        locals.setFloat(3, 3.14f);
        locals.setDouble(4, 2.718);
        ObjectInstance obj = new ObjectInstance(1, "Test");
        locals.setReference(6, obj);
        locals.setNull(7);

        assertEquals(42, locals.getInt(0));
        assertEquals(100L, locals.getLong(1));
        assertEquals(3.14f, locals.getFloat(3), 0.001f);
        assertEquals(2.718, locals.getDouble(4), 0.001);
        assertEquals(obj, locals.getReference(6));
        assertNull(locals.getReference(7));
    }

    @Test
    void testSnapshotWithWideValues() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setInt(0, 42);
        locals.setLong(1, 100L);
        locals.setInt(3, 99);
        Map<Integer, ConcreteValue> snapshot = locals.snapshot();
        assertEquals(3, snapshot.size());
        assertTrue(snapshot.containsKey(0));
        assertTrue(snapshot.containsKey(1));
        assertFalse(snapshot.containsKey(2));
        assertTrue(snapshot.containsKey(3));
    }
}
