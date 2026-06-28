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
     * Constructs an UnknownInstruction with zero-filled operands.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @param length The total length of the instruction in bytes.
     */
    public UnknownInstruction(int opcode, int offset, int length) {
        super(opcode, offset, length);
        this.operands = new byte[Math.max(0, length - 1)];
    }

    /**
     * Constructs an UnknownInstruction, copying the real operand bytes from the source bytecode so
     * the instruction round-trips through {@link #write} and its operands can be inspected.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param length   The total length of the instruction in bytes.
     * @param bytecode The source bytecode array to copy operand bytes from.
     */
    public UnknownInstruction(int opcode, int offset, int length, byte[] bytecode) {
        super(opcode, offset, length);
        this.operands = new byte[Math.max(0, length - 1)];
        int available = bytecode.length - (offset + 1);
        int copy = Math.min(operands.length, Math.max(0, available));
        System.arraycopy(bytecode, offset + 1, operands, 0, copy);
    }

    /** Returns the raw operand bytes following the opcode. */
    public byte[] getOperands() {
        return operands;
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