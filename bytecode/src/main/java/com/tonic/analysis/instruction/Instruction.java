package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a single bytecode instruction.
 */
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

    public int getOpcode() {
        return opcode;
    }

    public int getOffset() {
        return offset;
    }

    /** Returns the length of the instruction in bytes. */
    public int getLength() {
        return length;
    }

    /**
     * Updates this instruction's bytecode offset. Used by {@code CodeWriter}'s relink/layout pass
     * after a structural edit shifts instruction positions. Operand-bearing instructions whose
     * encoding depends on the offset (branches, switches) are reconstructed rather than mutated.
     *
     * @param offset the new bytecode offset
     */
    public void setOffset(int offset) {
        this.offset = offset;
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