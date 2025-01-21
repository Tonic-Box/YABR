package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the LCONST_* instructions (0x09 and 0x0A).
 */
public class LConstInstruction extends Instruction {
    private final long value;

    /**
     * Constructs an LConstInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @param value  The long constant value.
     */
    public LConstInstruction(int opcode, int offset, long value) {
        super(opcode, offset, 1);
        this.value = value;
    }

    /**
     * Writes the LCONST_* opcode to the DataOutputStream.
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
     * @return The stack size change (pushes a long, which occupies two stack slots).
     */
    @Override
    public int getStackChange() {
        return 2; // Long occupies two stack slots
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
     * @return The long constant.
     */
    public long getValue() {
        return value;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic and value of the instruction.
     */
    @Override
    public String toString() {
        return String.format("LCONST_%d", value);
    }
}
