package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractInterceptorTest {

    private TestInterceptor interceptor;
    private StackFrame mockFrame;
    private Instruction mockInstruction;

    static class TestInterceptor extends AbstractInterceptor {
    }

    @BeforeEach
    void setUp() {
        interceptor = new TestInterceptor();
        mockFrame = mock(StackFrame.class);
        mockInstruction = mock(Instruction.class);
    }

    @Test
    void testBeforeInstructionReturnsContinue() {
        InterceptorAction action = interceptor.beforeInstruction(mockFrame, mockInstruction);
        assertEquals(InterceptorAction.CONTINUE, action);
    }

    @Test
    void testAfterInstructionDoesNotThrow() {
        assertDoesNotThrow(() -> interceptor.afterInstruction(mockFrame, mockInstruction));
    }

    @Test
    void testOnMethodEntryReturnsContinue() {
        InterceptorAction action = interceptor.onMethodEntry(mockFrame);
        assertEquals(InterceptorAction.CONTINUE, action);
    }

    @Test
    void testOnMethodExitDoesNotThrow() {
        ConcreteValue returnValue = ConcreteValue.intValue(42);
        assertDoesNotThrow(() -> interceptor.onMethodExit(mockFrame, returnValue));
    }

    @Test
    void testOnMethodExitWithNullDoesNotThrow() {
        assertDoesNotThrow(() -> interceptor.onMethodExit(mockFrame, null));
    }

    @Test
    void testOnExceptionReturnsContinue() {
        ObjectInstance exception = new ObjectInstance(1, "java/lang/Exception");
        InterceptorAction action = interceptor.onException(mockFrame, exception);
        assertEquals(InterceptorAction.CONTINUE, action);
    }

    @Test
    void testCanExtendAndOverrideBeforeInstruction() {
        AbstractInterceptor custom = new AbstractInterceptor() {
            @Override
            public InterceptorAction beforeInstruction(StackFrame frame, Instruction instruction) {
                return InterceptorAction.PAUSE;
            }
        };

        InterceptorAction action = custom.beforeInstruction(mockFrame, mockInstruction);
        assertEquals(InterceptorAction.PAUSE, action);
    }

    @Test
    void testCanExtendAndOverrideOnMethodEntry() {
        AbstractInterceptor custom = new AbstractInterceptor() {
            @Override
            public InterceptorAction onMethodEntry(StackFrame frame) {
                return InterceptorAction.ABORT;
            }
        };

        InterceptorAction action = custom.onMethodEntry(mockFrame);
        assertEquals(InterceptorAction.ABORT, action);
    }

    @Test
    void testCanExtendAndOverrideAfterInstruction() {
        final boolean[] called = {false};
        AbstractInterceptor custom = new AbstractInterceptor() {
            @Override
            public void afterInstruction(StackFrame frame, Instruction instruction) {
                called[0] = true;
            }
        };

        custom.afterInstruction(mockFrame, mockInstruction);
        assertTrue(called[0]);
    }

    @Test
    void testImplementsInterface() {
        assertTrue(interceptor instanceof InstructionInterceptor);
    }
}
