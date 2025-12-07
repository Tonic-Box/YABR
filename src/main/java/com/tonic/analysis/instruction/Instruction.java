package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.utill.Logger;
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

    /**
     * Constructs an Instruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @param length The length of the instruction in bytes.
     */
    public Instruction(int opcode, int offset, int length) {
        this.opcode = opcode;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Returns the length of the instruction in bytes.
     *
     * @return The instruction length.
     */
    public int getLength() {
        Logger.info("Instruction " + this + " has length: " + length);
        return length;
    }

    /**
     * Accepts a visitor for bytecode analysis.
     *
     * @param visitor The visitor to accept.
     */
    public abstract void accept(AbstractBytecodeVisitor visitor);

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