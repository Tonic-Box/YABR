package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.result.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;

/**
 * Callback interface for observing bytecode interpreter events, with no-op defaults for every hook.
 */
public interface BytecodeListener
{

    /**
     * Called once before the entry point runs.
     *
     * @param entryPoint the method execution starts in
     */
    default void onExecutionStart(MethodEntry entryPoint) {}

    /**
     * Called once after execution finishes, normally or otherwise.
     *
     * @param result the outcome of the run
     */
    default void onExecutionEnd(BytecodeResult result) {}

    /**
     * Called when a frame is pushed onto the call stack.
     *
     * @param frame the pushed frame
     */
    default void onFramePush(StackFrame frame) {}

    /**
     * Called when a frame is popped after returning normally.
     *
     * @param frame the popped frame
     * @param returnValue the returned value, null for a void method
     */
    default void onFramePop(StackFrame frame, ConcreteValue returnValue) {}

    /**
     * Called when a frame is unwound by an exception.
     *
     * @param frame the frame being unwound
     * @param exception the in-flight exception instance
     */
    default void onFrameException(StackFrame frame, ObjectInstance exception) {}

    /**
     * Called just before an instruction is interpreted.
     *
     * @param frame the frame about to execute
     * @param instruction the instruction about to run
     */
    default void beforeInstruction(StackFrame frame, Instruction instruction) {}

    /**
     * Called after an instruction has been interpreted.
     *
     * @param frame the frame that executed
     * @param instruction the instruction that ran
     */
    default void afterInstruction(StackFrame frame, Instruction instruction) {}

    /**
     * Called when a value is pushed onto an operand stack.
     *
     * @param frame the owning frame
     * @param value the pushed value
     */
    default void onStackPush(StackFrame frame, ConcreteValue value) {}

    /**
     * Called when a value is popped off an operand stack.
     *
     * @param frame the owning frame
     * @param value the popped value
     */
    default void onStackPop(StackFrame frame, ConcreteValue value) {}

    /**
     * Called when a local variable is read.
     *
     * @param frame the owning frame
     * @param slot the local variable slot
     * @param value the value read
     */
    default void onLocalLoad(StackFrame frame, int slot, ConcreteValue value) {}

    /**
     * Called when a local variable is written.
     *
     * @param frame the owning frame
     * @param slot the local variable slot
     * @param value the value stored
     */
    default void onLocalStore(StackFrame frame, int slot, ConcreteValue value) {}

    /**
     * Called when an object is allocated on the heap.
     *
     * @param instance the new instance
     */
    default void onObjectAllocation(ObjectInstance instance) {}

    /**
     * Called when an array is allocated on the heap.
     *
     * @param array the new array
     */
    default void onArrayAllocation(ArrayInstance array) {}

    /**
     * Called when a field is read.
     *
     * @param instance the instance read from, null for a static field
     * @param fieldName the field name
     * @param value the value read
     */
    default void onFieldRead(ObjectInstance instance, String fieldName, ConcreteValue value) {}

    /**
     * Called when a field is written.
     *
     * @param instance the instance written to, null for a static field
     * @param fieldName the field name
     * @param oldValue the value being replaced
     * @param newValue the value stored
     */
    default void onFieldWrite(ObjectInstance instance, String fieldName, ConcreteValue oldValue, ConcreteValue newValue) {}

    /**
     * Called when an array element is read.
     *
     * @param array the array read from
     * @param index the element index
     * @param value the value read
     */
    default void onArrayRead(ArrayInstance array, int index, ConcreteValue value) {}

    /**
     * Called when an array element is written.
     *
     * @param array the array written to
     * @param index the element index
     * @param oldValue the value being replaced
     * @param newValue the value stored
     */
    default void onArrayWrite(ArrayInstance array, int index, ConcreteValue oldValue, ConcreteValue newValue) {}

    /**
     * Called when a branch instruction is decided.
     *
     * @param frame the branching frame
     * @param fromPC the branch instruction offset
     * @param toPC the offset control transfers to
     * @param taken whether the branch condition held
     */
    default void onBranch(StackFrame frame, int fromPC, int toPC, boolean taken) {}

    /**
     * Called when a method is invoked, before its frame is pushed.
     *
     * @param caller the calling frame
     * @param target the method being invoked
     * @param args the argument values
     */
    default void onMethodCall(StackFrame caller, MethodEntry target, ConcreteValue[] args) {}

    /**
     * Called when a return instruction executes.
     *
     * @param frame the returning frame
     * @param returnValue the returned value, null for a void method
     */
    default void onMethodReturn(StackFrame frame, ConcreteValue returnValue) {}

    /**
     * Called when an exception is thrown.
     *
     * @param frame the throwing frame
     * @param exception the thrown exception instance
     */
    default void onExceptionThrow(StackFrame frame, ObjectInstance exception) {}

    /**
     * Called when an exception is caught by a handler.
     *
     * @param frame the frame containing the handler
     * @param exception the caught exception instance
     * @param handlerPC the handler offset control transfers to
     */
    default void onExceptionCatch(StackFrame frame, ObjectInstance exception, int handlerPC) {}

    /**
     * Called when a call is dispatched to a native or emulated method instead of interpreted.
     *
     * @param method the method being invoked
     * @param receiver the receiver instance, null for a static method
     * @param args the argument values
     */
    default void onNativeMethodCall(MethodEntry method, ObjectInstance receiver, ConcreteValue[] args) {}

    /**
     * Called when a native or emulated method produces its result.
     *
     * @param method the method that was invoked
     * @param result the result value, null for a void method
     */
    default void onNativeMethodReturn(MethodEntry method, ConcreteValue result) {}
}
