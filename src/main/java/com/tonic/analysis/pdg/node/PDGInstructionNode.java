package com.tonic.analysis.pdg.node;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;

import java.util.List;

@Getter
public class PDGInstructionNode extends PDGNode {

    private final IRInstruction instruction;
    private final int instructionIndex;

    public PDGInstructionNode(int id, IRInstruction instruction, IRBlock block, int instructionIndex) {
        super(id, determineNodeType(instruction), block);
        this.instruction = instruction;
        this.instructionIndex = instructionIndex;
    }

    private static PDGNodeType determineNodeType(IRInstruction instruction) {
        if (instruction instanceof com.tonic.analysis.ssa.ir.PhiInstruction) {
            return PDGNodeType.PHI;
        }
        return PDGNodeType.INSTRUCTION;
    }

    @Override
    public String getLabel() {
        SSAValue result = instruction.getResult();
        if (result != null) {
            return result.getName() + " = " + instruction.getClass().getSimpleName();
        }
        return instruction.getClass().getSimpleName();
    }

    @Override
    public List<Value> getUsedValues() {
        return instruction.getOperands();
    }

    @Override
    public SSAValue getDefinedValue() {
        return instruction.getResult();
    }

    public boolean isPhi() {
        return instruction instanceof com.tonic.analysis.ssa.ir.PhiInstruction;
    }

    public boolean isTerminator() {
        return instruction.isTerminator();
    }

    public boolean hasResult() {
        return instruction.hasResult();
    }

    @Override
    public String toString() {
        return String.format("PDGInstr[%d: %s @ B%d:%d]",
            getId(),
            getLabel(),
            getBlock() != null ? getBlock().getId() : -1,
            instructionIndex);
    }
}
