package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.state.ConcreteStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StackSnapshotTest {

    @Test
    void testConstructionFromConcreteStack() {
        ConcreteStack stack = new ConcreteStack(5);
        stack.pushInt(42);
        stack.pushInt(100);

        StackSnapshot snapshot = new StackSnapshot(stack);
        assertNotNull(snapshot);
        assertEquals(2, snapshot.depth());
    }

    @Test
    void testDepthMatches() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(1);
        stack.pushInt(2);
        stack.pushInt(3);

        StackSnapshot snapshot = new StackSnapshot(stack);
        assertEquals(3, snapshot.depth());
    }

    @Test
    void testValuesInCorrectOrder() {
        ConcreteStack stack = new ConcreteStack(5);
        stack.pushInt(10);
        stack.pushInt(20);
        stack.pushInt(30);

        StackSnapshot snapshot = new StackSnapshot(stack);
        List<ValueInfo> values = snapshot.getValues();

        assertEquals(3, values.size());
        assertEquals(10, values.get(0).getRawValue());
        assertEquals(20, values.get(1).getRawValue());
        assertEquals(30, values.get(2).getRawValue());
    }

    @Test
    void testEmptyStackHandling() {
        ConcreteStack stack = new ConcreteStack(5);
        StackSnapshot snapshot = new StackSnapshot(stack);

        assertEquals(0, snapshot.depth());
        assertTrue(snapshot.getValues().isEmpty());
    }

    @Test
    void testGetValues() {
        ConcreteStack stack = new ConcreteStack(3);
        stack.pushInt(42);
        stack.pushInt(100);

        StackSnapshot snapshot = new StackSnapshot(stack);
        List<ValueInfo> values = snapshot.getValues();

        assertNotNull(values);
        assertEquals(2, values.size());
        assertEquals("INT", values.get(0).getType());
        assertEquals("INT", values.get(1).getType());
    }

    @Test
    void testNullStackThrows() {
        assertThrows(IllegalArgumentException.class, () -> new StackSnapshot(null));
    }

    @Test
    void testToString() {
        ConcreteStack stack = new ConcreteStack(5);
        stack.pushInt(42);

        StackSnapshot snapshot = new StackSnapshot(stack);
        String str = snapshot.toString();

        assertTrue(str.contains("StackSnapshot"));
        assertTrue(str.contains("depth=1"));
    }

    @Test
    void testMixedTypes() {
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);
        stack.pushLong(100L);
        stack.pushFloat(3.14f);

        StackSnapshot snapshot = new StackSnapshot(stack);
        List<ValueInfo> values = snapshot.getValues();

        assertEquals(3, values.size());
        assertEquals("INT", values.get(0).getType());
        assertEquals("LONG", values.get(1).getType());
        assertEquals("FLOAT", values.get(2).getType());
    }

    @Test
    void testSingleValue() {
        ConcreteStack stack = new ConcreteStack(5);
        stack.pushInt(999);

        StackSnapshot snapshot = new StackSnapshot(stack);
        assertEquals(1, snapshot.depth());
        assertEquals(999, snapshot.getValues().get(0).getRawValue());
    }
}
