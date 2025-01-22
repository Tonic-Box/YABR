package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the LASTORE instruction (0x5D).
 */
public class LAStoreInstruction extends Instruction {

    /**
     * Constructs a LASToreInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public LAStoreInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        if (opcode != 0x5D) {
            throw new IllegalArgumentException("Invalid opcode for LASToreInstruction: " + opcode);
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
        // LASTORE consumes three values from the stack (value high, value low, and array reference)
        return -3;
    }

    @Override
    public int getLocalChange() {
        // No change in the number of local variables
        return 0;
    }

    @Override
    public String toString() {
        return "lastore";
    }
}
