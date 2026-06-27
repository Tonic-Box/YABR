package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractBytecodeListenerTest {

    private static class TestListener extends AbstractBytecodeListener {
    }

    private TestListener listener;

    @BeforeEach
    void setUp() {
        listener = new TestListener();
    }

    @Test
    void initialStateIsNull() {
        assertNull(listener.getCurrentMethod());
        assertEquals(0, listener.getInstructionCount());
    }

    @Test
    void onFramePushSetsCurrentMethod() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        when(frame.getMethod()).thenReturn(method);

        listener.onFramePush(frame);

        assertEquals(method, listener.getCurrentMethod());
    }

    @Test
    void afterInstructionIncrementsCount() {
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);

        assertEquals(0, listener.getInstructionCount());

        listener.afterInstruction(frame, instr);
        assertEquals(1, listener.getInstructionCount());

        listener.afterInstruction(frame, instr);
        assertEquals(2, listener.getInstructionCount());
    }

    @Test
    void resetClearsMethod() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        when(frame.getMethod()).thenReturn(method);

        listener.onFramePush(frame);
        assertNotNull(listener.getCurrentMethod());

        listener.reset();
        assertNull(listener.getCurrentMethod());
    }

    @Test
    void resetClearsInstructionCount() {
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);

        listener.afterInstruction(frame, instr);
        listener.afterInstruction(frame, instr);
        assertEquals(2, listener.getInstructionCount());

        listener.reset();
        assertEquals(0, listener.getInstructionCount());
    }

    @Test
    void getCurrentMethodTracksLastFrame() {
        StackFrame frame1 = mock(StackFrame.class);
        StackFrame frame2 = mock(StackFrame.class);
        MethodEntry method1 = mock(MethodEntry.class);
        MethodEntry method2 = mock(MethodEntry.class);

        when(frame1.getMethod()).thenReturn(method1);
        when(frame2.getMethod()).thenReturn(method2);

        listener.onFramePush(frame1);
        assertEquals(method1, listener.getCurrentMethod());

        listener.onFramePush(frame2);
        assertEquals(method2, listener.getCurrentMethod());
    }

    @Test
    void instructionCountPersistsAcrossFrames() {
        StackFrame frame1 = mock(StackFrame.class);
        StackFrame frame2 = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);

        listener.afterInstruction(frame1, instr);
        listener.afterInstruction(frame1, instr);
        assertEquals(2, listener.getInstructionCount());

        listener.onFramePush(frame2);
        listener.afterInstruction(frame2, instr);
        assertEquals(3, listener.getInstructionCount());
    }

    @Test
    void protectedAccessorsWork() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        Instruction instr = mock(Instruction.class);

        when(frame.getMethod()).thenReturn(method);

        listener.onFramePush(frame);
        listener.afterInstruction(frame, instr);

        assertSame(method, listener.getCurrentMethod());
        assertEquals(1, listener.getInstructionCount());
    }

    @Test
    void canBeSubclassed() {
        AbstractBytecodeListener custom = new AbstractBytecodeListener() {
            private int customCount;

            @Override
            public void beforeInstruction(StackFrame frame, Instruction instruction) {
                customCount++;
            }

            public int getCustomCount() {
                return customCount;
            }
        };

        assertTrue(custom instanceof BytecodeListener);
    }

    @Test
    void resetCanBeCalledMultipleTimes() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        when(frame.getMethod()).thenReturn(method);

        listener.onFramePush(frame);
        listener.reset();
        listener.reset();

        assertNull(listener.getCurrentMethod());
        assertEquals(0, listener.getInstructionCount());
    }

    @Test
    void instructionCountDoesNotOverflow() {
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);

        for (int i = 0; i < 10000; i++) {
            listener.afterInstruction(frame, instr);
        }

        assertEquals(10000, listener.getInstructionCount());
    }

    @Test
    void framePopDoesNotClearCurrentMethod() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        when(frame.getMethod()).thenReturn(method);

        listener.onFramePush(frame);
        listener.onFramePop(frame, null);

        assertEquals(method, listener.getCurrentMethod());
    }

    @Test
    void allDefaultMethodsStillWork() {
        assertDoesNotThrow(() -> {
            listener.onExecutionStart(null);
            listener.onExecutionEnd(null);
            listener.onStackPush(null, null);
            listener.onStackPop(null, null);
            listener.onLocalLoad(null, 0, null);
            listener.onLocalStore(null, 0, null);
            listener.onObjectAllocation(null);
            listener.onArrayAllocation(null);
            listener.onBranch(null, 0, 0, false);
        });
    }
}
