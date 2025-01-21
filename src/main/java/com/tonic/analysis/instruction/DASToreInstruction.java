package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the DASTORE instruction (0x5B).
 */
public class DASToreInstruction extends Instruction {

    /**
     * Constructs a DASToreInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public DASToreInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        if (opcode != 0x5B) {
            throw new IllegalArgumentException("Invalid opcode for DASToreInstruction: " + opcode);
        }
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
    }

    @Override
    public int getStackChange() {
        // DASTORE consumes three values from the stack (value high, value low, and array reference)
        return -3;
    }

    @Override
    public int getLocalChange() {
        // No change in the number of local variables
        return 0;
    }

    @Override
    public String toString() {
        return "dastore";
    }
}
