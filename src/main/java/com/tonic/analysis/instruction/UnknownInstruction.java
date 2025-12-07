package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents an unknown or unhandled instruction.
 */
public class UnknownInstruction extends Instruction {
    private final byte[] operands;

    /**
     * Constructs an UnknownInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @param length The total length of the instruction in bytes.
     */
    public UnknownInstruction(int opcode, int offset, int length) {
        super(opcode, offset, length);
        this.operands = new byte[length - 1];
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the unknown opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        for (byte operand : operands) {
            dos.writeByte(operand);
        }
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (assumed to be 0 for unknown instructions).
     */
    @Override
    public int getStackChange() {
        return 0;
    }

    /**
     * Returns the change in local variables caused by this instruction.
     *
     * @return The local variables size change (assumed to be 0 for unknown instructions).
     */
    @Override
    public int getLocalChange() {
        return 0;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The unknown opcode in hexadecimal format.
     */
    @Override
    public String toString() {
        return String.format("UNKNOWN_OPCODE_0x%02X", opcode);
    }
}