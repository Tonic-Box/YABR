package com.tonic.analysis.simulation;

import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.ssa.type.PrimitiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StackState.
 */
class StackStateTest {

    @Test
    void testEmptyStack() {
        StackState stack = StackState.empty();
        assertEquals(0, stack.depth());
        assertTrue(stack.isEmpty());
        // peek() throws on empty stack
        assertThrows(IllegalStateException.class, stack::peek);
    }

    @Test
    void testPush() {
        StackState stack = StackState.empty();
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        StackState newStack = stack.push(value);

        assertEquals(1, newStack.depth());
        assertEquals(value, newStack.peek());
        // Original stack unchanged (immutability)
        assertEquals(0, stack.depth());
    }

    @Test
    void testPop() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        StackState stack = StackState.empty().push(v1).push(v2);
        assertEquals(2, stack.depth());

        StackState popped = stack.pop();
        assertEquals(1, popped.depth());
        assertEquals(v1, popped.peek());
    }

    @Test
    void testPopMultiple() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);
        SimValue v3 = SimValue.constant(3, PrimitiveType.INT, null);

        StackState stack = StackState.empty().push(v1).push(v2).push(v3);

        StackState popped = stack.pop(2);
        assertEquals(1, popped.depth());
        assertEquals(v1, popped.peek());
    }

    @Test
    void testPeekAtDepth() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);
        SimValue v3 = SimValue.constant(3, PrimitiveType.INT, null);

        StackState stack = StackState.empty().push(v1).push(v2).push(v3);

        assertEquals(v3, stack.peek(0)); // Top
        assertEquals(v2, stack.peek(1)); // Second
        assertEquals(v1, stack.peek(2)); // Third (bottom)
    }

    @Test
    void testPushWide() {
        SimValue value = SimValue.ofType(PrimitiveType.LONG, null);

        StackState stack = StackState.empty().pushWide(value);

        assertEquals(2, stack.depth()); // Wide takes 2 slots
        // peek() returns the wide second slot marker (top of stack)
        assertTrue(stack.peek().isWideSecondSlot());
        // peekValue() skips second slot and returns actual value
        assertEquals(value, stack.peekValue());
    }

    @Test
    void testPopWide() {
        SimValue value = SimValue.ofType(PrimitiveType.LONG, null);

        StackState stack = StackState.empty().pushWide(value);
        StackState popped = stack.popWide();

        assertTrue(popped.isEmpty());
    }

    @Test
    void testDup() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        StackState stack = StackState.empty().push(value);
        StackState duped = stack.dup();

        assertEquals(2, duped.depth());
        assertEquals(value, duped.peek(0));
        assertEquals(value, duped.peek(1));
    }

    @Test
    void testSwap() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        StackState stack = StackState.empty().push(v1).push(v2);
        StackState swapped = stack.swap();

        assertEquals(v1, swapped.peek(0)); // Now on top
        assertEquals(v2, swapped.peek(1)); // Now second
    }

    @Test
    void testMaxDepth() {
        StackState stack = StackState.empty();
        stack = stack.push(SimValue.unknown(null));
        stack = stack.push(SimValue.unknown(null));
        stack = stack.push(SimValue.unknown(null));
        stack = stack.pop();

        assertEquals(2, stack.depth());
        assertEquals(3, stack.maxDepth());
    }

    @Test
    void testClear() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        StackState stack = StackState.empty().push(v1).push(v2);
        StackState cleared = stack.clear();

        assertTrue(cleared.isEmpty());
    }

    @Test
    void testMerge() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        StackState s1 = StackState.empty().push(v1);
        StackState s2 = StackState.empty().push(v2);

        StackState merged = s1.merge(s2);

        assertEquals(1, merged.depth());
        // Merged value should exist
        assertNotNull(merged.peek());
    }

    @Test
    void testGetValues() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        StackState stack = StackState.empty().push(v1).push(v2);

        var values = stack.getValues();
        assertEquals(2, values.size());
        assertEquals(v1, values.get(0)); // Bottom
        assertEquals(v2, values.get(1)); // Top
    }
}
