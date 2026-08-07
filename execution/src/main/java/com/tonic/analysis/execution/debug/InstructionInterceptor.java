package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;

/**
 * Debugger hook invoked around each interpreted instruction, method entry and exit, and thrown exception.
 */
public interface InstructionInterceptor
{

    /**
     * Called just before an instruction is interpreted.
     *
     * @param frame the frame about to execute
     * @param instruction the instruction about to run
     * @return whether the interpreter continues, pauses or aborts
     */
    InterceptorAction beforeInstruction(StackFrame frame, Instruction instruction);

    /**
     * Called after an instruction has been interpreted.
     *
     * @param frame the frame that executed
     * @param instruction the instruction that ran
     */
    void afterInstruction(StackFrame frame, Instruction instruction);

    /**
     * Called when a frame has been pushed and is about to run.
     *
     * @param frame the newly pushed frame
     * @return whether the interpreter continues, pauses or aborts
     */
    InterceptorAction onMethodEntry(StackFrame frame);

    /**
     * Called when a frame returns normally.
     *
     * @param frame the returning frame
     * @param returnValue the returned value, null for a void method
     */
    void onMethodExit(StackFrame frame, ConcreteValue returnValue);

    /**
     * Called when an exception is raised in a frame.
     *
     * @param frame the frame the exception was raised in
     * @param exception the thrown exception instance
     * @return whether the interpreter continues, pauses or aborts
     */
    InterceptorAction onException(StackFrame frame, ObjectInstance exception);
}
