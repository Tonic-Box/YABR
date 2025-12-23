package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;

public interface InstructionInterceptor {

    InterceptorAction beforeInstruction(StackFrame frame, Instruction instruction);

    void afterInstruction(StackFrame frame, Instruction instruction);

    InterceptorAction onMethodEntry(StackFrame frame);

    void onMethodExit(StackFrame frame, ConcreteValue returnValue);

    InterceptorAction onException(StackFrame frame, ObjectInstance exception);
}
