package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the CALOAD instruction (0x34).
 */
public class CALoadInstruction extends Instruction {

    /**
     * Constructs a CALoadInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public CALoadInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
    }

    /**
     * Writes the CALOAD opcode to the DataOutputStream.
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
     * @return The stack size change (pops an array reference and index, pushes a char).
     */
    @Override
    public int getStackChange() {
        return 0; // Pops two, pushes one (char as int)
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
        return "CALOAD";
    }
}
