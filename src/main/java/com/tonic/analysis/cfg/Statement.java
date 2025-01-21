package com.tonic.analysis.cfg;

import com.tonic.analysis.instruction.Instruction;

import java.util.List;

/**
 * Represents a high-level statement abstraction of a basic block.
 */
public class Statement {
    private final int blockId;
    private final List<Instruction> instructions;

    public Statement(int blockId, List<Instruction> instructions) {
        this.blockId = blockId;
        this.instructions = instructions;
    }

    public int getBlockId() {
        return blockId;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Statement Block ").append(blockId).append(":\n");
        for (Instruction instr : instructions) {
            sb.append("  ").append(instr.toString()).append("\n");
        }
        return sb.toString();
    }
}