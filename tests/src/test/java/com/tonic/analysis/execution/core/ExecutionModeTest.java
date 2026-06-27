package com.tonic.analysis.execution.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionModeTest {

    @Test
    void testEnumValues() {
        ExecutionMode[] modes = ExecutionMode.values();
        assertEquals(2, modes.length);
    }

    @Test
    void testRecursiveMode() {
        ExecutionMode mode = ExecutionMode.RECURSIVE;
        assertNotNull(mode);
        assertEquals("RECURSIVE", mode.name());
    }

    @Test
    void testDelegatedMode() {
        ExecutionMode mode = ExecutionMode.DELEGATED;
        assertNotNull(mode);
        assertEquals("DELEGATED", mode.name());
    }

    @Test
    void testValueOf() {
        assertEquals(ExecutionMode.RECURSIVE, ExecutionMode.valueOf("RECURSIVE"));
        assertEquals(ExecutionMode.DELEGATED, ExecutionMode.valueOf("DELEGATED"));
    }

    @Test
    void testEnumOrdering() {
        ExecutionMode[] modes = ExecutionMode.values();
        assertEquals(ExecutionMode.RECURSIVE, modes[0]);
        assertEquals(ExecutionMode.DELEGATED, modes[1]);
    }
}
