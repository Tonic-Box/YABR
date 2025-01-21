package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the BIPUSH instruction (0x10).
 */
public class BipushInstruction extends Instruction {
    private final byte value;

    /**
     * Constructs a BipushInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @param value  The byte value to push onto the stack.
     */
    public BipushInstruction(int opcode, int offset, int value) {
        super(opcode, offset, 2);
        this.value = (byte) value;
    }

    /**
     * Writes the BIPUSH opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeByte(value);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pushes a byte as an int).
     */
    @Override
    public int getStackChange() {
        return 1; // Pushes an int onto the stack
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
     * Returns the byte value pushed onto the stack.
     *
     * @return The byte constant.
     */
    public byte getValue() {
        return value;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic and value of the instruction.
     */
    @Override
    public String toString() {
        return String.format("BIPUSH %d", value);
    }
}
