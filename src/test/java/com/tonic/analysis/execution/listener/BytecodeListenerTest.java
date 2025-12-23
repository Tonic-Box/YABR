package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.result.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeListenerTest {

    @Test
    void defaultMethodsAreNoOps() {
        BytecodeListener listener = new BytecodeListener() {};

        assertDoesNotThrow(() -> listener.onExecutionStart(null));
        assertDoesNotThrow(() -> listener.onExecutionEnd(null));
        assertDoesNotThrow(() -> listener.onFramePush(null));
        assertDoesNotThrow(() -> listener.onFramePop(null, null));
        assertDoesNotThrow(() -> listener.onFrameException(null, null));
        assertDoesNotThrow(() -> listener.beforeInstruction(null, null));
        assertDoesNotThrow(() -> listener.afterInstruction(null, null));
    }

    @Test
    void stackEventDefaultsAreNoOps() {
        BytecodeListener listener = new BytecodeListener() {};

        assertDoesNotThrow(() -> listener.onStackPush(null, null));
        assertDoesNotThrow(() -> listener.onStackPop(null, null));
    }

    @Test
    void localVariableEventDefaultsAreNoOps() {
        BytecodeListener listener = new BytecodeListener() {};

        assertDoesNotThrow(() -> listener.onLocalLoad(null, 0, null));
        assertDoesNotThrow(() -> listener.onLocalStore(null, 0, null));
    }

    @Test
    void heapEventDefaultsAreNoOps() {
        BytecodeListener listener = new BytecodeListener() {};

        assertDoesNotThrow(() -> listener.onObjectAllocation(null));
        assertDoesNotThrow(() -> listener.onArrayAllocation(null));
        assertDoesNotThrow(() -> listener.onFieldRead(null, null, null));
        assertDoesNotThrow(() -> listener.onFieldWrite(null, null, null, null));
        assertDoesNotThrow(() -> listener.onArrayRead(null, 0, null));
        assertDoesNotThrow(() -> listener.onArrayWrite(null, 0, null, null));
    }

    @Test
    void controlFlowEventDefaultsAreNoOps() {
        BytecodeListener listener = new BytecodeListener() {};

        assertDoesNotThrow(() -> listener.onBranch(null, 0, 0, false));
        assertDoesNotThrow(() -> listener.onMethodCall(null, null, null));
        assertDoesNotThrow(() -> listener.onMethodReturn(null, null));
        assertDoesNotThrow(() -> listener.onExceptionThrow(null, null));
        assertDoesNotThrow(() -> listener.onExceptionCatch(null, null, 0));
    }

    @Test
    void nativeMethodEventDefaultsAreNoOps() {
        BytecodeListener listener = new BytecodeListener() {};

        assertDoesNotThrow(() -> listener.onNativeMethodCall(null, null, null));
        assertDoesNotThrow(() -> listener.onNativeMethodReturn(null, null));
    }

    @Test
    void interfaceCanBeImplemented() {
        final boolean[] called = {false};

        BytecodeListener listener = new BytecodeListener() {
            @Override
            public void onExecutionStart(MethodEntry entryPoint) {
                called[0] = true;
            }
        };

        listener.onExecutionStart(null);
        assertTrue(called[0]);
    }

    @Test
    void allMethodsCallable() {
        BytecodeListener listener = new BytecodeListener() {};

        listener.onExecutionStart(null);
        listener.onExecutionEnd(null);
        listener.onFramePush(null);
        listener.onFramePop(null, null);
        listener.onFrameException(null, null);
        listener.beforeInstruction(null, null);
        listener.afterInstruction(null, null);
        listener.onStackPush(null, null);
        listener.onStackPop(null, null);
        listener.onLocalLoad(null, 0, null);
        listener.onLocalStore(null, 0, null);
        listener.onObjectAllocation(null);
        listener.onArrayAllocation(null);
        listener.onFieldRead(null, null, null);
        listener.onFieldWrite(null, null, null, null);
        listener.onArrayRead(null, 0, null);
        listener.onArrayWrite(null, 0, null, null);
        listener.onBranch(null, 0, 0, false);
        listener.onMethodCall(null, null, null);
        listener.onMethodReturn(null, null);
        listener.onExceptionThrow(null, null);
        listener.onExceptionCatch(null, null, 0);
        listener.onNativeMethodCall(null, null, null);
        listener.onNativeMethodReturn(null, null);
    }

    @Test
    void multipleMethodsCanBeOverridden() {
        final int[] count = {0};

        BytecodeListener listener = new BytecodeListener() {
            @Override
            public void onStackPush(StackFrame frame, ConcreteValue value) {
                count[0]++;
            }

            @Override
            public void onStackPop(StackFrame frame, ConcreteValue value) {
                count[0]++;
            }
        };

        listener.onStackPush(null, null);
        listener.onStackPop(null, null);
        listener.onLocalLoad(null, 0, null);

        assertEquals(2, count[0]);
    }
}
