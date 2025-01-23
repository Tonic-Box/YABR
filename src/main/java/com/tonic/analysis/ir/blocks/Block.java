package com.tonic.analysis.ir.blocks;

import com.tonic.analysis.instruction.Instruction;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract superclass representing a generic block of bytecode instructions.
 */
@Getter
public abstract class Block {
    protected final List<Instruction> instructions = new ArrayList<>();

    /**
     * Adds an instruction to the block.
     *
     * @param instruction The instruction to add.
     */
    public void addInstruction(Instruction instruction) {
        instructions.add(instruction);
    }

    /**
     * Gets the starting bytecode offset of the block.
     *
     * @return The starting offset.
     */
    public int getStartOffset() {
        if (instructions.isEmpty()) return -1;
        return instructions.get(0).getOffset();
    }

    /**
     * Gets the ending bytecode offset of the block.
     *
     * @return The ending offset.
     */
    public int getEndOffset() {
        if (instructions.isEmpty()) return -1;
        Instruction last = instructions.get(instructions.size() - 1);
        return last.getOffset() + last.getLength();
    }
}