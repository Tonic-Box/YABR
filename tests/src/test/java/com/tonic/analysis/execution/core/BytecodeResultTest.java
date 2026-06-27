package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeResultTest {

    @Test
    void testCompletedWithValue() {
        ConcreteValue value = ConcreteValue.intValue(42);
        BytecodeResult result = BytecodeResult.completed(value);

        assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        assertEquals(value, result.getReturnValue());
        assertNull(result.getException());
        assertTrue(result.isSuccess());
        assertFalse(result.hasException());
        assertEquals(0, result.getInstructionsExecuted());
        assertEquals(0, result.getExecutionTimeNanos());
        assertTrue(result.getStackTrace().isEmpty());
    }

    @Test
    void testCompletedWithNull() {
        BytecodeResult result = BytecodeResult.completed(null);

        assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        assertNull(result.getReturnValue());
        assertTrue(result.isSuccess());
    }

    @Test
    void testException() {
        ObjectInstance ex = new ObjectInstance(1, "java/lang/Exception");
        List<String> trace = Arrays.asList("line1", "line2", "line3");

        BytecodeResult result = BytecodeResult.exception(ex, trace);

        assertEquals(BytecodeResult.Status.EXCEPTION, result.getStatus());
        assertEquals(ex, result.getException());
        assertNull(result.getReturnValue());
        assertFalse(result.isSuccess());
        assertTrue(result.hasException());
        assertEquals(3, result.getStackTrace().size());
        assertEquals("line1", result.getStackTrace().get(0));
    }

    @Test
    void testExceptionRequiresNonNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            BytecodeResult.exception(null, null);
        });
    }

    @Test
    void testExceptionWithNullTrace() {
        ObjectInstance ex = new ObjectInstance(1, "java/lang/Exception");
        BytecodeResult result = BytecodeResult.exception(ex, null);

        assertTrue(result.getStackTrace().isEmpty());
    }

    @Test
    void testInterrupted() {
        BytecodeResult result = BytecodeResult.interrupted();

        assertEquals(BytecodeResult.Status.INTERRUPTED, result.getStatus());
        assertNull(result.getReturnValue());
        assertNull(result.getException());
        assertFalse(result.isSuccess());
        assertFalse(result.hasException());
    }

    @Test
    void testInstructionLimit() {
        BytecodeResult result = BytecodeResult.instructionLimit(1000000);

        assertEquals(BytecodeResult.Status.INSTRUCTION_LIMIT, result.getStatus());
        assertEquals(1000000, result.getInstructionsExecuted());
        assertFalse(result.isSuccess());
    }

    @Test
    void testDepthLimit() {
        BytecodeResult result = BytecodeResult.depthLimit(500);

        assertEquals(BytecodeResult.Status.DEPTH_LIMIT, result.getStatus());
        assertFalse(result.isSuccess());
        assertFalse(result.getStackTrace().isEmpty());
        assertTrue(result.getStackTrace().get(0).contains("500"));
    }

    @Test
    void testWithStatistics() {
        ConcreteValue value = ConcreteValue.intValue(10);
        BytecodeResult original = BytecodeResult.completed(value);

        BytecodeResult withStats = original.withStatistics(5000, 1000000);

        assertEquals(5000, withStats.getInstructionsExecuted());
        assertEquals(1000000, withStats.getExecutionTimeNanos());
        assertEquals(BytecodeResult.Status.COMPLETED, withStats.getStatus());
        assertEquals(value, withStats.getReturnValue());
    }

    @Test
    void testWithStatisticsPreservesStatus() {
        BytecodeResult interrupted = BytecodeResult.interrupted();
        BytecodeResult withStats = interrupted.withStatistics(100, 200);

        assertEquals(BytecodeResult.Status.INTERRUPTED, withStats.getStatus());
        assertEquals(100, withStats.getInstructionsExecuted());
        assertEquals(200, withStats.getExecutionTimeNanos());
    }

    @Test
    void testStackTraceIsImmutable() {
        List<String> trace = Arrays.asList("line1", "line2");
        ObjectInstance ex = new ObjectInstance(1, "java/lang/Exception");
        BytecodeResult result = BytecodeResult.exception(ex, trace);

        List<String> retrievedTrace = result.getStackTrace();
        assertThrows(UnsupportedOperationException.class, () -> {
            retrievedTrace.add("line3");
        });
    }

    @Test
    void testStackTraceModificationDoesNotAffectResult() {
        List<String> trace = new java.util.ArrayList<>(Arrays.asList("line1", "line2"));
        ObjectInstance ex = new ObjectInstance(1, "java/lang/Exception");
        BytecodeResult result = BytecodeResult.exception(ex, trace);

        trace.clear();

        assertEquals(2, result.getStackTrace().size());
    }

    @Test
    void testToStringForCompleted() {
        ConcreteValue value = ConcreteValue.intValue(42);
        BytecodeResult result = BytecodeResult.completed(value);

        String str = result.toString();
        assertTrue(str.contains("COMPLETED"));
        assertTrue(str.contains("returnValue"));
    }

    @Test
    void testToStringForException() {
        ObjectInstance ex = new ObjectInstance(1, "java/lang/Exception");
        BytecodeResult result = BytecodeResult.exception(ex, null);

        String str = result.toString();
        assertTrue(str.contains("EXCEPTION"));
        assertTrue(str.contains("exception"));
    }

    @Test
    void testToStringWithStatistics() {
        BytecodeResult result = BytecodeResult.completed(null)
            .withStatistics(1000, 5000000);

        String str = result.toString();
        assertTrue(str.contains("1000"));
        assertTrue(str.contains("5000000"));
    }

    @Test
    void testIsSuccessForAllStatuses() {
        assertTrue(BytecodeResult.completed(null).isSuccess());
        assertFalse(BytecodeResult.exception(new ObjectInstance(1, "E"), null).isSuccess());
        assertFalse(BytecodeResult.interrupted().isSuccess());
        assertFalse(BytecodeResult.instructionLimit(1).isSuccess());
        assertFalse(BytecodeResult.depthLimit(1).isSuccess());
    }

    @Test
    void testHasExceptionForAllStatuses() {
        assertFalse(BytecodeResult.completed(null).hasException());
        assertTrue(BytecodeResult.exception(new ObjectInstance(1, "E"), null).hasException());
        assertFalse(BytecodeResult.interrupted().hasException());
        assertFalse(BytecodeResult.instructionLimit(1).hasException());
        assertFalse(BytecodeResult.depthLimit(1).hasException());
    }
}
