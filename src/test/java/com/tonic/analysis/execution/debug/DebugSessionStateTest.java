package com.tonic.analysis.execution.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DebugSessionStateTest {

    @Test
    void testAllStatesExist() {
        assertNotNull(DebugSessionState.IDLE);
        assertNotNull(DebugSessionState.RUNNING);
        assertNotNull(DebugSessionState.PAUSED);
        assertNotNull(DebugSessionState.STOPPED);
    }

    @Test
    void testCorrectStateCount() {
        DebugSessionState[] states = DebugSessionState.values();
        assertEquals(4, states.length);
    }

    @Test
    void testValueOfIdle() {
        assertEquals(DebugSessionState.IDLE, DebugSessionState.valueOf("IDLE"));
    }

    @Test
    void testValueOfRunning() {
        assertEquals(DebugSessionState.RUNNING, DebugSessionState.valueOf("RUNNING"));
    }

    @Test
    void testValueOfPaused() {
        assertEquals(DebugSessionState.PAUSED, DebugSessionState.valueOf("PAUSED"));
    }

    @Test
    void testValueOfStopped() {
        assertEquals(DebugSessionState.STOPPED, DebugSessionState.valueOf("STOPPED"));
    }

    @Test
    void testEnumOrdinals() {
        assertEquals(0, DebugSessionState.IDLE.ordinal());
        assertEquals(1, DebugSessionState.RUNNING.ordinal());
        assertEquals(2, DebugSessionState.PAUSED.ordinal());
        assertEquals(3, DebugSessionState.STOPPED.ordinal());
    }
}
