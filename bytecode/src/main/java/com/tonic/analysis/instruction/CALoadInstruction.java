package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the CALOAD instruction (0x34).
 */
public class CALoadInstruction extends Instruction {

    /**
     * Constructs a CALoadInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public CALoadInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
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
        return "CALOAD";
    }
}
