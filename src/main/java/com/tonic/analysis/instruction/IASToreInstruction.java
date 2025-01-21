package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the IASTORE instruction (0x53).
 */
public class IASToreInstruction extends Instruction {

    /**
     * Constructs an IASToreInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public IASToreInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        if (opcode != 0x53) {
            throw new IllegalArgumentException("Invalid opcode for IASToreInstruction: " + opcode);
        }
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
    }

    @Override
    public int getStackChange() {
        // IASTORE consumes two values from the stack (value and array reference)
        return -2;
    }

    @Override
    public int getLocalChange() {
        // No change in the number of local variables
        return 0;
    }

    @Override
    public String toString() {
        return "iastore";
    }
}
