package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.result.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;

public interface BytecodeListener {

    default void onExecutionStart(MethodEntry entryPoint) {}

    default void onExecutionEnd(BytecodeResult result) {}

    default void onFramePush(StackFrame frame) {}

    default void onFramePop(StackFrame frame, ConcreteValue returnValue) {}

    default void onFrameException(StackFrame frame, ObjectInstance exception) {}

    default void beforeInstruction(StackFrame frame, Instruction instruction) {}

    default void afterInstruction(StackFrame frame, Instruction instruction) {}

    default void onStackPush(StackFrame frame, ConcreteValue value) {}

    default void onStackPop(StackFrame frame, ConcreteValue value) {}

    default void onLocalLoad(StackFrame frame, int slot, ConcreteValue value) {}

    default void onLocalStore(StackFrame frame, int slot, ConcreteValue value) {}

    default void onObjectAllocation(ObjectInstance instance) {}

    default void onArrayAllocation(ArrayInstance array) {}

    default void onFieldRead(ObjectInstance instance, String fieldName, ConcreteValue value) {}

    default void onFieldWrite(ObjectInstance instance, String fieldName, ConcreteValue oldValue, ConcreteValue newValue) {}

    default void onArrayRead(ArrayInstance array, int index, ConcreteValue value) {}

    default void onArrayWrite(ArrayInstance array, int index, ConcreteValue oldValue, ConcreteValue newValue) {}

    default void onBranch(StackFrame frame, int fromPC, int toPC, boolean taken) {}

    default void onMethodCall(StackFrame caller, MethodEntry target, ConcreteValue[] args) {}

    default void onMethodReturn(StackFrame frame, ConcreteValue returnValue) {}

    default void onExceptionThrow(StackFrame frame, ObjectInstance exception) {}

    default void onExceptionCatch(StackFrame frame, ObjectInstance exception, int handlerPC) {}

    default void onNativeMethodCall(MethodEntry method, ObjectInstance receiver, ConcreteValue[] args) {}

    default void onNativeMethodReturn(MethodEntry method, ConcreteValue result) {}
}
