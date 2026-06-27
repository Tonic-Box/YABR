package com.tonic.analysis.cpg.node;

import com.tonic.analysis.ssa.ir.*;
import lombok.Getter;

@Getter
public class InstructionNode extends CPGNode {

    private final IRInstruction instruction;
    private final int instructionIndex;
    private final int blockId;

    public InstructionNode(long id, IRInstruction instruction, int blockId, int instructionIndex) {
        super(id, CPGNodeType.INSTRUCTION);
        this.instruction = instruction;
        this.blockId = blockId;
        this.instructionIndex = instructionIndex;

        setProperty("blockId", blockId);
        setProperty("index", instructionIndex);
        setProperty("instrType", instruction.getClass().getSimpleName());

        if (instruction instanceof InvokeInstruction) {
            InvokeInstruction invoke = (InvokeInstruction) instruction;
            setProperty("targetOwner", invoke.getOwner());
            setProperty("targetName", invoke.getName());
            setProperty("targetDescriptor", invoke.getDescriptor());
        } else if (instruction instanceof FieldAccessInstruction) {
            FieldAccessInstruction field = (FieldAccessInstruction) instruction;
            setProperty("fieldOwner", field.getOwner());
            setProperty("fieldName", field.getName());
            setProperty("isStore", field.isStore());
        } else if (instruction instanceof NewInstruction) {
            NewInstruction newInstr = (NewInstruction) instruction;
            setProperty("allocType", newInstr.getClassName());
        }
    }

    @Override
    public String getLabel() {
        if (instruction.hasResult()) {
            return instruction.getResult().getName() + " = " + instruction.getClass().getSimpleName();
        }
        return instruction.getClass().getSimpleName();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getUnderlying() {
        return (T) instruction;
    }

    public boolean isInvoke() {
        return instruction instanceof InvokeInstruction;
    }

    public boolean isFieldAccess() {
        return instruction instanceof FieldAccessInstruction;
    }

    public boolean isAllocation() {
        return instruction instanceof NewInstruction || instruction instanceof NewArrayInstruction;
    }

    public boolean isReturn() {
        return instruction instanceof ReturnInstruction;
    }

    public boolean isBranch() {
        return instruction instanceof BranchInstruction;
    }

    public boolean isPhi() {
        return instruction instanceof PhiInstruction;
    }

    public boolean hasResult() {
        return instruction.hasResult();
    }

    public boolean isTerminator() {
        return instruction.isTerminator();
    }

    @Override
    public String toString() {
        return String.format("InstrNode[%d: %s @ B%d:%d]",
            getId(), instruction.getClass().getSimpleName(), blockId, instructionIndex);
    }
}
