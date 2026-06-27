package com.tonic.analysis.simulation;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.core.StateSnapshot;
import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.ssa.type.PrimitiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SimulationState.
 */
class SimulationStateTest {

    @Test
    void testEmpty() {
        SimulationState state = SimulationState.empty();
        assertEquals(0, state.stackDepth());
        assertNull(state.getCurrentBlock());
        assertEquals(0, state.getInstructionIndex());
        assertEquals(0, state.getCallDepth());
    }

    @Test
    void testOf() {
        StackState stack = StackState.empty();
        LocalState locals = LocalState.empty();

        SimulationState state = SimulationState.of(stack, locals);

        assertNotNull(state.getStack());
        assertNotNull(state.getLocals());
    }

    @Test
    void testPush() {
        SimulationState state = SimulationState.empty();
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        SimulationState newState = state.push(value);

        assertEquals(1, newState.stackDepth());
        assertEquals(value, newState.peek());
        // Original unchanged
        assertEquals(0, state.stackDepth());
    }

    @Test
    void testPushWide() {
        SimulationState state = SimulationState.empty();
        SimValue value = SimValue.ofType(PrimitiveType.LONG, null);

        SimulationState newState = state.pushWide(value);

        assertEquals(2, newState.stackDepth());
        // peek() returns the wide second slot marker (top of stack)
        assertTrue(newState.peek().isWideSecondSlot());
        // peekValue() skips second slot and returns actual value
        assertEquals(value, newState.peekValue());
    }

    @Test
    void testPop() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        SimulationState state = SimulationState.empty()
            .push(v1)
            .push(v2);

        SimulationState popped = state.pop();

        assertEquals(1, popped.stackDepth());
        assertEquals(v1, popped.peek());
    }

    @Test
    void testPopMultiple() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);
        SimValue v3 = SimValue.constant(3, PrimitiveType.INT, null);

        SimulationState state = SimulationState.empty()
            .push(v1)
            .push(v2)
            .push(v3);

        SimulationState popped = state.pop(2);
        assertEquals(1, popped.stackDepth());
    }

    @Test
    void testPopWide() {
        SimValue value = SimValue.ofType(PrimitiveType.DOUBLE, null);

        SimulationState state = SimulationState.empty().pushWide(value);
        SimulationState popped = state.popWide();

        assertEquals(0, popped.stackDepth());
    }

    @Test
    void testPeekAtDepth() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        SimulationState state = SimulationState.empty()
            .push(v1)
            .push(v2);

        assertEquals(v2, state.peek(0));
        assertEquals(v1, state.peek(1));
    }

    @Test
    void testSetLocal() {
        SimulationState state = SimulationState.empty();
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        SimulationState newState = state.setLocal(0, value);

        assertEquals(value, newState.getLocal(0));
        assertTrue(newState.hasLocal(0));
    }

    @Test
    void testSetLocalWide() {
        SimulationState state = SimulationState.empty();
        SimValue value = SimValue.ofType(PrimitiveType.LONG, null);

        SimulationState newState = state.setLocalWide(0, value);

        assertEquals(value, newState.getLocal(0));
        assertTrue(newState.hasLocal(0));
        assertTrue(newState.hasLocal(1));
    }

    @Test
    void testDup() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        SimulationState state = SimulationState.empty().push(value);
        SimulationState duped = state.dup();

        assertEquals(2, duped.stackDepth());
    }

    @Test
    void testSwap() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        SimulationState state = SimulationState.empty().push(v1).push(v2);
        SimulationState swapped = state.swap();

        assertEquals(v1, swapped.peek(0));
        assertEquals(v2, swapped.peek(1));
    }

    @Test
    void testClearStack() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);

        SimulationState state = SimulationState.empty()
            .push(value)
            .push(value);

        SimulationState cleared = state.clearStack();

        assertEquals(0, cleared.stackDepth());
    }

    @Test
    void testAtInstruction() {
        SimulationState state = SimulationState.empty();

        SimulationState newState = state.atInstruction(5);

        assertEquals(5, newState.getInstructionIndex());
    }

    @Test
    void testNextInstruction() {
        SimulationState state = SimulationState.empty().atInstruction(3);

        SimulationState next = state.nextInstruction();

        assertEquals(4, next.getInstructionIndex());
    }

    @Test
    void testEnterCall() {
        SimulationState state = SimulationState.empty()
            .push(SimValue.unknown(null));

        SimulationState called = state.enterCall();

        assertEquals(0, called.stackDepth()); // Fresh stack
        assertEquals(1, called.getCallDepth());
    }

    @Test
    void testMerge() {
        SimValue v1 = SimValue.constant(1, PrimitiveType.INT, null);
        SimValue v2 = SimValue.constant(2, PrimitiveType.INT, null);

        SimulationState s1 = SimulationState.empty().push(v1);
        SimulationState s2 = SimulationState.empty().push(v2);

        SimulationState merged = s1.merge(s2);

        assertEquals(1, merged.stackDepth());
    }

    @Test
    void testSnapshot() {
        SimValue value = SimValue.constant(42, PrimitiveType.INT, null);
        SimulationState state = SimulationState.empty().push(value);

        StateSnapshot snapshot = state.snapshot();

        assertEquals(1, snapshot.getStackDepth());
        assertEquals(value, snapshot.getTopOfStack());
    }

    @Test
    void testIsAtBlockStart() {
        SimulationState state = SimulationState.empty();
        assertTrue(state.isAtBlockStart());

        SimulationState advanced = state.atInstruction(1);
        assertFalse(advanced.isAtBlockStart());
    }

    @Test
    void testMaxStackDepth() {
        SimulationState state = SimulationState.empty()
            .push(SimValue.unknown(null))
            .push(SimValue.unknown(null))
            .push(SimValue.unknown(null))
            .pop();

        assertEquals(2, state.stackDepth());
        assertEquals(3, state.maxStackDepth());
    }
}
