package com.tonic.analysis.cfg;

import com.tonic.analysis.instruction.Instruction;

import java.util.List;

/**
 * Represents a basic block within the control flow graph.
 */
public class BasicBlock {
    private final int id;
    private final int startOffset;
    private final int endOffset;
    private final List<Instruction> instructions;

    public BasicBlock(int id, int startOffset, int endOffset, List<Instruction> instructions) {
        this.id = id;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.instructions = instructions;
    }

    public int getId() {
        return id;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    @Override
    public String toString() {
        return "BasicBlock{" +
                "id=" + id +
                ", startOffset=" + startOffset +
                ", endOffset=" + endOffset +
                ", instructions=" + instructions +
                '}';
    }
}