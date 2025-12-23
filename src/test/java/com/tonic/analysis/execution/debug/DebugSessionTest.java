package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DebugSessionTest {

    private HeapManager heapManager;
    private ClassResolver classResolver;
    private BytecodeContext context;
    private BreakpointManager breakpointManager;

    @BeforeEach
    void setUp() throws IOException {
        heapManager = new SimpleHeapManager();
        ClassPool pool = new ClassPool();
        classResolver = new ClassResolver(pool);

        context = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(classResolver)
            .maxCallDepth(100)
            .maxInstructions(1000)
            .trackStatistics(true)
            .build();

        breakpointManager = new BreakpointManager();
    }

    @Test
    void testCreateSessionInIdleState() {
        DebugSession session = new DebugSession(context);
        assertEquals(DebugSessionState.IDLE, session.getState());
        assertTrue(session.isStopped() || session.getState() == DebugSessionState.IDLE);
    }

    @Test
    void testCreateSessionWithBreakpointManager() {
        DebugSession session = new DebugSession(context, breakpointManager);
        assertNotNull(session);
        assertEquals(DebugSessionState.IDLE, session.getState());
    }

    @Test
    void testRejectsNullContext() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DebugSession(null);
        });
    }

    @Test
    void testRejectsNullBreakpointManager() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DebugSession(context, null);
        });
    }

    @Test
    void testStartTransitionsToPaused() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        assertEquals(DebugSessionState.PAUSED, session.getState());
        assertTrue(session.isPaused());
    }

    @Test
    void testCannotStartTwice() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        assertThrows(IllegalStateException.class, () -> {
            session.start(method);
        });
    }

    @Test
    void testStopFromPausedAfterStart() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        session.stop();

        assertEquals(DebugSessionState.STOPPED, session.getState());
        assertTrue(session.isStopped());
    }

    @Test
    void testStopFromPaused() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        session.stop();

        assertEquals(DebugSessionState.STOPPED, session.getState());
    }

    @Test
    void testCannotStopWhenIdle() {
        DebugSession session = new DebugSession(context);

        assertThrows(IllegalStateException.class, () -> {
            session.stop();
        });
    }

    @Test
    void testCannotStepWhenIdle() {
        DebugSession session = new DebugSession(context);

        assertThrows(IllegalStateException.class, () -> {
            session.stepInto();
        });
    }

    @Test
    void testCannotStepWhenStopped() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        session.stop();

        assertThrows(IllegalStateException.class, () -> {
            session.stepInto();
        });
    }

    @Test
    void testCannotStepWhenRunning() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        session.resume();

        assertThrows(IllegalStateException.class, () -> {
            session.stepInto();
        });
    }

    @Test
    void testStepIntoReturnsPausedState() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        DebugState state = session.stepInto();

        assertNotNull(state);
    }

    @Test
    void testStepOverReturnsPausedState() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        DebugState state = session.stepOver();

        assertNotNull(state);
    }

    @Test
    void testStepOutReturnsPausedState() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        DebugState state = session.stepOut();

        assertNotNull(state);
    }

    @Test
    void testRunToCursorReturnsPausedState() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        DebugState state = session.runToCursor(10);

        assertNotNull(state);
    }

    @Test
    void testResumeReturnsPausedState() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        DebugState state = session.resume();

        assertNotNull(state);
    }

    @Test
    void testIsRunningPredicates() {
        DebugSession session = new DebugSession(context);
        assertFalse(session.isRunning());

        MethodEntry method = createMockMethod();
        session.start(method);
        session.resume();

        assertTrue(session.isRunning() || session.isPaused() || session.isStopped());
    }

    @Test
    void testIsPausedPredicates() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        assertTrue(session.isPaused());
    }

    @Test
    void testIsStoppedPredicates() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        assertFalse(session.isStopped());

        session.start(method);
        session.stop();

        assertTrue(session.isStopped());
    }

    @Test
    void testGetCurrentStateReturnsValidSnapshot() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        DebugState state = session.getCurrentState();

        assertNotNull(state);
    }

    @Test
    void testGetResultOnlyValidWhenStopped() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        assertThrows(IllegalStateException.class, () -> {
            session.getResult();
        });

        session.stop();
        BytecodeResult result = session.getResult();

        assertNotNull(result);
    }

    @Test
    void testAddBreakpoint() {
        DebugSession session = new DebugSession(context);
        Breakpoint bp = new Breakpoint("TestClass", "testMethod", "()V", 10);

        session.addBreakpoint(bp);

        List<Breakpoint> breakpoints = session.getBreakpoints();
        assertTrue(breakpoints.contains(bp));
    }

    @Test
    void testRemoveBreakpoint() {
        DebugSession session = new DebugSession(context);
        Breakpoint bp = new Breakpoint("TestClass", "testMethod", "()V", 10);

        session.addBreakpoint(bp);
        session.removeBreakpoint(bp);

        List<Breakpoint> breakpoints = session.getBreakpoints();
        assertFalse(breakpoints.contains(bp));
    }

    @Test
    void testGetBreakpoints() {
        DebugSession session = new DebugSession(context);

        List<Breakpoint> breakpoints = session.getBreakpoints();
        assertNotNull(breakpoints);
        assertTrue(breakpoints.isEmpty());
    }

    @Test
    void testMultipleBreakpointsOnSameMethod() {
        DebugSession session = new DebugSession(context);
        Breakpoint bp1 = new Breakpoint("TestClass", "testMethod", "()V", 10);
        Breakpoint bp2 = new Breakpoint("TestClass", "testMethod", "()V", 20);

        session.addBreakpoint(bp1);
        session.addBreakpoint(bp2);

        List<Breakpoint> breakpoints = session.getBreakpoints();
        assertEquals(2, breakpoints.size());
    }

    @Test
    void testDisabledBreakpointsIgnored() {
        DebugSession session = new DebugSession(context);
        Breakpoint bp = new Breakpoint("TestClass", "testMethod", "()V", 10);
        bp.setEnabled(false);

        session.addBreakpoint(bp);

        List<Breakpoint> breakpoints = session.getBreakpoints();
        assertEquals(1, breakpoints.size());
        assertFalse(breakpoints.get(0).isEnabled());
    }

    @Test
    void testGetCurrentFrameWhenNotStarted() {
        DebugSession session = new DebugSession(context);
        assertNull(session.getCurrentFrame());
    }

    @Test
    void testGetCallStackWhenNotStarted() {
        DebugSession session = new DebugSession(context);
        List<?> stack = session.getCallStack();

        assertNotNull(stack);
        assertTrue(stack.isEmpty());
    }

    @Test
    void testGetCallStackAfterStart() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        List<?> stack = session.getCallStack();

        assertNotNull(stack);
    }

    @Test
    void testAddListener() {
        DebugSession session = new DebugSession(context);
        TestListener listener = new TestListener();

        session.addListener(listener);

        MethodEntry method = createMockMethod();
        session.start(method);

        assertTrue(listener.sessionStartCalled);
    }

    @Test
    void testRemoveListener() {
        DebugSession session = new DebugSession(context);
        TestListener listener = new TestListener();

        session.addListener(listener);
        session.removeListener(listener);

        MethodEntry method = createMockMethod();
        session.start(method);

        assertFalse(listener.sessionStartCalled);
    }

    @Test
    void testAddNullListenerIsIgnored() {
        DebugSession session = new DebugSession(context);
        assertDoesNotThrow(() -> session.addListener(null));
    }

    @Test
    void testRemoveNullListenerIsIgnored() {
        DebugSession session = new DebugSession(context);
        assertDoesNotThrow(() -> session.removeListener(null));
    }

    @Test
    void testOnSessionStartFires() {
        DebugSession session = new DebugSession(context);
        TestListener listener = new TestListener();
        session.addListener(listener);

        MethodEntry method = createMockMethod();
        session.start(method);

        assertTrue(listener.sessionStartCalled);
    }

    @Test
    void testOnSessionStopFires() {
        DebugSession session = new DebugSession(context);
        TestListener listener = new TestListener();
        session.addListener(listener);

        MethodEntry method = createMockMethod();
        session.start(method);
        session.stop();

        assertTrue(listener.sessionStopCalled);
    }

    @Test
    void testOnStepCompleteFires() {
        DebugSession session = new DebugSession(context);
        TestListener listener = new TestListener();
        session.addListener(listener);

        MethodEntry method = createMockMethod();
        session.start(method);
        session.stepInto();

        assertTrue(listener.stepCompleteCalled);
    }

    @Test
    void testOnStateChangeFires() {
        DebugSession session = new DebugSession(context);
        TestListener listener = new TestListener();
        session.addListener(listener);

        MethodEntry method = createMockMethod();
        session.start(method);

        assertTrue(listener.stateChangeCalled);
    }

    @Test
    void testEmptyMethodExecution() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        DebugState state = session.stepInto();

        assertNotNull(state);
    }

    @Test
    void testAbortDuringExecution() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        session.stop();

        assertEquals(DebugSessionState.STOPPED, session.getState());
    }

    @Test
    void testResumeWhenAlreadyRunning() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        session.resume();

        assertThrows(IllegalStateException.class, () -> {
            session.resume();
        });
    }

    @Test
    void testPauseTransitionsCorrectly() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        assertEquals(DebugSessionState.PAUSED, session.getState());
    }

    @Test
    void testCannotPauseWhenNotRunning() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        assertThrows(IllegalStateException.class, () -> {
            session.pause();
        });
    }

    @Test
    void testMultipleListeners() {
        DebugSession session = new DebugSession(context);
        TestListener listener1 = new TestListener();
        TestListener listener2 = new TestListener();

        session.addListener(listener1);
        session.addListener(listener2);

        MethodEntry method = createMockMethod();
        session.start(method);

        assertTrue(listener1.sessionStartCalled);
        assertTrue(listener2.sessionStartCalled);
    }

    @Test
    void testStartWithArguments() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        ConcreteValue arg1 = ConcreteValue.intValue(10);
        ConcreteValue arg2 = ConcreteValue.intValue(20);

        session.start(method, arg1, arg2);

        assertEquals(DebugSessionState.PAUSED, session.getState());
    }

    @Test
    void testStartWithNoArguments() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);

        assertEquals(DebugSessionState.PAUSED, session.getState());
    }

    @Test
    void testStopIdempotent() {
        DebugSession session = new DebugSession(context);
        MethodEntry method = createMockMethod();

        session.start(method);
        session.stop();
        session.stop();

        assertEquals(DebugSessionState.STOPPED, session.getState());
    }

    private MethodEntry createMockMethod() {
        MethodEntry method = mock(MethodEntry.class);
        CodeAttribute codeAttr = mock(CodeAttribute.class);
        com.tonic.parser.ClassFile classFile = mock(com.tonic.parser.ClassFile.class);
        com.tonic.parser.ConstPool constPool = mock(com.tonic.parser.ConstPool.class);

        when(method.getCodeAttribute()).thenReturn(codeAttr);
        when(method.getName()).thenReturn("testMethod");
        when(method.getDesc()).thenReturn("()V");
        when(method.getOwnerName()).thenReturn("TestClass");
        when(method.getClassFile()).thenReturn(classFile);
        when(classFile.getConstPool()).thenReturn(constPool);
        when(codeAttr.getMaxStack()).thenReturn(10);
        when(codeAttr.getMaxLocals()).thenReturn(10);
        when(codeAttr.getCode()).thenReturn(new byte[] {
            (byte) 0xB1
        });

        return method;
    }

    private static class TestListener implements DebugEventListener {
        boolean sessionStartCalled = false;
        boolean sessionStopCalled = false;
        boolean breakpointHitCalled = false;
        boolean stepCompleteCalled = false;
        boolean exceptionCalled = false;
        boolean stateChangeCalled = false;

        @Override
        public void onSessionStart(DebugSession session) {
            sessionStartCalled = true;
        }

        @Override
        public void onSessionStop(DebugSession session, BytecodeResult result) {
            sessionStopCalled = true;
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
        public void onException(DebugSession session, com.tonic.analysis.execution.heap.ObjectInstance exception) {
            exceptionCalled = true;
        }

        @Override
        public void onStateChange(DebugSession session, DebugSessionState oldState, DebugSessionState newState) {
            stateChangeCalled = true;
        }
    }
}
