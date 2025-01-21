package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the FASTORE instruction (0x5C).
 */
public class FASToreInstruction extends Instruction {

    /**
     * Constructs a FASToreInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public FASToreInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        if (opcode != 0x5C) {
            throw new IllegalArgumentException("Invalid opcode for FASToreInstruction: " + opcode);
        }
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
    }

    @Override
    public int getStackChange() {
        // FASTORE consumes two values from the stack (value and array reference)
        return -2;
    }

    @Override
    public int getLocalChange() {
        // No change in the number of local variables
        return 0;
    }

    @Override
    public String toString() {
        return "fastore";
    }
}
