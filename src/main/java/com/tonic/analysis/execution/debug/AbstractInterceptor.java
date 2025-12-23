package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;

public abstract class AbstractInterceptor implements InstructionInterceptor {

    @Override
    public InterceptorAction beforeInstruction(StackFrame frame, Instruction instruction) {
        return InterceptorAction.CONTINUE;
    }

    @Override
    public void afterInstruction(StackFrame frame, Instruction instruction) {
    }

    @Override
    public InterceptorAction onMethodEntry(StackFrame frame) {
        return InterceptorAction.CONTINUE;
    }

    @Override
    public void onMethodExit(StackFrame frame, ConcreteValue returnValue) {
    }

    @Override
    public InterceptorAction onException(StackFrame frame, ObjectInstance exception) {
        return InterceptorAction.CONTINUE;
    }
}
