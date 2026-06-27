package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the ATHROW instruction (0xBF).
 */
public class ATHROWInstruction extends Instruction {

    /**
     * Constructs an ATHROWInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public ATHROWInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        if (opcode != 0xBF) {
            throw new IllegalArgumentException("Invalid opcode for ATHROWInstruction: " + opcode);
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
        return -1;
    }

    @Override
    public int getLocalChange() {
        return 0;
    }

    @Override
    public String toString() {
        return "ATHROW";
    }
}
