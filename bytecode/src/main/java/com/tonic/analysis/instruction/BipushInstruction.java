package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

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

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeByte(value);
    }

    @Override
    public int getStackChange() {
        return 1;
    }

    @Override
    public int getLocalChange() {
        return 0;
    }

    public byte getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.format("BIPUSH %d", value);
    }
}
