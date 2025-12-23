package com.tonic.analysis.execution.frame;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.parser.attribute.table.LineNumberTableEntry;

public final class StackFrame {

    private final MethodEntry method;
    private final CodeWriter code;
    private final ConcreteStack stack;
    private final ConcreteLocals locals;
    private int pc;
    private boolean completed;
    private ConcreteValue returnValue;
    private ObjectInstance exception;

    public StackFrame(MethodEntry method, ConcreteValue[] args) {
        if (method == null) {
            throw new IllegalArgumentException("Method cannot be null");
        }

        this.method = method;

        CodeAttribute codeAttr = method.getCodeAttribute();
        if (codeAttr == null) {
            throw new IllegalArgumentException("Method has no code attribute (abstract/native): " + method.getName());
        }

        this.code = new CodeWriter(method);
        int stackSize = Math.max(codeAttr.getMaxStack(), 8);
        this.stack = new ConcreteStack(stackSize);
        this.locals = ConcreteLocals.forMethod(method, args);
        this.pc = 0;
        this.completed = false;
        this.returnValue = null;
        this.exception = null;
    }

    public int getPC() {
        return pc;
    }

    public void setPC(int pc) {
        if (pc < 0) {
            throw new IllegalArgumentException("PC cannot be negative: " + pc);
        }
        this.pc = pc;
    }

    public void advancePC(int delta) {
        if (delta < 0) {
            throw new IllegalArgumentException("PC delta cannot be negative: " + delta);
        }
        this.pc += delta;
    }

    public Instruction getCurrentInstruction() {
        return getInstructionAt(pc);
    }

    public Instruction getInstructionAt(int offset) {
        return code.getInstructions().spliterator().tryAdvance(instr -> {})
            ? findInstructionAtOffset(offset)
            : null;
    }

    private Instruction findInstructionAtOffset(int offset) {
        for (Instruction instr : code.getInstructions()) {
            if (instr.getOffset() == offset) {
                return instr;
            }
        }
        return null;
    }

    public boolean hasMoreInstructions() {
        return getCurrentInstruction() != null && !completed;
    }

    public void complete(ConcreteValue returnValue) {
        if (completed) {
            throw new IllegalStateException("Frame already completed");
        }
        this.completed = true;
        this.returnValue = returnValue;
    }

    public void completeExceptionally(ObjectInstance exception) {
        if (completed) {
            throw new IllegalStateException("Frame already completed");
        }
        if (exception == null) {
            throw new IllegalArgumentException("Exception cannot be null");
        }
        this.completed = true;
        this.exception = exception;
    }

    public boolean isCompleted() {
        return completed;
    }

    public ConcreteValue getReturnValue() {
        if (!completed) {
            throw new IllegalStateException("Frame not yet completed");
        }
        return returnValue;
    }

    public ObjectInstance getException() {
        return exception;
    }

    public MethodEntry getMethod() {
        return method;
    }

    public CodeWriter getCode() {
        return code;
    }

    public ConcreteStack getStack() {
        return stack;
    }

    public ConcreteLocals getLocals() {
        return locals;
    }

    public String getMethodSignature() {
        return method.getOwnerName() + "." + method.getName() + method.getDesc();
    }

    public int getLineNumber() {
        CodeAttribute codeAttr = method.getCodeAttribute();
        if (codeAttr == null) {
            return -1;
        }

        LineNumberTableAttribute lineTable = null;
        for (var attr : codeAttr.getAttributes()) {
            if (attr instanceof LineNumberTableAttribute) {
                lineTable = (LineNumberTableAttribute) attr;
                break;
            }
        }

        if (lineTable == null || lineTable.getLineNumberTable().isEmpty()) {
            return -1;
        }

        int bestLine = -1;
        int bestPc = -1;

        for (LineNumberTableEntry entry : lineTable.getLineNumberTable()) {
            int startPc = entry.getStartPc();
            if (startPc <= pc && startPc > bestPc) {
                bestPc = startPc;
                bestLine = entry.getLineNumber();
            }
        }

        return bestLine;
    }

    @Override
    public String toString() {
        return "StackFrame{" +
                "method=" + getMethodSignature() +
                ", pc=" + pc +
                ", line=" + getLineNumber() +
                ", completed=" + completed +
                '}';
    }
}
