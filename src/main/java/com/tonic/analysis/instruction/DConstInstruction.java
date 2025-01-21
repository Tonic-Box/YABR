package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the DCONST_* instructions (0x0E and 0x0F).
 */
public class DConstInstruction extends Instruction {
    private final double value;

    /**
     * Constructs a DConstInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @param value  The double constant value.
     */
    public DConstInstruction(int opcode, int offset, double value) {
        super(opcode, offset, 1);
        this.value = value;
    }

    /**
     * Writes the DCONST_* opcode to the DataOutputStream.
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
     * @return The stack size change (pushes a double, which occupies two stack slots).
     */
    @Override
    public int getStackChange() {
        return 2; // Double occupies two stack slots
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
     * Returns the constant value pushed onto the stack.
     *
     * @return The double constant.
     */
    public double getValue() {
        return value;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic and value of the instruction.
     */
    @Override
    public String toString() {
        return String.format("DCONST_%.1f", value);
    }
}
