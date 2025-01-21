package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents an unknown or unhandled instruction.
 */
public class UnknownInstruction extends Instruction {
    private final byte[] operands;

    public UnknownInstruction(int opcode, int offset, int length) {
        super(opcode, offset, length);
        this.operands = new byte[length - 1];
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        for (byte operand : operands) {
            dos.writeByte(operand);
        }
    }

    @Override
    public int getStackChange() {
        // Unknown, assume no change
        return 0;
    }

    @Override
    public int getLocalChange() {
        // Unknown, assume no change
        return 0;
    }

    @Override
    public String toString() {
        return String.format("UNKNOWN_OPCODE_0x%02X", opcode);
    }
}