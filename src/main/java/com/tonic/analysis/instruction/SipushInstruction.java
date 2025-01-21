package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the SIPUSH instruction (0x11).
 */
public class SipushInstruction extends Instruction {
    private final short value;

    /**
     * Constructs a SipushInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @param value  The short value to push onto the stack.
     */
    public SipushInstruction(int opcode, int offset, int value) {
        super(opcode, offset, 3);
        this.value = (short) value;
    }

    /**
     * Writes the SIPUSH opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(value);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pushes a short as an int).
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
     * Returns the short value pushed onto the stack.
     *
     * @return The short constant.
     */
    public short getValue() {
        return value;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic and value of the instruction.
     */
    @Override
    public String toString() {
        return String.format("SIPUSH %d", value);
    }
}
