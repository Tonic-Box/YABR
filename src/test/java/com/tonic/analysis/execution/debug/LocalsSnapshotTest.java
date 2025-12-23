package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteValue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalsSnapshotTest {

    @Test
    void testConstructionFromConcreteLocals() {
        ConcreteLocals locals = new ConcreteLocals(5);
        locals.setInt(0, 42);
        locals.setLong(1, 100L);

        LocalsSnapshot snapshot = new LocalsSnapshot(locals);
        assertNotNull(snapshot);
        assertEquals(2, snapshot.size());
    }

    @Test
    void testSizeReflectsDefinedSlots() {
        ConcreteLocals locals = new ConcreteLocals(10);
        locals.setInt(0, 1);
        locals.setInt(2, 2);
        locals.setInt(5, 3);

        LocalsSnapshot snapshot = new LocalsSnapshot(locals);
        assertEquals(3, snapshot.size());
    }

    @Test
    void testGetSpecificSlot() {
        ConcreteLocals locals = new ConcreteLocals(5);
        locals.setInt(0, 42);
        locals.setInt(2, 100);

        LocalsSnapshot snapshot = new LocalsSnapshot(locals);

        ValueInfo value0 = snapshot.get(0);
        assertNotNull(value0);
        assertEquals("INT", value0.getType());
        assertEquals(42, value0.getRawValue());

        ValueInfo value2 = snapshot.get(2);
        assertNotNull(value2);
        assertEquals(100, value2.getRawValue());
    }

    @Test
    void testUndefinedSlotsNotIncluded() {
        ConcreteLocals locals = new ConcreteLocals(5);
        locals.setInt(0, 42);

        LocalsSnapshot snapshot = new LocalsSnapshot(locals);
        assertNull(snapshot.get(1));
        assertNull(snapshot.get(2));
        assertNull(snapshot.get(3));
        assertNull(snapshot.get(4));
    }

    @Test
    void testGetValuesReturnsMap() {
        ConcreteLocals locals = new ConcreteLocals(5);
        locals.setInt(0, 42);
        locals.setInt(2, 100);

        LocalsSnapshot snapshot = new LocalsSnapshot(locals);
        Map<Integer, ValueInfo> values = snapshot.getValues();

        assertNotNull(values);
        assertEquals(2, values.size());
        assertTrue(values.containsKey(0));
        assertTrue(values.containsKey(2));
    }

    @Test
    void testEmptyLocals() {
        ConcreteLocals locals = new ConcreteLocals(5);
        LocalsSnapshot snapshot = new LocalsSnapshot(locals);

        assertEquals(0, snapshot.size());
        assertTrue(snapshot.getValues().isEmpty());
    }

    @Test
    void testNullLocalsThrows() {
        assertThrows(IllegalArgumentException.class, () -> new LocalsSnapshot(null));
    }

    @Test
    void testToString() {
        ConcreteLocals locals = new ConcreteLocals(3);
        locals.setInt(0, 42);

        LocalsSnapshot snapshot = new LocalsSnapshot(locals);
        String str = snapshot.toString();

        assertTrue(str.contains("LocalsSnapshot"));
        assertTrue(str.contains("size=1"));
    }

    @Test
    void testWideValue() {
        ConcreteLocals locals = new ConcreteLocals(5);
        locals.setLong(0, 123456789L);

        LocalsSnapshot snapshot = new LocalsSnapshot(locals);
        assertEquals(1, snapshot.size());

        ValueInfo value = snapshot.get(0);
        assertNotNull(value);
        assertEquals("LONG", value.getType());
        assertEquals(123456789L, value.getRawValue());
    }
}
