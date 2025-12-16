package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

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
        super(opcode, offset, 1);
        if (opcode != 0xBE) {
            throw new IllegalArgumentException("Invalid opcode for ArrayLengthInstruction: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
    }

    @Override
    public int getStackChange() {
        return 0;
    }

    @Override
    public int getLocalChange() {
        return 0;
    }

    @Override
    public String toString() {
        return "ARRAYLENGTH";
    }
}
