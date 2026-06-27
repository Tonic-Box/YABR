package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.result.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TracingListenerTest {

    private TracingListener listener;

    @BeforeEach
    void setUp() {
        listener = new TracingListener();
    }

    @Test
    void initiallyEmpty() {
        assertTrue(listener.getEvents().isEmpty());
    }

    @Test
    void onExecutionStartRecordsEvent() {
        MethodEntry method = mock(MethodEntry.class);
        when(method.getOwnerName()).thenReturn("Test");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("()V");

        listener.onExecutionStart(method);

        assertEquals(1, listener.getEvents().size());
        assertEquals(TraceEvent.Type.EXECUTION_START, listener.getEvents().get(0).getType());
    }

    @Test
    void onExecutionEndRecordsEvent() {
        BytecodeResult result = BytecodeResult.success(null);

        listener.onExecutionEnd(result);

        assertEquals(1, listener.getEvents().size());
        assertEquals(TraceEvent.Type.EXECUTION_END, listener.getEvents().get(0).getType());
    }

    @Test
    void onFramePushRecordsEvent() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getMethodSignature()).thenReturn("Test.method()V");

        listener.onFramePush(frame);

        assertEquals(1, listener.getEvents().size());
        assertEquals(TraceEvent.Type.FRAME_PUSH, listener.getEvents().get(0).getType());
    }

    @Test
    void beforeInstructionRecordsEvent() {
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);
        when(frame.getPC()).thenReturn(10);
        when(instr.getOpcode()).thenReturn(0x60);
        when(frame.getStack()).thenReturn(new ConcreteStack(10));

        listener.beforeInstruction(frame, instr);

        assertEquals(1, listener.getEvents().size());
        assertEquals(TraceEvent.Type.INSTRUCTION, listener.getEvents().get(0).getType());
    }

    @Test
    void onStackPushRecordsEvent() {
        StackFrame frame = mock(StackFrame.class);
        when(frame.getPC()).thenReturn(5);
        ConcreteValue value = ConcreteValue.intValue(42);

        listener.onStackPush(frame, value);

        assertEquals(1, listener.getEvents().size());
        assertEquals(TraceEvent.Type.STACK_PUSH, listener.getEvents().get(0).getType());
    }

    @Test
    void onObjectAllocationRecordsEvent() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/String");

        listener.onObjectAllocation(obj);

        assertEquals(1, listener.getEvents().size());
        assertEquals(TraceEvent.Type.OBJECT_ALLOC, listener.getEvents().get(0).getType());
    }

    @Test
    void onArrayAllocationRecordsEvent() {
        ArrayInstance arr = new ArrayInstance(2, "I", 10);

        listener.onArrayAllocation(arr);

        assertEquals(1, listener.getEvents().size());
        assertEquals(TraceEvent.Type.ARRAY_ALLOC, listener.getEvents().get(0).getType());
    }

    @Test
    void onBranchRecordsEvent() {
        StackFrame frame = mock(StackFrame.class);

        listener.onBranch(frame, 10, 20, true);

        assertEquals(1, listener.getEvents().size());
        assertEquals(TraceEvent.Type.BRANCH, listener.getEvents().get(0).getType());
    }

    @Test
    void includeStackStateFalseByDefault() {
        TracingListener listener = new TracingListener();
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);
        when(frame.getPC()).thenReturn(0);
        when(instr.getOpcode()).thenReturn(0x01);
        when(frame.getStack()).thenReturn(new ConcreteStack(10));

        listener.beforeInstruction(frame, instr);

        assertNull(listener.getEvents().get(0).getStackState());
    }

    @Test
    void includeStackStateWhenEnabled() {
        TracingListener listener = new TracingListener(true);
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);
        ConcreteStack stack = new ConcreteStack(10);
        stack.pushInt(42);

        when(frame.getPC()).thenReturn(0);
        when(instr.getOpcode()).thenReturn(0x01);
        when(frame.getStack()).thenReturn(stack);

        listener.beforeInstruction(frame, instr);

        assertNotNull(listener.getEvents().get(0).getStackState());
        assertEquals(1, listener.getEvents().get(0).getStackState().size());
    }

    @Test
    void maxEventsLimitRespected() {
        TracingListener listener = new TracingListener(false, 3);
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);
        when(frame.getPC()).thenReturn(0);
        when(instr.getOpcode()).thenReturn(0x01);
        when(frame.getStack()).thenReturn(new ConcreteStack(10));

        for (int i = 0; i < 10; i++) {
            listener.beforeInstruction(frame, instr);
        }

        assertEquals(3, listener.getEvents().size());
    }

    @Test
    void clearEventsWorks() {
        ObjectInstance obj = new ObjectInstance(1, "Test");
        listener.onObjectAllocation(obj);

        assertEquals(1, listener.getEvents().size());

        listener.clearEvents();

        assertTrue(listener.getEvents().isEmpty());
    }

    @Test
    void getEventsIsUnmodifiable() {
        List<TraceEvent> events = listener.getEvents();

        assertThrows(UnsupportedOperationException.class, () -> {
            events.add(TraceEvent.executionStart("test"));
        });
    }

    @Test
    void formatTraceProducesReadableOutput() {
        ObjectInstance obj = new ObjectInstance(1, "Test");
        listener.onObjectAllocation(obj);
        listener.onObjectAllocation(obj);

        String trace = listener.formatTrace();

        assertTrue(trace.contains("Execution Trace"));
        assertTrue(trace.contains("2 events"));
        assertTrue(trace.contains("OBJECT_ALLOC"));
    }

    @Test
    void formatTraceWithMaxLines() {
        ObjectInstance obj = new ObjectInstance(1, "Test");
        for (int i = 0; i < 10; i++) {
            listener.onObjectAllocation(obj);
        }

        String trace = listener.formatTrace(3);

        assertTrue(trace.contains("7 more events"));
    }

    @Test
    void resetClearsEvents() {
        ObjectInstance obj = new ObjectInstance(1, "Test");
        listener.onObjectAllocation(obj);

        assertFalse(listener.getEvents().isEmpty());

        listener.reset();

        assertTrue(listener.getEvents().isEmpty());
    }

    @Test
    void allEventTypesRecorded() {
        StackFrame frame = mock(StackFrame.class);
        MethodEntry method = mock(MethodEntry.class);
        Instruction instr = mock(Instruction.class);
        ConcreteValue value = ConcreteValue.intValue(1);
        ObjectInstance obj = new ObjectInstance(1, "Test");
        ArrayInstance arr = new ArrayInstance(2, "I", 10);

        when(frame.getPC()).thenReturn(0);
        when(frame.getMethod()).thenReturn(method);
        when(frame.getMethodSignature()).thenReturn("Test.method()V");
        when(frame.getStack()).thenReturn(new ConcreteStack(10));
        when(instr.getOpcode()).thenReturn(0x01);
        when(method.getOwnerName()).thenReturn("Test");
        when(method.getName()).thenReturn("method");
        when(method.getDesc()).thenReturn("()V");

        listener.onExecutionStart(method);
        listener.onFramePush(frame);
        listener.beforeInstruction(frame, instr);
        listener.onStackPush(frame, value);
        listener.onStackPop(frame, value);
        listener.onLocalLoad(frame, 0, value);
        listener.onLocalStore(frame, 1, value);
        listener.onObjectAllocation(obj);
        listener.onArrayAllocation(arr);
        listener.onFieldRead(obj, "field", value);
        listener.onFieldWrite(obj, "field", value, value);
        listener.onArrayRead(arr, 0, value);
        listener.onArrayWrite(arr, 0, value, value);
        listener.onBranch(frame, 0, 10, true);
        listener.onMethodCall(frame, method, new ConcreteValue[0]);
        listener.onMethodReturn(frame, value);
        listener.onExceptionThrow(frame, obj);
        listener.onExceptionCatch(frame, obj, 20);
        listener.onNativeMethodCall(method, obj, new ConcreteValue[0]);
        listener.onNativeMethodReturn(method, value);
        listener.onFramePop(frame, value);
        listener.onFrameException(frame, obj);
        listener.onExecutionEnd(BytecodeResult.success(value));

        assertEquals(23, listener.getEvents().size());
    }

    @Test
    void constructorWithIncludeStackState() {
        TracingListener listener = new TracingListener(true);
        assertNotNull(listener);
    }

    @Test
    void constructorWithBothParameters() {
        TracingListener listener = new TracingListener(true, 100);
        assertNotNull(listener);
    }

    @Test
    void formatTraceUnlimited() {
        ObjectInstance obj = new ObjectInstance(1, "Test");
        listener.onObjectAllocation(obj);

        String trace = listener.formatTrace();

        assertFalse(trace.contains("more events"));
    }
}
