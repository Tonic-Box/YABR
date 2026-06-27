package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.result.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompositeBytecodeListenerTest {

    private CompositeBytecodeListener composite;

    @BeforeEach
    void setUp() {
        composite = new CompositeBytecodeListener();
    }

    @Test
    void emptyCompositeIsValid() {
        assertNotNull(composite);
        assertTrue(composite.isEmpty());
        assertEquals(0, composite.size());
    }

    @Test
    void constructorWithVarargs() {
        BytecodeListener l1 = mock(BytecodeListener.class);
        BytecodeListener l2 = mock(BytecodeListener.class);

        CompositeBytecodeListener comp = new CompositeBytecodeListener(l1, l2);

        assertEquals(2, comp.size());
        assertTrue(comp.getListeners().contains(l1));
        assertTrue(comp.getListeners().contains(l2));
    }

    @Test
    void constructorWithCollection() {
        List<BytecodeListener> listeners = Arrays.asList(
            mock(BytecodeListener.class),
            mock(BytecodeListener.class)
        );

        CompositeBytecodeListener comp = new CompositeBytecodeListener(listeners);

        assertEquals(2, comp.size());
    }

    @Test
    void addListener() {
        BytecodeListener listener = mock(BytecodeListener.class);

        composite.addListener(listener);

        assertEquals(1, composite.size());
        assertTrue(composite.getListeners().contains(listener));
    }

    @Test
    void addNullListenerIsIgnored() {
        composite.addListener(null);

        assertEquals(0, composite.size());
    }

    @Test
    void removeListener() {
        BytecodeListener listener = mock(BytecodeListener.class);

        composite.addListener(listener);
        assertEquals(1, composite.size());

        composite.removeListener(listener);
        assertEquals(0, composite.size());
    }

    @Test
    void removeNonExistentListenerIsNoOp() {
        BytecodeListener listener = mock(BytecodeListener.class);

        composite.removeListener(listener);
        assertEquals(0, composite.size());
    }

    @Test
    void clear() {
        composite.addListener(mock(BytecodeListener.class));
        composite.addListener(mock(BytecodeListener.class));

        assertEquals(2, composite.size());

        composite.clear();

        assertEquals(0, composite.size());
        assertTrue(composite.isEmpty());
    }

    @Test
    void getListenersIsUnmodifiable() {
        List<BytecodeListener> listeners = composite.getListeners();

        assertThrows(UnsupportedOperationException.class, () -> {
            listeners.add(mock(BytecodeListener.class));
        });
    }

    @Test
    void onExecutionStartDelegatesToAll() {
        BytecodeListener l1 = mock(BytecodeListener.class);
        BytecodeListener l2 = mock(BytecodeListener.class);
        MethodEntry method = mock(MethodEntry.class);

        composite.addListener(l1);
        composite.addListener(l2);

        composite.onExecutionStart(method);

        verify(l1).onExecutionStart(method);
        verify(l2).onExecutionStart(method);
    }

    @Test
    void onExecutionEndDelegatesToAll() {
        BytecodeListener l1 = mock(BytecodeListener.class);
        BytecodeListener l2 = mock(BytecodeListener.class);
        BytecodeResult result = BytecodeResult.success(null);

        composite.addListener(l1);
        composite.addListener(l2);

        composite.onExecutionEnd(result);

        verify(l1).onExecutionEnd(result);
        verify(l2).onExecutionEnd(result);
    }

    @Test
    void onFramePushDelegatesToAll() {
        BytecodeListener l1 = mock(BytecodeListener.class);
        BytecodeListener l2 = mock(BytecodeListener.class);
        StackFrame frame = mock(StackFrame.class);

        composite.addListener(l1);
        composite.addListener(l2);

        composite.onFramePush(frame);

        verify(l1).onFramePush(frame);
        verify(l2).onFramePush(frame);
    }

    @Test
    void beforeInstructionDelegatesToAll() {
        BytecodeListener l1 = mock(BytecodeListener.class);
        BytecodeListener l2 = mock(BytecodeListener.class);
        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);

        composite.addListener(l1);
        composite.addListener(l2);

        composite.beforeInstruction(frame, instr);

        verify(l1).beforeInstruction(frame, instr);
        verify(l2).beforeInstruction(frame, instr);
    }

    @Test
    void onStackPushDelegatesToAll() {
        BytecodeListener l1 = mock(BytecodeListener.class);
        BytecodeListener l2 = mock(BytecodeListener.class);
        StackFrame frame = mock(StackFrame.class);
        ConcreteValue value = ConcreteValue.intValue(42);

        composite.addListener(l1);
        composite.addListener(l2);

        composite.onStackPush(frame, value);

        verify(l1).onStackPush(frame, value);
        verify(l2).onStackPush(frame, value);
    }

    @Test
    void orderIsPreserved() {
        List<Integer> order = new ArrayList<>();

        BytecodeListener l1 = new BytecodeListener() {
            @Override
            public void onExecutionStart(MethodEntry entryPoint) {
                order.add(1);
            }
        };

        BytecodeListener l2 = new BytecodeListener() {
            @Override
            public void onExecutionStart(MethodEntry entryPoint) {
                order.add(2);
            }
        };

        BytecodeListener l3 = new BytecodeListener() {
            @Override
            public void onExecutionStart(MethodEntry entryPoint) {
                order.add(3);
            }
        };

        composite.addListener(l1);
        composite.addListener(l2);
        composite.addListener(l3);

        composite.onExecutionStart(null);

        assertEquals(Arrays.asList(1, 2, 3), order);
    }

    @Test
    void allEventsDelegated() {
        BytecodeListener listener = mock(BytecodeListener.class);
        composite.addListener(listener);

        StackFrame frame = mock(StackFrame.class);
        Instruction instr = mock(Instruction.class);
        ConcreteValue value = ConcreteValue.intValue(1);
        MethodEntry method = mock(MethodEntry.class);
        ObjectInstance obj = new ObjectInstance(1, "Test");
        ArrayInstance arr = new ArrayInstance(2, "I", 10);
        BytecodeResult result = BytecodeResult.success(null);

        composite.onExecutionStart(method);
        composite.onExecutionEnd(result);
        composite.onFramePush(frame);
        composite.onFramePop(frame, value);
        composite.onFrameException(frame, obj);
        composite.beforeInstruction(frame, instr);
        composite.afterInstruction(frame, instr);
        composite.onStackPush(frame, value);
        composite.onStackPop(frame, value);
        composite.onLocalLoad(frame, 0, value);
        composite.onLocalStore(frame, 1, value);
        composite.onObjectAllocation(obj);
        composite.onArrayAllocation(arr);
        composite.onFieldRead(obj, "field", value);
        composite.onFieldWrite(obj, "field", value, value);
        composite.onArrayRead(arr, 0, value);
        composite.onArrayWrite(arr, 0, value, value);
        composite.onBranch(frame, 0, 10, true);
        composite.onMethodCall(frame, method, new ConcreteValue[0]);
        composite.onMethodReturn(frame, value);
        composite.onExceptionThrow(frame, obj);
        composite.onExceptionCatch(frame, obj, 20);
        composite.onNativeMethodCall(method, obj, new ConcreteValue[0]);
        composite.onNativeMethodReturn(method, value);

        verify(listener).onExecutionStart(method);
        verify(listener).onExecutionEnd(result);
        verify(listener).onFramePush(frame);
        verify(listener).onFramePop(frame, value);
        verify(listener).onFrameException(frame, obj);
        verify(listener).beforeInstruction(frame, instr);
        verify(listener).afterInstruction(frame, instr);
        verify(listener).onStackPush(frame, value);
        verify(listener).onStackPop(frame, value);
        verify(listener).onLocalLoad(frame, 0, value);
        verify(listener).onLocalStore(frame, 1, value);
        verify(listener).onObjectAllocation(obj);
        verify(listener).onArrayAllocation(arr);
        verify(listener).onFieldRead(obj, "field", value);
        verify(listener).onFieldWrite(obj, "field", value, value);
        verify(listener).onArrayRead(arr, 0, value);
        verify(listener).onArrayWrite(arr, 0, value, value);
        verify(listener).onBranch(frame, 0, 10, true);
        verify(listener).onMethodCall(frame, method, new ConcreteValue[0]);
        verify(listener).onMethodReturn(frame, value);
        verify(listener).onExceptionThrow(frame, obj);
        verify(listener).onExceptionCatch(frame, obj, 20);
        verify(listener).onNativeMethodCall(method, obj, new ConcreteValue[0]);
        verify(listener).onNativeMethodReturn(method, value);
    }

    @Test
    void emptyCompositeDoesNotThrow() {
        assertDoesNotThrow(() -> {
            composite.onExecutionStart(null);
            composite.onExecutionEnd(null);
            composite.beforeInstruction(null, null);
        });
    }
}
