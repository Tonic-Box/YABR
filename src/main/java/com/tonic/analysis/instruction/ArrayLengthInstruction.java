package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the ARRAYLENGTH instruction (0xBE).
 */
public class ArrayLengthInstruction extends Instruction {

    /**
     * Constructs an ArrayLengthInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public ArrayLengthInstruction(int opcode, int offset) {
        super(opcode, offset, 1); // opcode only
        if (opcode != 0xBE) {
            throw new IllegalArgumentException("Invalid opcode for ArrayLengthInstruction: " + opcode);
        }
    }

    /**
     * Writes the ARRAYLENGTH opcode to the DataOutputStream.
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
     * @return The stack size change (pops one array reference, pushes one int).
     */
    @Override
    public int getStackChange() {
        return 0; // Pops one reference and pushes one int (net change: 0)
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
        return "ARRAYLENGTH";
    }
}
