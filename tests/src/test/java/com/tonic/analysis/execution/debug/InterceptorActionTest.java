package com.tonic.analysis.execution.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterceptorActionTest {

    @Test
    void testAllValuesExist() {
        InterceptorAction[] values = InterceptorAction.values();
        assertNotNull(values);
        assertTrue(values.length > 0);
    }

    @Test
    void testContinueExists() {
        InterceptorAction action = InterceptorAction.CONTINUE;
        assertNotNull(action);
        assertEquals("CONTINUE", action.name());
    }

    @Test
    void testPauseExists() {
        InterceptorAction action = InterceptorAction.PAUSE;
        assertNotNull(action);
        assertEquals("PAUSE", action.name());
    }

    @Test
    void testAbortExists() {
        InterceptorAction action = InterceptorAction.ABORT;
        assertNotNull(action);
        assertEquals("ABORT", action.name());
    }

    @Test
    void testCorrectValueCount() {
        InterceptorAction[] values = InterceptorAction.values();
        assertEquals(3, values.length);
    }
}
