package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the CASTORE instruction (0x55).
 */
public class CASToreInstruction extends Instruction {

    /**
     * Constructs a CASToreInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public CASToreInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
    }

    /**
     * Writes the CASTORE opcode to the DataOutputStream.
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
     * @return The stack size change (pops array reference, index, and char value; no push).
     */
    @Override
    public int getStackChange() {
        return -3; // Pops three: array reference, index, char value
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
        return "CASTORE";
    }
}
