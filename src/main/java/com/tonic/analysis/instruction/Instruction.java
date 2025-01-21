package com.tonic.analysis.instruction;

import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a single bytecode instruction.
 */
@Getter
public abstract class Instruction {
    protected int opcode;
    protected int offset;
    protected int length;

    public Instruction(int opcode, int offset, int length) {
        this.opcode = opcode;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Writes the instruction to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    public abstract void write(DataOutputStream dos) throws IOException;

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change.
     */
    public abstract int getStackChange();

    /**
     * Returns the change in local variables caused by this instruction.
     *
     * @return The local variables size change.
     */
    public abstract int getLocalChange();

    @Override
    public String toString() {
        return String.format("Instruction{opcode=0x%02X, offset=%d, length=%d}", opcode, offset, length);
    }
}