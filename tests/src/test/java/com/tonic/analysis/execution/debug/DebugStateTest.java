package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebugStateTest {

    @Test
    void testBuilderCreatesValidState() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.RUNNING)
                .currentMethod("Test.method()V")
                .currentPC(10)
                .currentLine(42)
                .callDepth(3)
                .instructionCount(100)
                .build();

        assertNotNull(state);
        assertEquals(DebugState.Status.RUNNING, state.getStatus());
        assertEquals("Test.method()V", state.getCurrentMethod());
        assertEquals(10, state.getCurrentPC());
        assertEquals(42, state.getCurrentLine());
        assertEquals(3, state.getCallDepth());
        assertEquals(100, state.getInstructionCount());
    }

    @Test
    void testAllAccessorsWork() {
        Breakpoint bp = new Breakpoint("com/test/Test", "method", "()V", 10);
        ConcreteLocals locals = new ConcreteLocals(5);
        locals.setInt(0, 42);
        LocalsSnapshot localsSnapshot = new LocalsSnapshot(locals);

        ConcreteStack stack = new ConcreteStack(5);
        stack.pushInt(100);
        StackSnapshot stackSnapshot = new StackSnapshot(stack);

        List<StackFrameInfo> callStack = new ArrayList<>();

        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.PAUSED)
                .currentMethod("Test.method()V")
                .currentPC(20)
                .currentLine(50)
                .callDepth(2)
                .instructionCount(200)
                .hitBreakpoint(bp)
                .callStack(callStack)
                .locals(localsSnapshot)
                .operandStack(stackSnapshot)
                .build();

        assertEquals(DebugState.Status.PAUSED, state.getStatus());
        assertEquals("Test.method()V", state.getCurrentMethod());
        assertEquals(20, state.getCurrentPC());
        assertEquals(50, state.getCurrentLine());
        assertEquals(2, state.getCallDepth());
        assertEquals(200, state.getInstructionCount());
        assertEquals(bp, state.getHitBreakpoint());
        assertNotNull(state.getCallStack());
        assertEquals(localsSnapshot, state.getLocals());
        assertEquals(stackSnapshot, state.getOperandStack());
    }

    @Test
    void testIsPausedWhenStatusPaused() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.PAUSED)
                .build();

        assertTrue(state.isPaused());
        assertFalse(state.isRunning());
        assertFalse(state.isFinished());
    }

    @Test
    void testIsRunningWhenStatusRunning() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.RUNNING)
                .build();

        assertTrue(state.isRunning());
        assertFalse(state.isPaused());
        assertFalse(state.isFinished());
    }

    @Test
    void testIsRunningWhenStatusStepping() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.STEPPING)
                .build();

        assertTrue(state.isRunning());
        assertFalse(state.isPaused());
        assertFalse(state.isFinished());
    }

    @Test
    void testIsFinishedWhenStatusCompleted() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.COMPLETED)
                .build();

        assertTrue(state.isFinished());
        assertFalse(state.isRunning());
        assertFalse(state.isPaused());
    }

    @Test
    void testIsFinishedWhenStatusException() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.EXCEPTION)
                .build();

        assertTrue(state.isFinished());
        assertFalse(state.isRunning());
        assertFalse(state.isPaused());
    }

    @Test
    void testIsFinishedWhenStatusAborted() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.ABORTED)
                .build();

        assertTrue(state.isFinished());
        assertFalse(state.isRunning());
        assertFalse(state.isPaused());
    }

    @Test
    void testIsAtBreakpointWhenBreakpointPresent() {
        Breakpoint bp = new Breakpoint("com/test/Test", "method", "()V", 10);
        DebugState state = new DebugState.Builder()
                .hitBreakpoint(bp)
                .build();

        assertTrue(state.isAtBreakpoint());
    }

    @Test
    void testIsAtBreakpointWhenBreakpointAbsent() {
        DebugState state = new DebugState.Builder().build();
        assertFalse(state.isAtBreakpoint());
    }

    @Test
    void testBuilderDefaults() {
        DebugState state = new DebugState.Builder().build();

        assertEquals(DebugState.Status.IDLE, state.getStatus());
        assertNull(state.getCurrentMethod());
        assertEquals(0, state.getCurrentPC());
        assertEquals(-1, state.getCurrentLine());
        assertEquals(0, state.getCallDepth());
        assertEquals(0, state.getInstructionCount());
        assertNull(state.getHitBreakpoint());
        assertNotNull(state.getCallStack());
        assertTrue(state.getCallStack().isEmpty());
    }

    @Test
    void testImmutability() {
        List<StackFrameInfo> originalList = new ArrayList<>();
        DebugState state = new DebugState.Builder()
                .callStack(originalList)
                .build();

        List<StackFrameInfo> returnedList = state.getCallStack();
        assertThrows(UnsupportedOperationException.class, () -> returnedList.add(null));
    }

    @Test
    void testToString() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.RUNNING)
                .currentMethod("Test.method()V")
                .currentPC(10)
                .currentLine(42)
                .callDepth(2)
                .instructionCount(100)
                .build();

        String str = state.toString();
        assertTrue(str.contains("RUNNING"));
        assertTrue(str.contains("Test.method()V"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("42"));
    }

    @Test
    void testBuilderChaining() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.IDLE)
                .currentMethod("method")
                .currentPC(5)
                .currentLine(10)
                .callDepth(1)
                .instructionCount(50)
                .build();

        assertNotNull(state);
    }

    @Test
    void testStatusIdle() {
        DebugState state = new DebugState.Builder()
                .status(DebugState.Status.IDLE)
                .build();

        assertFalse(state.isPaused());
        assertFalse(state.isRunning());
        assertFalse(state.isFinished());
    }

    @Test
    void testNullCallStackBecomesEmpty() {
        DebugState state = new DebugState.Builder()
                .callStack(null)
                .build();

        assertNotNull(state.getCallStack());
        assertTrue(state.getCallStack().isEmpty());
    }

    @Test
    void testNegativeLineNumber() {
        DebugState state = new DebugState.Builder()
                .currentLine(-1)
                .build();

        assertEquals(-1, state.getCurrentLine());
    }

    @Test
    void testZeroInstructionCount() {
        DebugState state = new DebugState.Builder()
                .instructionCount(0)
                .build();

        assertEquals(0, state.getInstructionCount());
    }

    @Test
    void testLargeInstructionCount() {
        DebugState state = new DebugState.Builder()
                .instructionCount(Long.MAX_VALUE)
                .build();

        assertEquals(Long.MAX_VALUE, state.getInstructionCount());
    }
}
