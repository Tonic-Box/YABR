package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.heap.ObjectInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DebugEventListenerTest {

    @Test
    void testDefaultOnSessionStartIsNoop() {
        DebugEventListener listener = new DebugEventListener() {};
        DebugSession session = mock(DebugSession.class);

        assertDoesNotThrow(() -> listener.onSessionStart(session));
    }

    @Test
    void testDefaultOnSessionStopIsNoop() {
        DebugEventListener listener = new DebugEventListener() {};
        DebugSession session = mock(DebugSession.class);
        BytecodeResult result = mock(BytecodeResult.class);

        assertDoesNotThrow(() -> listener.onSessionStop(session, result));
    }

    @Test
    void testDefaultOnBreakpointHitIsNoop() {
        DebugEventListener listener = new DebugEventListener() {};
        DebugSession session = mock(DebugSession.class);
        Breakpoint breakpoint = mock(Breakpoint.class);

        assertDoesNotThrow(() -> listener.onBreakpointHit(session, breakpoint));
    }

    @Test
    void testDefaultOnStepCompleteIsNoop() {
        DebugEventListener listener = new DebugEventListener() {};
        DebugSession session = mock(DebugSession.class);
        DebugState state = mock(DebugState.class);

        assertDoesNotThrow(() -> listener.onStepComplete(session, state));
    }

    @Test
    void testDefaultOnExceptionIsNoop() {
        DebugEventListener listener = new DebugEventListener() {};
        DebugSession session = mock(DebugSession.class);
        ObjectInstance exception = mock(ObjectInstance.class);

        assertDoesNotThrow(() -> listener.onException(session, exception));
    }

    @Test
    void testDefaultOnStateChangeIsNoop() {
        DebugEventListener listener = new DebugEventListener() {};
        DebugSession session = mock(DebugSession.class);

        assertDoesNotThrow(() -> listener.onStateChange(session, DebugSessionState.IDLE, DebugSessionState.RUNNING));
    }

    @Test
    void testCanImplementOnSessionStart() {
        TestListener listener = new TestListener();
        DebugSession session = mock(DebugSession.class);

        listener.onSessionStart(session);

        assertTrue(listener.sessionStartCalled);
    }

    @Test
    void testCanImplementOnBreakpointHit() {
        TestListener listener = new TestListener();
        DebugSession session = mock(DebugSession.class);
        Breakpoint breakpoint = mock(Breakpoint.class);

        listener.onBreakpointHit(session, breakpoint);

        assertTrue(listener.breakpointHitCalled);
    }

    @Test
    void testCanImplementOnStepComplete() {
        TestListener listener = new TestListener();
        DebugSession session = mock(DebugSession.class);
        DebugState state = mock(DebugState.class);

        listener.onStepComplete(session, state);

        assertTrue(listener.stepCompleteCalled);
    }

    @Test
    void testCanImplementOnException() {
        TestListener listener = new TestListener();
        DebugSession session = mock(DebugSession.class);
        ObjectInstance exception = mock(ObjectInstance.class);

        listener.onException(session, exception);

        assertTrue(listener.exceptionCalled);
    }

    private static class TestListener implements DebugEventListener {
        boolean sessionStartCalled = false;
        boolean breakpointHitCalled = false;
        boolean stepCompleteCalled = false;
        boolean exceptionCalled = false;

        @Override
        public void onSessionStart(DebugSession session) {
            sessionStartCalled = true;
        }

        @Override
        public void onBreakpointHit(DebugSession session, Breakpoint breakpoint) {
            breakpointHitCalled = true;
        }

        @Override
        public void onStepComplete(DebugSession session, DebugState state) {
            stepCompleteCalled = true;
        }

        @Override
        public void onException(DebugSession session, ObjectInstance exception) {
            exceptionCalled = true;
        }
    }
}
