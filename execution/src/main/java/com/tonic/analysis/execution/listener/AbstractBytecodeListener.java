package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;

public abstract class AbstractBytecodeListener implements BytecodeListener {

    private MethodEntry currentMethod;
    private int instructionCount;

    @Override
    public void onFramePush(StackFrame frame) {
        this.currentMethod = frame.getMethod();
    }

    @Override
    public void afterInstruction(StackFrame frame, Instruction instruction) {
        instructionCount++;
    }

    protected MethodEntry getCurrentMethod() {
        return currentMethod;
    }

    protected int getInstructionCount() {
        return instructionCount;
    }

    public void reset() {
        currentMethod = null;
        instructionCount = 0;
    }
}
