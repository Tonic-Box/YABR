package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.result.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class CompositeBytecodeListener implements BytecodeListener {

    private final List<BytecodeListener> listeners;

    public CompositeBytecodeListener() {
        this.listeners = new ArrayList<>();
    }

    public CompositeBytecodeListener(BytecodeListener... listeners) {
        this.listeners = new ArrayList<>(Arrays.asList(listeners));
    }

    public CompositeBytecodeListener(Collection<BytecodeListener> listeners) {
        this.listeners = new ArrayList<>(listeners);
    }

    public void addListener(BytecodeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(BytecodeListener listener) {
        listeners.remove(listener);
    }

    public void clear() {
        listeners.clear();
    }

    public int size() {
        return listeners.size();
    }

    public boolean isEmpty() {
        return listeners.isEmpty();
    }

    public List<BytecodeListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    @Override
    public void onExecutionStart(MethodEntry entryPoint) {
        for (BytecodeListener listener : listeners) {
            listener.onExecutionStart(entryPoint);
        }
    }

    @Override
    public void onExecutionEnd(BytecodeResult result) {
        for (BytecodeListener listener : listeners) {
            listener.onExecutionEnd(result);
        }
    }

    @Override
    public void onFramePush(StackFrame frame) {
        for (BytecodeListener listener : listeners) {
            listener.onFramePush(frame);
        }
    }

    @Override
    public void onFramePop(StackFrame frame, ConcreteValue returnValue) {
        for (BytecodeListener listener : listeners) {
            listener.onFramePop(frame, returnValue);
        }
    }

    @Override
    public void onFrameException(StackFrame frame, ObjectInstance exception) {
        for (BytecodeListener listener : listeners) {
            listener.onFrameException(frame, exception);
        }
    }

    @Override
    public void beforeInstruction(StackFrame frame, Instruction instruction) {
        for (BytecodeListener listener : listeners) {
            listener.beforeInstruction(frame, instruction);
        }
    }

    @Override
    public void afterInstruction(StackFrame frame, Instruction instruction) {
        for (BytecodeListener listener : listeners) {
            listener.afterInstruction(frame, instruction);
        }
    }

    @Override
    public void onStackPush(StackFrame frame, ConcreteValue value) {
        for (BytecodeListener listener : listeners) {
            listener.onStackPush(frame, value);
        }
    }

    @Override
    public void onStackPop(StackFrame frame, ConcreteValue value) {
        for (BytecodeListener listener : listeners) {
            listener.onStackPop(frame, value);
        }
    }

    @Override
    public void onLocalLoad(StackFrame frame, int slot, ConcreteValue value) {
        for (BytecodeListener listener : listeners) {
            listener.onLocalLoad(frame, slot, value);
        }
    }

    @Override
    public void onLocalStore(StackFrame frame, int slot, ConcreteValue value) {
        for (BytecodeListener listener : listeners) {
            listener.onLocalStore(frame, slot, value);
        }
    }

    @Override
    public void onObjectAllocation(ObjectInstance instance) {
        for (BytecodeListener listener : listeners) {
            listener.onObjectAllocation(instance);
        }
    }

    @Override
    public void onArrayAllocation(ArrayInstance array) {
        for (BytecodeListener listener : listeners) {
            listener.onArrayAllocation(array);
        }
    }

    @Override
    public void onFieldRead(ObjectInstance instance, String fieldName, ConcreteValue value) {
        for (BytecodeListener listener : listeners) {
            listener.onFieldRead(instance, fieldName, value);
        }
    }

    @Override
    public void onFieldWrite(ObjectInstance instance, String fieldName, ConcreteValue oldValue, ConcreteValue newValue) {
        for (BytecodeListener listener : listeners) {
            listener.onFieldWrite(instance, fieldName, oldValue, newValue);
        }
    }

    @Override
    public void onArrayRead(ArrayInstance array, int index, ConcreteValue value) {
        for (BytecodeListener listener : listeners) {
            listener.onArrayRead(array, index, value);
        }
    }

    @Override
    public void onArrayWrite(ArrayInstance array, int index, ConcreteValue oldValue, ConcreteValue newValue) {
        for (BytecodeListener listener : listeners) {
            listener.onArrayWrite(array, index, oldValue, newValue);
        }
    }

    @Override
    public void onBranch(StackFrame frame, int fromPC, int toPC, boolean taken) {
        for (BytecodeListener listener : listeners) {
            listener.onBranch(frame, fromPC, toPC, taken);
        }
    }

    @Override
    public void onMethodCall(StackFrame caller, MethodEntry target, ConcreteValue[] args) {
        for (BytecodeListener listener : listeners) {
            listener.onMethodCall(caller, target, args);
        }
    }

    @Override
    public void onMethodReturn(StackFrame frame, ConcreteValue returnValue) {
        for (BytecodeListener listener : listeners) {
            listener.onMethodReturn(frame, returnValue);
        }
    }

    @Override
    public void onExceptionThrow(StackFrame frame, ObjectInstance exception) {
        for (BytecodeListener listener : listeners) {
            listener.onExceptionThrow(frame, exception);
        }
    }

    @Override
    public void onExceptionCatch(StackFrame frame, ObjectInstance exception, int handlerPC) {
        for (BytecodeListener listener : listeners) {
            listener.onExceptionCatch(frame, exception, handlerPC);
        }
    }

    @Override
    public void onNativeMethodCall(MethodEntry method, ObjectInstance receiver, ConcreteValue[] args) {
        for (BytecodeListener listener : listeners) {
            listener.onNativeMethodCall(method, receiver, args);
        }
    }

    @Override
    public void onNativeMethodReturn(MethodEntry method, ConcreteValue result) {
        for (BytecodeListener listener : listeners) {
            listener.onNativeMethodReturn(method, result);
        }
    }
}
