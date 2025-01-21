package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the SASTORE instruction (0x56).
 */
public class SASToreInstruction extends Instruction {

    /**
     * Constructs a SASToreInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public SASToreInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
    }

    /**
     * Writes the SASTORE opcode to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pops array reference, index, and short value; no push).
     */
    @Override
    public int getStackChange() {
        return -3; // Pops three: array reference, index, short value
    }

    /**
     * Returns the change in local variables caused by this instruction.
     *
     * @return The local variables size change (none).
     */
    @Override
    public int getLocalChange() {
        return 0;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic of the instruction.
     */
    @Override
    public String toString() {
        return "SASTORE";
    }
}
